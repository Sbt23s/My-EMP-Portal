import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/realtime/push_notifications.dart';
import '../core/realtime/realtime_service.dart';
import 'app_providers.dart';

final Provider<RealtimeService> realtimeServiceProvider =
    Provider<RealtimeService>((ref) {
  final service = RealtimeService(ref.watch(tokenStoreProvider));
  ref.onDispose(service.dispose);
  return service;
});

/// Everything the server pushes to this person, as one stream.
final StreamProvider<RealtimeEvent> realtimeEventsProvider =
    StreamProvider<RealtimeEvent>((ref) {
  return ref.watch(realtimeServiceProvider).events;
});

/// Connects when somebody signs in, disconnects when they leave.
///
/// Kept in one place rather than each screen opening its own socket. Twenty
/// screens each connecting would be twenty sockets against a server that counts
/// its database connections in tens, and a notification would arrive once per
/// open screen.
///
/// Watched from the shell so it lives exactly as long as a session does.
final Provider<void> realtimeBinderProvider = Provider<void>((ref) {
  final user = ref.watch(currentUserProvider);
  final service = ref.watch(realtimeServiceProvider);

  if (user == null) {
    service.disconnect();
    return;
  }

  service.connect();
  // Notifications are addressed to a person; chat arrives per channel and is
  // subscribed by the room that is open. Tasks come here too — a task assigned
  // to somebody is worth a buzz whether or not the task screen is open.
  service.subscribe('/topic/notifications/${user.id}');
  service.subscribe('/topic/tasks/${user.id}');

  // Raise a phone notification for anything that arrives while the app is not
  // the thing being looked at.
  final sub = service.events.listen((event) {
    if (!event.topic.startsWith('/topic/notifications/')) return;
    final title = event.body['title']?.toString();
    final body = event.body['body']?.toString() ?? event.body['message']?.toString();
    if (title == null && body == null) return;
    PushNotifications.instance.show(
      title: title ?? 'HR Portal',
      body: body ?? '',
      id: (event.body['id'] as num?)?.toInt(),
    );
  });

  ref.onDispose(sub.cancel);
});
