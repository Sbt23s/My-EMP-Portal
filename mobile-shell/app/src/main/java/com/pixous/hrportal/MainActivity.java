package com.pixous.hrportal;

import android.Manifest;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.PermissionRequest;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * The Pixous HR portal, as an app.
 *
 * <p>This is a window onto the real website rather than a copy of it. Every
 * screen, module, wording and tile is whatever the portal is serving right
 * now, so the app cannot drift away from the website and one deploy updates
 * both. There is no second implementation to keep in step.
 *
 * <p>What the Java here is for is the handful of things a page cannot do for
 * itself inside a WebView: hold the Android permissions the browser needs
 * before it will grant the page anything, put a file somewhere when the page
 * downloads one, hand the page a file when it asks, and make the hardware
 * back button mean "back" rather than "quit".
 */
public class MainActivity extends AppCompatActivity {

    private static final String PORTAL = "https://pixoushrportal.pixous.info/";
    private static final String PORTAL_HOST = "pixoushrportal.pixous.info";

    /**
     * Everything the portal asks for, requested together on first launch.
     *
     * <p>A permission dialog that appears the moment somebody tries to punch
     * attendance -- while the camera is opening -- is one they dismiss by
     * reflex, and then attendance is broken with no explanation.
     */
    private static final String[] PERMISSIONS = {
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
    };

    private WebView web;
    private SwipeRefreshLayout refresher;

    /** Set while the page has a file chooser open, cleared when it closes. */
    private ValueCallback<Uri[]> fileCallback;
    private ActivityResultLauncher<Intent> filePicker;
    private ActivityResultLauncher<String[]> permissionAsker;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        web = findViewById(R.id.web);
        refresher = findViewById(R.id.refresher);

