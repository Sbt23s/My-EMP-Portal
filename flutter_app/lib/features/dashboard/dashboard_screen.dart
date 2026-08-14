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

class _Content extends ConsumerWidget {
  const _Content({required this.data});

  final EmployeeDashboard data;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final scheme = Theme.of(context).colorScheme;
    final modules = ref.watch(modulesProvider);
    final brand = ref.watch(brandingProvider);

    /*
     * Tiles belonging to a switched-off module do not appear.
     *
     * The web dashboard gates all thirteen of its widgets this way, and the
     * reason is the same here: a company with Assets switched off has nobody who
     * can be issued an asset, so "Assets with me: 0" is not a fact about them,
     * it is a feature leaking through a setting that was supposed to hide it.
     *
     * Built as a list because the heading above depends on it — "My overview"
     * over nothing is worse than no heading.
     */
    final tiles = <Widget>[
      if (modules.has('LEAVE'))
        StatCard(
          label: 'Pending leave requests',
          value: '${data.pendingLeaveRequests}',
          icon: Icons.event_note_rounded,
          tint: AppTheme.warning(context),
        ),
      if (modules.has('HELPDESK'))
        StatCard(
          label: 'Open tickets',
          value: '${data.myOpenTickets}',
          icon: Icons.support_agent_rounded,
          tint: scheme.tertiary,
        ),
      if (modules.has('ASSETS'))
        StatCard(
          label: 'Assets with me',
          value: '${data.myAssets}',
          icon: Icons.inventory_2_outlined,
          tint: scheme.primary,
        ),
      if (modules.has('ATTENDANCE'))
        StatCard(
          label: 'Worked today',
          value: data.workedLabel,
          icon: Icons.timer_outlined,
          tint: AppTheme.success(context),
        ),
    ];

    return ListView(
      physics: const AlwaysScrollableScrollPhysics(),
      padding: const EdgeInsets.all(16),
      children: [
        // The company's own welcome line, where it has written one. Set in the
        // branding screen; absent for everyone who has not.
        if (brand.welcomeText != null) ...[
          Text(
            brand.welcomeText!,
            style: Theme.of(context)
                .textTheme
                .bodyMedium
                ?.copyWith(color: scheme.onSurfaceVariant),
          ),
          const SizedBox(height: 14),
        ],
        // Nagging someone to punch in when they have no way to punch is the
        // module leaking through the greeting.
        if (modules.has('ATTENDANCE'))
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
        if (tiles.isNotEmpty) ...[
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
              for (var i = 0; i < tiles.length; i++)
                tiles[i]
                    .animate()
                    .fadeIn(delay: (i * 60).ms, duration: 240.ms)
                    .slideY(begin: 0.10, end: 0, curve: Curves.easeOutCubic),
            ],
          ),
        ],
        if (modules.has('LEAVE') && data.leaveBalances.isNotEmpty) ...[
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
