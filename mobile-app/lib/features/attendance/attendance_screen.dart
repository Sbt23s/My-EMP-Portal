import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import '../../models/attendance.dart';
import 'face_punch_sheet.dart';
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

  /// Punching with a selfie, checked against the enrolled photo.
  ///
  /// Offered alongside the ordinary punch rather than replacing it: whether a
  /// company requires a verified face is its own setting, and the server is what
  /// enforces it. A phone that only offered one of the two would be deciding
  /// that on the company's behalf.
  Future<void> _facePunch({required bool punchIn}) async {
    final done = await showModalBottomSheet<bool>(
      context: context,
      isScrollControlled: true,
      useSafeArea: true,
      builder: (_) => FacePunchSheet(punchIn: punchIn),
    );
    if (done == true && mounted) {
      ref
        ..invalidate(todayAttendanceProvider)
        ..invalidate(monthAttendanceProvider);
      ScaffoldMessenger.of(context)
        ..hideCurrentSnackBar()
        ..showSnackBar(
          SnackBar(content: Text(punchIn ? 'Punched in' : 'Punched out')),
        );
    }
  }

  Future<void> _punch({required bool punchIn}) async {
    if (_busy) return;
    setState(() => _busy = true);
    try {
      final repo = ref.read(workRepositoryProvider);

      /*
       * Where they are, if it can be had.
       *
       * Read before the punch and never allowed to stop it. The server records
       * a punch with no coordinates as a geofence exception rather than
       * refusing it, and somebody standing at the gate at nine o'clock must be
       * able to mark their attendance whether or not the GPS cooperates.
       *
       * This is the fix for the app sending no coordinates at all — which meant
       * every punch from a phone was filed as an exception, and a field
       * employee could punch in from anywhere.
       */
      final where = await ref.read(punchLocationServiceProvider).current();

      punchIn
          ? await repo.punchIn(
              latitude: where.latitude,
              longitude: where.longitude,
            )
          : await repo.punchOut(
              latitude: where.latitude,
              longitude: where.longitude,
            );
      if (!mounted) return;
      ref
        ..invalidate(todayAttendanceProvider)
        ..invalidate(monthAttendanceProvider);

      final warning = where.warning;
      ScaffoldMessenger.of(context)
        ..hideCurrentSnackBar()
        ..showSnackBar(
          SnackBar(
            // The punch succeeded either way, so the confirmation comes first
            // and the caveat second. Leading with the problem would read as a
            // failure and send somebody to punch again.
            content: Text(
              warning == null
                  ? (punchIn ? 'Punched in' : 'Punched out')
                  : '${punchIn ? 'Punched in' : 'Punched out'}. $warning',
            ),
            duration: warning == null
                ? const Duration(seconds: 3)
                // Long enough to read a sentence that asks them to do something.
                : const Duration(seconds: 7),
          ),
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
                onFacePunch: () => _facePunch(punchIn: !(today.value?.isPunchedIn ?? false)),
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
    required this.onFacePunch,
    required this.onPunchOut,
  });

  final AttendanceDay? day;
  final bool busy;
  final VoidCallback onPunchIn;

  /// Punch with a selfie, checked against the enrolled photo.
  final VoidCallback onFacePunch;
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
            if (!done) ...[
              const SizedBox(height: 10),
              // Secondary, not primary. Most punches are the ordinary kind; the
              // face check is what a company turns on when it needs proof, and
              // making it the louder of the two buttons would suggest the plain
              // one is the lesser option when the server treats them equally.
              OutlinedButton.icon(
                onPressed: busy ? null : onFacePunch,
                icon: const Icon(Icons.face_retouching_natural_rounded, size: 18),
                label: Text(
                  punchedIn ? 'Punch out with face' : 'Punch in with face',
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }
}
