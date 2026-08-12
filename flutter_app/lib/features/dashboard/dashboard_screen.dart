import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import '../../models/dashboard.dart';
import '../../providers/app_providers.dart';
import '../../themes/app_theme.dart';
import '../../widgets/states.dart';
import '../notifications/notifications_screen.dart';

/// What the dashboard shows, fetched fresh.
///
/// A failure surfaces as a failure. Nothing here substitutes invented numbers
/// for a request that did not come back.
final myDashboardProvider = FutureProvider.autoDispose<EmployeeDashboard>((
  ref,
) async {
  return ref.watch(workRepositoryProvider).myDashboard();
});

class DashboardScreen extends ConsumerWidget {
  const DashboardScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final user = ref.watch(currentUserProvider);
    final async = ref.watch(myDashboardProvider);

    return Scaffold(
      appBar: AppBar(
        title: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              DateFormat('EEEE, d MMMM').format(DateTime.now()),
              style: Theme.of(context).textTheme.bodySmall,
            ),
            Text(user?.name.isNotEmpty == true ? user!.name : 'Dashboard'),
          ],
        ),
        actions: const [_NotificationBell()],
      ),
      body: RefreshIndicator(
        onRefresh: () => ref.refresh(myDashboardProvider.future),
        child: async.when(
          loading: () => const LoadingList(itemCount: 4, itemHeight: 96),
          error: (e, _) => ListView(
            // A scrollable is needed for pull-to-refresh to work while failed.
            physics: const AlwaysScrollableScrollPhysics(),
            children: [
              SizedBox(
                height: MediaQuery.sizeOf(context).height * 0.6,
                child: ErrorState(
                  message: e.toString(),
                  onRetry: () => ref.invalidate(myDashboardProvider),
                ),
              ),
            ],
          ),
          data: (d) => _Content(data: d),
        ),
      ),
    );
  }
}

/// The bell, with a count only when there genuinely is one.
///
/// A failed count shows no badge rather than a number. The web client answered
/// a failed request with "2", so the bell advertised unread items that did not
/// exist — showing nothing is the honest answer when we do not know.
class _NotificationBell extends ConsumerWidget {
  const _NotificationBell();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final count = ref
        .watch(unreadCountProvider)
        .maybeWhen(data: (c) => c, orElse: () => 0);

    return Stack(
      alignment: Alignment.center,
      children: [
        IconButton(
          tooltip: 'Notifications',
          icon: const Icon(Icons.notifications_none_rounded),
          onPressed: () {
            Navigator.of(context)
                .push(
                  MaterialPageRoute<void>(
                    builder: (_) => const NotificationsScreen(),
                  ),
                )
                .then((_) {
                  ref.invalidate(unreadCountProvider);
                });
          },
        ),
        if (count > 0)
          Positioned(
            top: 8,
            right: 8,
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 5, vertical: 1),
              constraints: const BoxConstraints(minWidth: 16),
              decoration: BoxDecoration(
                color: Theme.of(context).colorScheme.error,
                borderRadius: BorderRadius.circular(10),
              ),
              child: Text(
                count > 9 ? '9+' : '$count',
                textAlign: TextAlign.center,
                style: const TextStyle(
                  color: Colors.white,
                  fontSize: 10,
                  fontWeight: FontWeight.w700,
                ),
              ),
            ),
          ),
      ],
    );
  }
}

class _Content extends StatelessWidget {
  const _Content({required this.data});

  final EmployeeDashboard data;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;

    return ListView(
      physics: const AlwaysScrollableScrollPhysics(),
      padding: const EdgeInsets.all(16),
      children: [
        Card(
          child: Padding(
            padding: const EdgeInsets.all(18),
            child: Row(
              children: [
                Container(
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color:
                        (data.punchedInToday
                                ? AppTheme.success(context)
                                : scheme.primary)
                            .withValues(alpha: 0.12),
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: Icon(
                    data.punchedInToday
                        ? Icons.check_circle_outline_rounded
                        : Icons.schedule_rounded,
                    color: data.punchedInToday
                        ? AppTheme.success(context)
                        : scheme.primary,
                  ),
                ),
                const SizedBox(width: 14),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        data.punchedInToday
                            ? 'Punched in'
                            : "You haven't punched in yet",
                        style: Theme.of(context).textTheme.titleMedium
                            ?.copyWith(fontWeight: FontWeight.w600),
                      ),
                      const SizedBox(height: 2),
                      Text(
                        data.punchInAt != null
                            ? 'Since ${DateFormat('h:mm a').format(data.punchInAt!)} · ${data.workedLabel}'
                            : 'Mark your attendance from the Attendance tab',
                        style: Theme.of(context).textTheme.bodySmall?.copyWith(
                          color: scheme.onSurfaceVariant,
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
        ),
        const SizedBox(height: 20),
        Text(
          'My overview',
          style: Theme.of(context).textTheme.titleSmall?.copyWith(
            fontWeight: FontWeight.w700,
            letterSpacing: 0.3,
          ),
        ),
        const SizedBox(height: 12),
        GridView.count(
          crossAxisCount: 2,
          shrinkWrap: true,
          physics: const NeverScrollableScrollPhysics(),
          mainAxisSpacing: 12,
          crossAxisSpacing: 12,
          childAspectRatio: 1.35,
          children: [
            StatCard(
              label: 'Pending leave requests',
              value: '${data.pendingLeaveRequests}',
              icon: Icons.event_note_rounded,
              tint: AppTheme.warning(context),
            ),
            StatCard(
              label: 'Open tickets',
              value: '${data.myOpenTickets}',
              icon: Icons.support_agent_rounded,
              tint: scheme.tertiary,
            ),
            StatCard(
              label: 'Assets with me',
              value: '${data.myAssets}',
              icon: Icons.inventory_2_outlined,
              tint: scheme.primary,
            ),
            StatCard(
              label: 'Worked today',
              value: data.workedLabel,
              icon: Icons.timer_outlined,
              tint: AppTheme.success(context),
            ),
          ],
        ),
        if (data.leaveBalances.isNotEmpty) ...[
          const SizedBox(height: 24),
          Text(
            'Leave balance',
            style: Theme.of(context).textTheme.titleSmall?.copyWith(
              fontWeight: FontWeight.w700,
              letterSpacing: 0.3,
            ),
          ),
          const SizedBox(height: 12),
          Card(
            child: Column(
              children: [
                for (var i = 0; i < data.leaveBalances.length; i++) ...[
                  if (i > 0) const Divider(height: 1),
                  ListTile(
                    title: Text(data.leaveBalances[i].leaveTypeName),
                    subtitle: Text(
                      '${data.leaveBalances[i].used} used '
                      'of ${data.leaveBalances[i].allocated}',
                    ),
                    trailing: Text(
                      '${data.leaveBalances[i].available}',
                      style: Theme.of(context).textTheme.titleMedium?.copyWith(
                        fontWeight: FontWeight.w700,
                        color: scheme.primary,
                      ),
                    ),
                  ),
                ],
              ],
            ),
          ),
        ],
        const SizedBox(height: 24),
      ],
    );
  }
}
