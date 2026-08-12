import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import '../../models/attendance.dart';
import '../../providers/app_providers.dart';
import '../../themes/app_theme.dart';
import '../../widgets/states.dart';

final todayAttendanceProvider = FutureProvider.autoDispose<AttendanceDay?>(
  (ref) => ref.watch(workRepositoryProvider).today(),
);

/// This month so far.
final monthAttendanceProvider = FutureProvider.autoDispose<List<AttendanceDay>>(
  (ref) {
    final now = DateTime.now();
    return ref
        .watch(workRepositoryProvider)
        .attendanceBetween(DateTime(now.year, now.month, 1), now);
  },
);

class AttendanceScreen extends ConsumerStatefulWidget {
  const AttendanceScreen({super.key});

  @override
  ConsumerState<AttendanceScreen> createState() => _AttendanceScreenState();
}

class _AttendanceScreenState extends ConsumerState<AttendanceScreen> {
  /// Guards the punch buttons. Two taps must not become two punches — the
  /// server rejects the second, but the person should not see an error for
  /// something they did not mean to do twice.
  bool _busy = false;

  Future<void> _punch({required bool punchIn}) async {
    if (_busy) return;
    setState(() => _busy = true);
    try {
      final repo = ref.read(workRepositoryProvider);
      punchIn ? await repo.punchIn() : await repo.punchOut();
      if (!mounted) return;
      ref
        ..invalidate(todayAttendanceProvider)
        ..invalidate(monthAttendanceProvider);
      ScaffoldMessenger.of(context)
        ..hideCurrentSnackBar()
        ..showSnackBar(
          SnackBar(content: Text(punchIn ? 'Punched in' : 'Punched out')),
        );
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context)
        ..hideCurrentSnackBar()
        ..showSnackBar(
          SnackBar(
            content: Text(e.toString()),
            backgroundColor: Theme.of(context).colorScheme.error,
          ),
        );
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final today = ref.watch(todayAttendanceProvider);
    final month = ref.watch(monthAttendanceProvider);
    final scheme = Theme.of(context).colorScheme;

    return Scaffold(
      appBar: AppBar(title: const Text('Attendance')),
      body: RefreshIndicator(
        onRefresh: () async {
          ref
            ..invalidate(todayAttendanceProvider)
            ..invalidate(monthAttendanceProvider);
          await ref.read(todayAttendanceProvider.future);
        },
        child: ListView(
          physics: const AlwaysScrollableScrollPhysics(),
          padding: const EdgeInsets.all(16),
          children: [
            today.when(
              loading: () => const Card(
                child: SizedBox(
                  height: 150,
                  child: Center(child: CircularProgressIndicator()),
                ),
              ),
              error: (e, _) => Card(
                child: SizedBox(
                  height: 170,
                  child: ErrorState(
                    message: e.toString(),
                    onRetry: () => ref.invalidate(todayAttendanceProvider),
                  ),
                ),
              ),
              data: (day) => _PunchCard(
                day: day,
                busy: _busy,
                onPunchIn: () => _punch(punchIn: true),
                onPunchOut: () => _punch(punchIn: false),
              ),
            ),
            const SizedBox(height: 24),
            Text(
              'This month',
              style: Theme.of(context).textTheme.titleSmall?.copyWith(
                fontWeight: FontWeight.w700,
                letterSpacing: 0.3,
              ),
            ),
            const SizedBox(height: 12),
            month.when(
              loading: () => const SizedBox(
                height: 200,
                child: LoadingList(itemCount: 3, itemHeight: 56),
              ),
              error: (e, _) => SizedBox(
                height: 200,
                child: ErrorState(
                  message: e.toString(),
                  onRetry: () => ref.invalidate(monthAttendanceProvider),
                ),
              ),
              data: (days) => days.isEmpty
                  ? const SizedBox(
                      height: 200,
                      child: EmptyState(
                        icon: Icons.event_busy_rounded,
                        title: 'No attendance yet this month',
                        description: 'Days appear here once you punch in.',
                      ),
                    )
                  : Card(
                      child: Column(
                        children: [
                          for (var i = 0; i < days.length; i++) ...[
                            if (i > 0) const Divider(height: 1),
                            ListTile(
                              dense: true,
                              leading: CircleAvatar(
                                radius: 18,
                                backgroundColor: scheme.primary.withValues(
                                  alpha: 0.12,
                                ),
                                child: Text(
                                  DateFormat('d').format(days[i].workDate),
                                  style: TextStyle(
                                    color: scheme.primary,
                                    fontWeight: FontWeight.w700,
                                    fontSize: 13,
                                  ),
                                ),
                              ),
                              title: Text(
                                DateFormat(
                                  'EEE, d MMM',
                                ).format(days[i].workDate),
                              ),
                              subtitle: Text(
                                days[i].punchInAt == null
                                    ? 'No punch'
                                    : '${DateFormat('h:mm a').format(days[i].punchInAt!)}'
                                          '${days[i].punchOutAt != null ? ' – ${DateFormat('h:mm a').format(days[i].punchOutAt!)}' : ''}',
                              ),
                              trailing: Text(
                                days[i].workedLabel,
                                style: const TextStyle(
                                  fontWeight: FontWeight.w600,
                                  fontFeatures: [FontFeature.tabularFigures()],
                                ),
                              ),
                            ),
                          ],
                        ],
                      ),
                    ),
            ),
            const SizedBox(height: 24),
          ],
        ),
      ),
    );
  }
}

class _PunchCard extends StatelessWidget {
  const _PunchCard({
    required this.day,
    required this.busy,
    required this.onPunchIn,
    required this.onPunchOut,
  });

  final AttendanceDay? day;
  final bool busy;
  final VoidCallback onPunchIn;
  final VoidCallback onPunchOut;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final punchedIn = day?.isPunchedIn ?? false;
    final done = day?.isComplete ?? false;

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          children: [
            Text(
              DateFormat('h:mm a').format(DateTime.now()),
              style: Theme.of(context).textTheme.displaySmall?.copyWith(
                fontWeight: FontWeight.w300,
                fontFeatures: const [FontFeature.tabularFigures()],
              ),
            ),
            const SizedBox(height: 4),
            Text(
              done
                  ? 'Done for today'
                  : punchedIn
                  ? 'Punched in at ${DateFormat('h:mm a').format(day!.punchInAt!)}'
                  : 'Not punched in',
              style: Theme.of(
                context,
              ).textTheme.bodyMedium?.copyWith(color: scheme.onSurfaceVariant),
            ),
            if (day?.lateMinutes != null && day!.lateMinutes! > 0) ...[
              const SizedBox(height: 6),
              Text(
                '${day!.lateMinutes} minutes late',
                style: TextStyle(
                  color: AppTheme.warning(context),
                  fontSize: 12,
                ),
              ),
            ],
            const SizedBox(height: 20),
            if (done)
              Text(
                'Worked ${day!.workedLabel}',
                style: Theme.of(
                  context,
                ).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w600),
              )
            else
              FilledButton.icon(
                onPressed: busy ? null : (punchedIn ? onPunchOut : onPunchIn),
                style: punchedIn
                    ? FilledButton.styleFrom(
                        backgroundColor: AppTheme.danger(context),
                      )
                    : null,
                icon: busy
                    ? const SizedBox(
                        height: 18,
                        width: 18,
                        child: CircularProgressIndicator(strokeWidth: 2.2),
                      )
                    : Icon(
                        punchedIn ? Icons.logout_rounded : Icons.login_rounded,
                      ),
                label: Text(punchedIn ? 'Punch out' : 'Punch in'),
              ),
          ],
        ),
      ),
    );
  }
}
