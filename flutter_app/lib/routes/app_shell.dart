import 'package:flutter/material.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../features/attendance/attendance_screen.dart';
import '../features/dashboard/dashboard_screen.dart';
import '../features/leave/leave_screen.dart';
import '../features/more/more_screen.dart';
import '../features/profile/profile_screen.dart';
import '../providers/app_providers.dart';
import '../providers/modules_provider.dart';

/// One destination in the bottom bar, and what has to be true for it to appear.
class _Tab {
  const _Tab({
    required this.label,
    required this.icon,
    required this.selectedIcon,
    required this.screen,
    this.moduleCode,
    this.hiddenForAdmin = false,
  });

  final String label;
  final IconData icon;
  final IconData selectedIcon;
  final Widget screen;

  /// Null for a destination that is not a module — Profile, More.
  final String? moduleCode;

  /// Mirrors the web sidebar's `excludeRole`. An administrator does not punch in
  /// or apply for their own leave; they approve other people's.
  ///
  /// Belt and braces. Administrators cannot sign in to this app at all — see
  /// MobileAccess — so nothing currently reaches this. Kept because the two
  /// rules answer different questions: that one decides who gets in, this one
  /// decides what they would see, and relaxing the first should not silently
  /// hand somebody a punch-in button.
  final bool hiddenForAdmin;
}

/// The tabs, gated exactly as the web client gates its sidebar.
///
/// A module switched off has to disappear here too. It did not: the app read no
/// settings at all, so switching Chat off hid it in the browser and left it on
/// every phone — which is worse than not having the setting, because somebody
/// believes a module is off when it is still in a colleague's pocket.
class AppShell extends ConsumerStatefulWidget {
  const AppShell({super.key});

  @override
  ConsumerState<AppShell> createState() => _AppShellState();
}

class _AppShellState extends ConsumerState<AppShell> {
  int _index = 0;

  static const List<_Tab> _all = [
    _Tab(
      label: 'Home',
      icon: Icons.dashboard_outlined,
      selectedIcon: Icons.dashboard_rounded,
      screen: DashboardScreen(),
      moduleCode: 'DASHBOARD',
    ),
    _Tab(
      label: 'Attendance',
      icon: Icons.access_time_outlined,
      selectedIcon: Icons.access_time_filled_rounded,
      screen: AttendanceScreen(),
      moduleCode: 'ATTENDANCE',
      hiddenForAdmin: true,
    ),
    _Tab(
      label: 'Leave',
      icon: Icons.event_note_outlined,
      selectedIcon: Icons.event_note_rounded,
      screen: LeaveScreen(),
      moduleCode: 'LEAVE',
      hiddenForAdmin: true,
    ),
    _Tab(
      label: 'More',
      icon: Icons.grid_view_outlined,
      selectedIcon: Icons.grid_view_rounded,
      screen: MoreScreen(),
    ),
    _Tab(
      label: 'Profile',
      icon: Icons.person_outline_rounded,
      selectedIcon: Icons.person_rounded,
      screen: ProfileScreen(),
    ),
  ];

  @override
  Widget build(BuildContext context) {
    final modules = ref.watch(modulesProvider);
    final user = ref.watch(currentUserProvider);
    final isAdmin = user?.isCompanyAdmin ?? false;

    final visible = _all.where((tab) {
      if (tab.hiddenForAdmin && isAdmin) return false;
      if (tab.moduleCode != null && !modules.has(tab.moduleCode!)) return false;
      return true;
    }).toList();

    // Profile is not a module and cannot be switched off, so this is only
    // reachable if the list itself were emptied — but an index past the end
    // throws, and the list shrinks whenever a module is switched off while
    // somebody is standing on that tab.
    if (visible.isEmpty) {
      return const Scaffold(body: SafeArea(child: _NothingEnabled()));
    }
    final index = _index.clamp(0, visible.length - 1);

    return Scaffold(
      body: IndexedStack(
        index: index,
        // Kept alive rather than rebuilt: each tab holds its scroll position and
        // its loaded data, which is what a person expects coming back to a tab
        // they were just on.
        children: [for (final tab in visible) tab.screen],
      ),
      bottomNavigationBar: NavigationBar(
        selectedIndex: index,
        onDestinationSelected: (i) => setState(() => _index = i),
        destinations: [
          for (final tab in visible)
            NavigationDestination(
              icon: Icon(tab.icon),
              selectedIcon: Icon(tab.selectedIcon),
              label: tab.label,
            ),
        ],
      ),
    );
  }
}

/// Shown when this company has no module the person in this role may open.
///
/// Without it they would sign in to an app with an empty bar and a blank panel,
/// indistinguishable from the application being broken. It is not broken;
/// nothing has been switched on for them yet, and that is worth saying in those
/// words. The same notice, in the same words, as the web client shows.
class _NothingEnabled extends StatelessWidget {
  const _NothingEnabled();

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(28),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              height: 56,
              width: 56,
              alignment: Alignment.center,
              decoration: BoxDecoration(
                color: Colors.amber.withValues(alpha: 0.16),
                shape: BoxShape.circle,
              ),
              child: const Icon(Icons.shield_outlined, color: Colors.amber, size: 28),
            ),
            const SizedBox(height: 18),
            Text(
              'Nothing is switched on for you yet',
              textAlign: TextAlign.center,
              style: Theme.of(context).textTheme.titleMedium?.copyWith(
                    fontWeight: FontWeight.w700,
                  ),
            ),
            const SizedBox(height: 8),
            Text(
              'Your account is fine and you are signed in correctly. No modules '
              'have been enabled for your company yet, so there are no pages to '
              'show.\n\nAsk your administrator to enable the modules your team needs.',
              textAlign: TextAlign.center,
              style: Theme.of(context)
                  .textTheme
                  .bodySmall
                  ?.copyWith(color: scheme.onSurfaceVariant, height: 1.5),
            ),
          ],
        ),
      ).animate().fadeIn(duration: 260.ms).slideY(begin: 0.04, end: 0),
    );
  }
}
