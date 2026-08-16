/// Where the app points, and the handful of numbers that shape its behaviour.
///
/// The base URL is a compile-time constant so a release build cannot be pointed
/// somewhere else at runtime:
///
///   flutter run --dart-define=API_BASE_URL=http://10.0.2.2:7060/api
///
/// 10.0.2.2 is the host machine as seen from the Android emulator; a device on
/// the same network needs the machine's LAN address. The default is the hosted
/// backend, so an install with no flags talks to the real thing.
class AppConfig {
  const AppConfig._();

  /// The live server.
  ///
  /// HTTPS, on the portal's own name. The app spent its early builds on plain
  /// http to a bare IP, because a certificate cannot be issued for an IP address
  /// — a domain was the missing piece, and now there is one.
  ///
  /// What that changes: sign-in credentials no longer cross the network in the
  /// clear. The Android cleartext exception that existed for the IP is gone with
  /// it, so this build cannot fall back to plain http even by accident.
  static const String apiBaseUrl = String.fromEnvironment(
    'API_BASE_URL',
    defaultValue: 'https://pixoushrportal.pixous.info/api',
  );

  /// The Python face-matching service.
  ///
  /// A different application behind the same nginx: `/analytics`, not `/api`,
  /// and it takes no bearer token. Derived from [apiBaseUrl] rather than written
  /// out again so the two cannot end up pointing at different servers — which is
  /// exactly how the app came to be aimed at a Render address the portal had
  /// stopped running on.
  static String get faceServiceBaseUrl {
    final origin = apiBaseUrl.endsWith('/api')
        ? apiBaseUrl.substring(0, apiBaseUrl.length - 4)
        : apiBaseUrl;
    return '$origin/analytics';
  }

  /// The websocket the *web* portal uses for notifications, chat and presence.
  ///
  /// Nothing in this app connects to it yet — no socket client exists, so the
  /// constant is tree-shaken straight out of the release binary. Kept because it
  /// is the right address when one is written, but read the sentence above
  /// before assuming the phone receives anything pushed: notifications here are
  /// whatever the screens fetch when they open.
  static const String wsUrl = String.fromEnvironment(
    'WS_URL',
    defaultValue: 'https://pixoushrportal.pixous.info/ws',
  );

  /// Generous, because the hosted database is on shared infrastructure and a
  /// cold request there can genuinely take this long.
  static const Duration connectTimeout = Duration(seconds: 30);
  static const Duration receiveTimeout = Duration(seconds: 30);

  /// Rows per page wherever a list is paged. Matches the web client so the two
  /// behave the same against the same endpoints.
  static const int pageSize = 20;

  /// Whether this build is talking to its server in the clear.
  ///
  /// False now, and Android enforces it: with the cleartext exception removed,
  /// a build pointed at an http:// address would fail every request rather than
  /// quietly sending passwords in plain text. Kept as a plain statement of fact
  /// — it claims nothing it does not decide.
  static bool get isCleartext => apiBaseUrl.startsWith('http://');
}
