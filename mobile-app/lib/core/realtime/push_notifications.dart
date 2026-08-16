import 'package:flutter_local_notifications/flutter_local_notifications.dart';

/// Showing what the server pushed, on the phone itself.
///
/// Not Firebase. These are notifications raised locally the moment the live
/// socket delivers something, which is a real distinction worth being clear
/// about: they arrive while the app is running or in the background with its
/// socket alive, and they do not arrive when Android has killed the process.
///
/// FCM is what survives that, and it needs a Firebase project, a server key and
/// a backend that sends to it — none of which exists yet. Calling this "push
/// notifications" and stopping there would leave somebody believing their phone
/// will buzz overnight.
class PushNotifications {
  PushNotifications._();

  static final PushNotifications instance = PushNotifications._();

  final _plugin = FlutterLocalNotificationsPlugin();
  bool _ready = false;

  /// One channel, named for what it carries.
  ///
  /// Android shows channels in system settings, so the name is read by people:
  /// it lets somebody silence work notifications for the evening without
  /// silencing the phone.
  static const _channel = AndroidNotificationChannel(
    'hrp_alerts',
    'HR Portal alerts',
    description: 'Leave decisions, messages, tasks and announcements.',
    importance: Importance.high,
  );

  Future<void> init() async {
    if (_ready) return;

    const settings = InitializationSettings(
      android: AndroidInitializationSettings('@mipmap/ic_launcher'),
    );

    try {
      await _plugin.initialize(settings);
      await _plugin
          .resolvePlatformSpecificImplementation<
              AndroidFlutterLocalNotificationsPlugin>()
          ?.createNotificationChannel(_channel);

      // Android 13 and later. Asked once, here, rather than at first launch in
      // a burst with location and camera — a permission requested with no
      // context is the one people refuse.
      await _plugin
          .resolvePlatformSpecificImplementation<
              AndroidFlutterLocalNotificationsPlugin>()
          ?.requestNotificationsPermission();

      _ready = true;
    } catch (_) {
      // A phone that will not give us notifications still gets a working app.
      // Everything below simply does nothing.
    }
  }

  Future<void> show({
    required String title,
    required String body,
    int? id,
  }) async {
    if (!_ready) return;
    try {
      await _plugin.show(
        // Keyed by the thing it is about where the caller knows it, so a second
        // update about the same leave request replaces the first rather than
        // stacking. Falls back to the clock for anything unkeyed.
        id ?? DateTime.now().millisecondsSinceEpoch.remainder(100000),
        title,
        body,
        NotificationDetails(
          android: AndroidNotificationDetails(
            _channel.id,
            _channel.name,
            channelDescription: _channel.description,
            importance: Importance.high,
            priority: Priority.high,
            // Long text, so a message is readable when pulled down instead of
            // ending in an ellipsis.
            styleInformation: BigTextStyleInformation(body),
          ),
        ),
      );
    } catch (_) {
      // Never let a notification failure reach the code that produced the event.
    }
  }
}
