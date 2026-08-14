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
  /// This pointed at a Render address that the portal has not run on since the
  /// EC2 rebuild. A release build carrying it installs cleanly, opens, and then
  /// fails every request — which reads as a broken app rather than as an app
  /// aimed at a server that is no longer there.
  ///
  /// Plain http because the portal has no certificate yet; HTTPS is waiting on a
  /// DNS A record for this address. Android refuses cleartext by default, so
  /// `android/app/src/main/res/xml/network_security_config.xml` permits it for
  /// this one host — see that file for what to undo once the certificate exists.
  static const String apiBaseUrl = String.fromEnvironment(
    'API_BASE_URL',
    defaultValue: 'http://16.192.105.61/api',
  );

  /// The websocket the *web* portal uses for notifications, chat and presence.
  ///
  /// Nothing in this app connects to it yet — no socket client exists, so the
  /// constant is tree-shaken straight out of the release binary. Kept because it
  /// is the right address when one is written, but read the sentence above
  /// before assuming the phone receives anything pushed: notifications here are
  /// whatever the screens fetch when they open.
  static const String wsUrl = String.fromEnvironment(
    'WS_URL',
    defaultValue: 'http://16.192.105.61/ws',
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
  /// Renamed from `requireHttps`, which promised something it never delivered:
  /// nothing read it. A getter whose name says a rule is enforced, sitting in a
  /// config file with no caller, is worse than no getter — the next person
  /// reading this file would take the guarantee at face value.
  ///
  /// What actually decides it is the Android network security config, which
  /// permits cleartext to one host and refuses it everywhere else. This is left
  /// as a plain statement of fact, for a banner or a log line to use.
  static bool get isCleartext => apiBaseUrl.startsWith('http://');
}
