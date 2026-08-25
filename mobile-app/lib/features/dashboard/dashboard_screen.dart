import 'package:flutter/material.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import '../../models/dashboard.dart';
import '../../providers/app_providers.dart';
import '../../providers/modules_provider.dart';
import '../../themes/app_theme.dart';
import '../../widgets/states.dart';
import '../more/more_screen.dart';
import '../notifications/notifications_screen.dart';
import '../../routes/app_shell.dart';
import '../../widgets/hero_cards.dart';
import '../../widgets/ui_kit.dart';

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
    final async = ref.watch(myDashboardProvider);

    return Scaffold(
      // No app bar: the greeting row below is the header, as in the design.
      body: SafeArea(
        bottom: false,
        child: RefreshIndicator(
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

  static String _greeting() {
    final h = DateTime.now().hour;
    if (h < 12) return 'Good Morning';
    if (h < 17) return 'Good Afternoon';
    return 'Good Evening';
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final scheme = Theme.of(context).colorScheme;
    final modules = ref.watch(modulesProvider);
    final brand = ref.watch(brandingProvider);
    final user = ref.watch(currentUserProvider);
    final now = DateTime.now();

    /*
     * Quick actions are gated by module, exactly as the tiles below are.
     *
     * A shortcut into a switched-off module is a dead end: it opens a screen
     * with nothing in it, or fails on a request the company does not have.
     * Better that it is not offered at all.
     */
    final quick = <Widget>[
      if (modules.has('LEAVE'))
        QuickAction(
          icon: Icons.event_note_rounded,
          label: 'Leave',
          color: scheme.primary,
          onTap: () => AppShell.go(context, 'Leave'),
        ),
      if (modules.has('ATTENDANCE'))
        QuickAction(
          icon: Icons.fingerprint_rounded,
          label: 'Attendance',
          color: AppTheme.success(context),
          onTap: () => AppShell.go(context, 'Attendance'),
        ),
      if (modules.has('PAYROLL'))
        QuickAction(
          icon: Icons.account_balance_wallet_rounded,
          label: 'Payslip',
          color: AppTheme.warning(context),
          onTap: () => Navigator.of(context).push(
            MaterialPageRoute<void>(builder: (_) => const PayslipsScreen()),
          ),
        ),
      QuickAction(
        icon: Icons.grid_view_rounded,
        label: 'More',
        color: scheme.tertiary,
        onTap: () => AppShell.go(context, 'More'),
      ),
    ];

    final tiles = <Widget>[
      if (modules.has('LEAVE'))
        StatCell(
          value: '${data.pendingLeaveRequests}',
          label: 'Pending',
          color: AppTheme.warning(context),
        ),
      if (modules.has('HELPDESK'))
        StatCell(
          value: '${data.myOpenTickets}',
          label: 'Tickets',
          color: scheme.tertiary,
        ),
      if (modules.has('ASSETS'))
        StatCell(
          value: '${data.myAssets}',
          label: 'Assets',
          color: scheme.primary,
        ),
      if (modules.has('ATTENDANCE'))
        StatCell(
          value: data.workedLabel,
          label: 'Worked',
          color: AppTheme.success(context),
        ),
    ];

    return ListView(
      physics: const AlwaysScrollableScrollPhysics(),
      padding: const EdgeInsets.fromLTRB(16, 8, 16, 28),
      children: [
        GreetingHeader(
          greeting: _greeting(),
          name: user?.name.isNotEmpty == true ? user!.name : 'Dashboard',
          subtitle: user?.designation ?? '',
          photoUrl: user?.photoPath,
          trailing: const _NotificationBell(),
        ),
        const SizedBox(height: 16),

        // The company's own welcome line, where it has written one.
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
          HeroCard(
            title: DateFormat('hh:mm a').format(now),
            subtitle: DateFormat('EEEE, d MMMM yyyy').format(now),
            badge: data.punchedInToday ? 'Checked In' : null,
            action: data.punchedInToday ? null : 'Go to Attendance',
            onAction: () => AppShell.go(context, 'Attendance'),
            footer: data.punchInAt != null
                ? Row(
                    children: [
                      const Icon(Icons.login_rounded,
                          size: 15, color: Colors.white70),
                      const SizedBox(width: 6),
                      Text(
                        'Since ${DateFormat('h:mm a').format(data.punchInAt!)}'
                        '  \u00b7  ${data.workedLabel}',
                        style: const TextStyle(
                          color: Colors.white70,
                          fontSize: 12,
                          fontWeight: FontWeight.w500,
                        ),
                      ),
                    ],
                  )
                : null,
          ).animate().fadeIn(duration: 260.ms).slideY(
                begin: 0.06,
                end: 0,
                curve: Curves.easeOutCubic,
              ),

        if (quick.isNotEmpty) ...[
          const SizedBox(height: 22),
          const SectionHeader('Quick Actions'),
          Container(
            padding: const EdgeInsets.symmetric(vertical: 10, horizontal: 6),
            decoration: UI.card(context),
            child: Row(
              children: [
                for (var i = 0; i < quick.length; i++)
                  Expanded(
                    child: quick[i]
                        .animate()
                        .fadeIn(delay: (i * 55).ms, duration: 220.ms),
                  ),
              ],
            ),
          ),
        ],

        if (tiles.isNotEmpty) ...[
          const SizedBox(height: 22),
          const SectionHeader('Overview'),
          Container(
            padding: const EdgeInsets.symmetric(vertical: 16, horizontal: 8),
            decoration: UI.card(context),
            child: Row(
              children: [
                for (final t in tiles) Expanded(child: t),
              ],
            ),
          ).animate().fadeIn(duration: 240.ms),
        ],

        if (modules.has('LEAVE') && data.leaveBalances.isNotEmpty) ...[
          const SizedBox(height: 22),
          const SectionHeader('Leave balance'),
          Container(
            decoration: UI.card(context),
            clipBehavior: Clip.antiAlias,
            child: Column(
              children: [
                for (var i = 0; i < data.leaveBalances.length; i++) ...[
                  if (i > 0) const Divider(height: 1),
                  ListTile(
                    dense: true,
                    title: Text(
                      data.leaveBalances[i].leaveTypeName,
                      style: const TextStyle(
                        fontWeight: FontWeight.w600,
                        fontSize: 13.5,
                      ),
                    ),
                    subtitle: Text(
                      '${data.leaveBalances[i].used} used '
                      'of ${data.leaveBalances[i].allocated}',
                      style: const TextStyle(fontSize: 11.5),
                    ),
                    trailing: Text(
                      '${data.leaveBalances[i].available}',
                      style: Theme.of(context).textTheme.titleMedium?.copyWith(
                            fontWeight: FontWeight.w800,
                            color: scheme.primary,
                          ),
                    ),
                  ),
                ],
              ],
            ),
          ),
        ],
      ],
    );
  }
}
