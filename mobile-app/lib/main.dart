import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'features/auth/login_screen.dart';
import 'core/realtime/push_notifications.dart';
import 'providers/app_providers.dart';
import 'providers/modules_provider.dart';
import 'providers/theme_provider.dart';
import 'routes/app_shell.dart';
import 'themes/app_theme.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  // Channel and permission set up before anything can try to raise one.
  // Awaiting it would hold the first frame for a dialog nobody has context for.
  unawaited(PushNotifications.instance.init());
  runApp(const ProviderScope(child: HrPortalApp()));
}

class HrPortalApp extends ConsumerStatefulWidget {
  const HrPortalApp({super.key});

  @override
  ConsumerState<HrPortalApp> createState() => _HrPortalAppState();
}

class _HrPortalAppState extends ConsumerState<HrPortalApp> {
  @override
  void initState() {
    super.initState();
    // Ask the server who this token belongs to before anything is drawn. A
    // token the server rejects ends the session here, so the app can never show
    // a signed-in shell with nothing behind it.
    WidgetsBinding.instance.addPostFrameCallback((_) {
      ref.read(authProvider.notifier).restore();
    });
  }

  @override
  Widget build(BuildContext context) {
    final auth = ref.watch(authProvider);

    /*
     * The company's look, applied at the root.
     *
     * Watched here rather than per screen so the whole tree repaints together
     * when it arrives: applying it further down would leave the app bar in one
     * colour and the page beneath it in another for a frame, which reads worse
     * than the colour arriving a moment late.
     *
     * Signed out, this resolves to the app's own palette. The sign-in screen is
     * unbranded on purpose — nobody has said who they are yet, so there is no
     * company whose colours apply.
     */
    final brand = ref.watch(brandingProvider);

    return MaterialApp(
      title: brand.productName ?? 'Pixous HR Portal',
      debugShowCheckedModeBanner: false,
      theme: AppTheme.branded(Brightness.light, brand),
      darkTheme: AppTheme.branded(Brightness.dark, brand),
      // The phone's setting, unless somebody has said otherwise. The portal has
      // a light/dark button in its top bar; this had none, so a person who keeps
      // their phone dark had no way to read a payslip in light.
      themeMode: ref.watch(themeModeProvider),
      /*
       * Keep somebody's font-size choice, but not past the point where the
       * layout gives up.
       *
       * Android lets a phone scale every label by up to 2x. This app has a few
       * hundred rows and cards built to a fixed height -- a summary tile, a
       * table row, a call control -- and at 2x the text inside them no longer
       * fits: it clips, or Flutter paints the yellow-and-black overflow stripe
       * over the screen. Neither is readable, which defeats the point of asking
       * for larger text in the first place.
       *
       * 1.3 is the most the current layouts absorb without clipping. Below
       * that the phone's setting is honoured exactly, so somebody who needs
       * slightly larger text still gets it -- they are simply not offered a
       * size that breaks the page.
       *
       * The proper fix is for those heights to be intrinsic rather than fixed,
       * screen by screen. Until then this is the difference between "a bit
       * small for me" and "unusable".
       */
      builder: (context, child) {
        final media = MediaQuery.of(context);
        final scale = media.textScaler.clamp(
          minScaleFactor: 1.0,
          maxScaleFactor: 1.3,
        );
        return MediaQuery(
          data: media.copyWith(textScaler: scale),
          child: child ?? const SizedBox.shrink(),
        );
      },
      home: switch (auth.status) {
        AuthStatus.checking when auth.user == null => const _Splash(),
        AuthStatus.signedIn => const AppShell(),
        _ => const LoginScreen(),
      },
    );
  }
}

class _Splash extends StatelessWidget {
  const _Splash();

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Scaffold(
      body: Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              height: 64,
              width: 64,
              alignment: Alignment.center,
              decoration: BoxDecoration(
                color: scheme.primary,
                borderRadius: BorderRadius.circular(18),
              ),
              child: const Icon(
                Icons.apartment_rounded,
                color: Colors.white,
                size: 32,
              ),
            ),
            const SizedBox(height: 28),
            const SizedBox(
              height: 22,
              width: 22,
              child: CircularProgressIndicator(strokeWidth: 2.4),
            ),
          ],
        ),
      ),
    );
  }
}
