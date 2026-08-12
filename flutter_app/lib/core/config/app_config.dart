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

  static const String apiBaseUrl = String.fromEnvironment(
    'API_BASE_URL',
    defaultValue: 'https://pixous-ems-backend.onrender.com/api',
  );

  /// The websocket the portal uses for notifications, chat and presence.
  static const String wsUrl = String.fromEnvironment(
    'WS_URL',
    defaultValue: 'https://pixous-ems-backend.onrender.com/ws',
  );

  /// Generous, because the hosted database is on shared infrastructure and a
  /// cold request there can genuinely take this long.
  static const Duration connectTimeout = Duration(seconds: 30);
  static const Duration receiveTimeout = Duration(seconds: 30);

  /// Rows per page wherever a list is paged. Matches the web client so the two
  /// behave the same against the same endpoints.
  static const int pageSize = 20;

  /// Only enforced for release builds; a debug build often points at a local
  /// backend over plain http.
  static bool get requireHttps =>
      const bool.fromEnvironment('dart.vm.product') &&
      !apiBaseUrl.startsWith('https://');
}
