import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import '../../models/work_items.dart';
import '../../providers/app_providers.dart';
import '../../widgets/states.dart';

final notificationsProvider = FutureProvider.autoDispose<List<AppNotification>>(
  (ref) => ref.watch(workRepositoryProvider).notifications(),
);

/// Unread count for the bell. Kept separate from the list so the badge can
/// refresh on its own.
final unreadCountProvider = FutureProvider.autoDispose<int>(
  (ref) => ref.watch(workRepositoryProvider).unreadCount(),
);

class NotificationsScreen extends ConsumerWidget {
  const NotificationsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(notificationsProvider);

    return Scaffold(
      appBar: AppBar(
        title: const Text('Notifications'),
        actions: [
          async.maybeWhen(
            data: (items) => items.any((n) => !n.read)
                ? TextButton(
                    onPressed: () async {
                      try {
                        await ref
                            .read(workRepositoryProvider)
                            .markAllNotificationsRead();
                        ref
                          ..invalidate(notificationsProvider)
                          ..invalidate(unreadCountProvider);
                      } catch (e) {
                        if (!context.mounted) return;
                        ScaffoldMessenger.of(
                          context,
                        ).showSnackBar(SnackBar(content: Text(e.toString())));
                      }
                    },
                    child: const Text('Mark all read'),
                  )
                : const SizedBox.shrink(),
            orElse: () => const SizedBox.shrink(),
          ),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: () async {
          ref
            ..invalidate(notificationsProvider)
            ..invalidate(unreadCountProvider);
          await ref.read(notificationsProvider.future);
        },
        child: async.when(
          loading: () => const LoadingList(itemHeight: 84),
          // "Couldn't load" and "you have none" are different answers, and the
          // second is a claim about the inbox we are in no position to make when
          // the request failed.
          error: (e, _) => ListView(
            physics: const AlwaysScrollableScrollPhysics(),
            children: [
              SizedBox(
                height: MediaQuery.sizeOf(context).height * 0.65,
                child: ErrorState(
                  title: "Couldn't load your notifications",
                  message:
                      'The server did not answer. This does not mean you have none.',
                  onRetry: () => ref.invalidate(notificationsProvider),
                ),
              ),
            ],
          ),
          data: (items) => items.isEmpty
              ? ListView(
                  physics: const AlwaysScrollableScrollPhysics(),
                  children: [
                    SizedBox(
                      height: MediaQuery.sizeOf(context).height * 0.65,
                      child: const EmptyState(
                        icon: Icons.notifications_none_rounded,
                        title: "You're all caught up",
                        description:
                            'Alerts about leave, attendance and tickets appear here.',
                      ),
                    ),
                  ],
                )
              : ListView.separated(
                  physics: const AlwaysScrollableScrollPhysics(),
                  padding: const EdgeInsets.all(16),
                  itemCount: items.length,
                  separatorBuilder: (_, __) => const SizedBox(height: 10),
                  itemBuilder: (context, i) =>
                      _NotificationTile(notification: items[i]),
                ),
        ),
      ),
    );
  }
}

class _NotificationTile extends ConsumerWidget {
  const _NotificationTile({required this.notification});

  final AppNotification notification;

  IconData get _icon => switch (notification.type?.toUpperCase()) {
    'LEAVE' => Icons.event_note_rounded,
    'ATTENDANCE' => Icons.access_time_rounded,
    'TASK' => Icons.checklist_rounded,
    'PERMISSION' => Icons.schedule_rounded,
    'CHAT' => Icons.chat_bubble_outline_rounded,
    'ANNOUNCEMENT' => Icons.campaign_rounded,
    _ => Icons.notifications_none_rounded,
  };

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final scheme = Theme.of(context).colorScheme;
    final unread = !notification.read;

    return Card(
      child: InkWell(
        borderRadius: BorderRadius.circular(14),
        onTap: unread
            ? () async {
                try {
                  await ref
                      .read(workRepositoryProvider)
                      .markNotificationRead(notification.id);
                  ref
                    ..invalidate(notificationsProvider)
                    ..invalidate(unreadCountProvider);
                } catch (_) {
                  // Marking as read is not worth interrupting anyone over.
                }
              }
            : null,
        child: Padding(
          padding: const EdgeInsets.all(14),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Container(
                padding: const EdgeInsets.all(9),
                decoration: BoxDecoration(
                  color: (unread ? scheme.primary : scheme.onSurfaceVariant)
                      .withValues(alpha: 0.12),
                  borderRadius: BorderRadius.circular(10),
                ),
                child: Icon(
                  _icon,
                  size: 19,
                  color: unread ? scheme.primary : scheme.onSurfaceVariant,
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      notification.title,
                      style: TextStyle(
                        fontWeight: unread ? FontWeight.w700 : FontWeight.w500,
                      ),
                    ),
                    if (notification.body?.isNotEmpty == true) ...[
                      const SizedBox(height: 3),
                      Text(
                        notification.body!,
                        style: Theme.of(context).textTheme.bodySmall?.copyWith(
                          color: scheme.onSurfaceVariant,
                        ),
                      ),
                    ],
                    if (notification.createdAt != null) ...[
                      const SizedBox(height: 6),
                      Text(
                        DateFormat(
                          'd MMM, h:mm a',
                        ).format(notification.createdAt!),
                        style: TextStyle(
                          fontSize: 11,
                          color: scheme.onSurfaceVariant,
                        ),
                      ),
                    ],
                  ],
                ),
              ),
              if (unread)
                Container(
                  margin: const EdgeInsets.only(left: 8, top: 4),
                  width: 8,
                  height: 8,
                  decoration: BoxDecoration(
                    color: scheme.primary,
                    shape: BoxShape.circle,
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }
}