        permissionAsker = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> { /* Whatever was granted is granted; the page is told when it asks. */ });

        filePicker = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (fileCallback == null) return;
                    ValueCallback<Uri[]> cb = fileCallback;
                    fileCallback = null;
                    // A cancelled picker must still be answered, with null.
                    // Leaving the callback unanswered locks that file input for
                    // good -- it never opens a chooser again until the app is
                    // restarted.
                    cb.onReceiveValue(WebChromeClient.FileChooserParams
                            .parseResult(result.getResultCode(), result.getData()));
                });

        configureWebView();
        askForPermissions();

        refresher.setOnRefreshListener(() -> web.reload());

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // Back means back through the portal, and only leaves the app
                // once there is nowhere left to go.
                if (web.canGoBack()) {
                    web.goBack();
                } else {
                    finish();
                }
            }
        });

        if (savedInstanceState == null) {
            web.loadUrl(PORTAL);
        }
    }

    private void askForPermissions() {
        List<String> missing = new ArrayList<>();
        for (String p : PERMISSIONS) {
            if (!granted(p)) missing.add(p);
        }
        if (!missing.isEmpty()) {
            permissionAsker.launch(missing.toArray(new String[0]));
        }
    }

    @SuppressWarnings("SetJavaScriptEnabled")
    private void configureWebView() {
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);       // the portal keeps its session here
        s.setDatabaseEnabled(true);
        /*
          Render at the phone's real width.

          useWideViewPort makes the WebView honour the page's own
          <meta viewport width=device-width>, which is what gives the portal
          its phone layout -- the drawer instead of the sidebar, stacked cards,
          and the horizontal scroll its tables already carry. Without it the
          WebView invents a desktop-width viewport and serves the desktop
          layout shrunk to fit, which is unreadable.
        */
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);

        /*
          Ignore the phone's system font-size setting.

          A phone set to "largest text" scales every page by up to 130%, and a
          layout built for the width it was given then overflows -- columns
          wrap, buttons fall off the edge, tables that fit stop fitting. The
          portal is meant to look the way it looks in a browser, so the text
          is pinned to the size the CSS asks for.
        */
        s.setTextZoom(100);

        /*
          Pinch to zoom, without the on-screen +/- buttons.

          The portal is responsive and lays itself out for the phone, so this
          is not how it is meant to be read -- it is the escape hatch. A single
          wide table or a long employee name on a small screen should be
          something the reader can zoom into, not something that traps them.
          The floating zoom buttons are switched off because they sit on top of
          the page and cover the portal's own controls.
        */
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setSupportMultipleWindows(false);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);

        /*
          Without this, a remote video track arrives and never renders.

          A WebView refuses to start media that no gesture asked for, and the
          far side's video in a call is exactly that. It is the same policy
          that had to be worked around in Chrome, and it fails the same way:
          the connection is fine, the track is live, and the picture never
          appears.
        */
        s.setMediaPlaybackRequiresUserGesture(false);

        // Identify the app while keeping the real browser string, so the portal
        // renders exactly as it does in Chrome.
        s.setUserAgentString(s.getUserAgentString() + " PixousHRApp/1.0");

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                // The portal stays in the app. Anything else -- a link somebody
                // pasted into chat -- opens in the phone's browser, where the
                // address bar is visible and the user can judge it themselves.
                if (PORTAL_HOST.equalsIgnoreCase(uri.getHost())) return false;
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                    return true;
                } catch (Exception e) {
                    return false;
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                refresher.setRefreshing(false);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request,
                                        WebResourceError error) {
                refresher.setRefreshing(false);
                // Only the page itself is worth interrupting somebody about. An
                // image or a background request failing is not.
                if (request.isForMainFrame()) {
                    Toast.makeText(MainActivity.this,
                            "Cannot reach the portal. Check your connection and pull down to retry.",
                            Toast.LENGTH_LONG).show();
                }
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            /**
             * The camera and microphone.
             *
             * <p>Without this, calls and face punch fail silently: getUserMedia
             * is rejected and the page sees a denial it can do nothing about,
             * because inside a WebView that decision belongs to the app rather
             * than the browser.
             *
             * <p>Only what the app itself holds is granted -- granting a
             * permission the app was refused would throw.
             */
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(() -> {
                    List<String> allow = new ArrayList<>();
                    for (String res : request.getResources()) {
                        if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(res)
                                && granted(Manifest.permission.CAMERA)) {
                            allow.add(res);
                        } else if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(res)
                                && granted(Manifest.permission.RECORD_AUDIO)) {
                            allow.add(res);
                        }
                    }
                    if (allow.isEmpty()) {
                        request.deny();
                        Toast.makeText(MainActivity.this,
                                "Allow camera and microphone in Settings to use calls and attendance.",
                                Toast.LENGTH_LONG).show();
                    } else {
                        request.grant(allow.toArray(new String[0]));
                    }
                });
            }

            /** The geofence check on a field attendance punch. */
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin,
                                                           GeolocationPermissions.Callback callback) {
                boolean ok = granted(Manifest.permission.ACCESS_FINE_LOCATION)
                        || granted(Manifest.permission.ACCESS_COARSE_LOCATION);
                callback.invoke(origin, ok, false);
            }

            /** Attachments on work reports, claims and chat. */
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                try {
                    filePicker.launch(params.createIntent());
                    return true;
                } catch (Exception e) {
                    fileCallback = null;
                    return false;
                }
            }
        });

        /*
          Payslips and exported spreadsheets.

          A WebView does nothing at all with a download by default, so the tap
          looks like a broken button. These go to Android's download manager,
          landing in Downloads and appearing in the notification shade.
        */
        web.setDownloadListener((url, userAgent, contentDisposition, mimeType, length) -> {
            try {
                String name = URLUtil.guessFileName(url, contentDisposition, mimeType);
                DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
                req.setMimeType(mimeType);
                req.addRequestHeader("User-Agent", userAgent);
                // The session lives in a cookie and the download manager is a
                // separate process with its own cookie jar. Without this, a
                // payslip download quietly fetches the login page instead.
                String cookie = CookieManager.getInstance().getCookie(url);
                if (cookie != null) req.addRequestHeader("Cookie", cookie);
                req.setTitle(name);
                req.setDescription("Downloading from Pixous HR");
                req.setNotificationVisibility(
                        DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name);

                DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
                dm.enqueue(req);
                Toast.makeText(this, "Downloading " + name, Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "Could not start the download.", Toast.LENGTH_LONG).show();
            }
        });

        WebView.setWebContentsDebuggingEnabled(false);
    }

    private boolean granted(String permission) {
        return ContextCompat.checkSelfPermission(this, permission)
                == PackageManager.PERMISSION_GRANTED;
    }

    // The WebView holds the signed-in session and the page state, so it is
    // saved and restored rather than reloaded -- rotating the phone in the
    // middle of a form should not empty the form.
    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        web.saveState(outState);
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        web.restoreState(savedInstanceState);
    }

    @Override
    protected void onPause() {
        super.onPause();
        web.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
    }

    @Override
    protected void onDestroy() {
        // A WebView left attached after the activity goes takes the activity
        // with it.
        ViewGroup parent = (ViewGroup) web.getParent();
        if (parent != null) parent.removeView(web);
        web.destroy();
        super.onDestroy();
    }
}
