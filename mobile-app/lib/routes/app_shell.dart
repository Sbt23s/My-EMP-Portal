import 'dart:async';

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
import '../core/calls/call_service.dart';
import '../features/calls/call_screen.dart';
import '../providers/call_provider.dart';
import '../providers/realtime_provider.dart';

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
  final String? moduleCode;
  final bool hiddenForAdmin;
}

/// The tabs, gated exactly as the web client gates its sidebar.
class AppShell extends ConsumerStatefulWidget {
  const AppShell({super.key});

  @override
  ConsumerState<AppShell> createState() => _AppShellState();
}

class _AppShellState extends ConsumerState<AppShell> {
  int _index = 0;
  StreamSubscription<String>? _callErrorSub;

  /// Tracks the previous call state so we can detect transitions and push/pop
  /// the CallScreen overlay at the right moment.
  CallState _prevCallState = CallState.idle;

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
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      final callService = ref.read(callServiceProvider);
      _callErrorSub = callService.errors.listen((msg) {
        if (!mounted) return;
        ScaffoldMessenger.of(context)
          ..hideCurrentSnackBar()
          ..showSnackBar(
            SnackBar(
              content: Text(msg),
              backgroundColor: Theme.of(context).colorScheme.error,
            ),
          );
      });
    });
  }

  @override
  void dispose() {
    _callErrorSub?.cancel();
    super.dispose();
  }

  /// Push the CallScreen as a full-screen route on top of whatever is showing.
  /// When the call ends, the CallScreen pops itself and the user returns to
  /// exactly where they were — chat, dashboard, more screen, anything.
  void _handleCallTransition(CallState newState) {
    final wentActive = newState != CallState.idle && _prevCallState == CallState.idle;
    final wentIdle = newState == CallState.idle && _prevCallState != CallState.idle;
    _prevCallState = newState;

    if (wentActive && mounted) {
      Navigator.of(context).push(
        PageRouteBuilder(
          opaque: true,
          fullscreenDialog: true,
          transitionDuration: Duration.zero,
          pageBuilder: (_, __, ___) => const _CallOverlay(),
          settings: const RouteSettings(name: 'call'),
        ),
      );
    }
    if (wentIdle && mounted) {
      // Pop the CallScreen overlay — it will return to whatever was underneath.
      final nav = Navigator.of(context);
      // Only pop if the current route is the call overlay.
      if (nav.canPop()) {
        nav.pop();
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    ref.watch(realtimeBinderProvider);
    ref.watch(callBinderProvider);
    ref.watch(callChangesProvider);

    // After building, check for call state transitions so we can push/pop
    // the call overlay at the right moment.
    final call = ref.watch(callServiceProvider);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _handleCallTransition(call.state);
    });

    final modules = ref.watch(modulesProvider);
    final user = ref.watch(currentUserProvider);
    final isAdmin = user?.isCompanyAdmin ?? false;

    final visible = _all.where((tab) {
      if (tab.hiddenForAdmin && isAdmin) return false;
      if (tab.moduleCode != null && !modules.has(tab.moduleCode!)) return false;
      return true;
    }).toList();

    if (visible.isEmpty) {
      return const Scaffold(body: SafeArea(child: _NothingEnabled()));
    }
    final index = _index.clamp(0, visible.length - 1);

    return Scaffold(
      body: IndexedStack(
        index: index,
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

/// The CallScreen pushed as a route overlay. When the call ends (state
/// returns to idle), it pops itself so the user returns to whatever screen
/// they were on before the call started.

class _CallOverlay extends ConsumerStatefulWidget {
  const _CallOverlay();

  @override
  ConsumerState<_CallOverlay> createState() => _CallOverlayState();
}

class _CallOverlayState extends ConsumerState<_CallOverlay> {
  CallState _lastSeen = CallState.outgoing;
  bool _popping = false;

  @override
  Widget build(BuildContext context) {
    final call = ref.watch(callServiceProvider);

    // When the call ends, pop this overlay immediately. Use a synchronous
    // flag to avoid rebuilding with an empty peer on the next frame.
    if (call.state == CallState.idle && _lastSeen != CallState.idle && !_popping) {
      _popping = true;
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted && Navigator.of(context).canPop()) {
          Navigator.of(context).pop();
        }
      });
    }
    _lastSeen = call.state;

    // While popping, show nothing (transparent) so the pop animation reveals
    // whatever is underneath rather than flashing an empty call screen.
    if (_popping) {
      return const Scaffold(backgroundColor: Colors.transparent);
    }

    return const CallScreen();
  }
}

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
