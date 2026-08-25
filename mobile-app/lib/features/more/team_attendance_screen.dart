import 'package:flutter/material.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import '../../models/directory_person.dart';
import '../../providers/app_providers.dart';
import '../../themes/app_theme.dart';
import '../../widgets/states.dart';
import '../../widgets/date_field.dart';

/// Rows for one day. Keyed by the date so switching back to yesterday is
/// instant rather than a second request for something already fetched.
final teamAttendanceProvider = FutureProvider.autoDispose
    .family<List<TeamAttendanceRow>, DateTime>(
  (ref, date) => ref.watch(workRepositoryProvider).teamAttendance(date),
);

/// Who was in, on a given day.
///
/// The portal's Team Attendance page. A team lead sees their team and HR sees
/// the company — decided by the server, not here, so the two products cannot
/// disagree about who somebody is allowed to see.
class TeamAttendanceScreen extends ConsumerStatefulWidget {
  const TeamAttendanceScreen({super.key});

  @override
  ConsumerState<TeamAttendanceScreen> createState() =>
      _TeamAttendanceScreenState();
}

class _TeamAttendanceScreenState extends ConsumerState<TeamAttendanceScreen> {
  late DateTime _date = _todayOnly();

  static DateTime _todayOnly() {
    final n = DateTime.now();
    return DateTime(n.year, n.month, n.day);
  }

  @override
  Widget build(BuildContext context) {
    final async = ref.watch(teamAttendanceProvider(_date));
    final isToday = _date == _todayOnly();

    return Scaffold(
      appBar: AppBar(title: const Text('Team attendance')),
      body: Column(
        children: [
          _DateBar(
            date: _date,
            isToday: isToday,
            onPrevious: () => setState(
              () => _date = _date.subtract(const Duration(days: 1)),
            ),
            // Disabled on today rather than hidden: a control that vanishes at
            // the edge of a range is one people press twice looking for.
            onNext: isToday
                ? null
                : () => setState(() => _date = _date.add(const Duration(days: 1))),
            onPick: _pickDate,
          ),
          Expanded(
            child: RefreshIndicator(
              onRefresh: () async =>
                  ref.invalidate(teamAttendanceProvider(_date)),
              child: async.when(
                loading: () => const LoadingList(),
                error: (e, _) => ListView(
                  physics: const AlwaysScrollableScrollPhysics(),
                  children: [
                    const SizedBox(height: 60),
                    ErrorState(
                      message: '$e',
                      onRetry: () =>
                          ref.invalidate(teamAttendanceProvider(_date)),
                    ),
                  ],
                ),
                data: (rows) {
                  if (rows.isEmpty) {
                    return ListView(
                      physics: const AlwaysScrollableScrollPhysics(),
                      children: const [
                        SizedBox(height: 60),
                        EmptyState(
                          icon: Icons.groups_outlined,
                          title: 'Nothing recorded',
                          description:
                              'No attendance was recorded for anyone on this day.',
                        ),
                      ],
                    );
                  }

                  final present = rows.where((r) => r.punchedIn).length;
                  final late = rows.where((r) => r.isLate).length;

                  return ListView(
                    physics: const AlwaysScrollableScrollPhysics(),
                    padding: const EdgeInsets.fromLTRB(16, 4, 16, 24),
                    children: [
                      _Summary(total: rows.length, present: present, late: late),
                      const SizedBox(height: 14),
                      for (var i = 0; i < rows.length; i++)
                        Padding(
                          padding: const EdgeInsets.only(bottom: 8),
                          child: i > 8
                              ? _Row(row: rows[i])
                              : _Row(row: rows[i])
                                  .animate()
                                  .fadeIn(delay: (i * 35).ms, duration: 200.ms)
                                  .slideY(
                                    begin: 0.06,
                                    end: 0,
                                    curve: Curves.easeOutCubic,
                                  ),
                        ),
                    ],
                  );
                },
              ),
            ),
          ),
        ],
      ),
    );
  }

  Future<void> _pickDate() async {
    final today = _todayOnly();
    final picked = await pickDate(
      context,
      initialDate: _date,
      firstDate: today.subtract(const Duration(days: 365)),
      lastDate: today,
    );
    if (picked != null && mounted) {
      setState(() => _date = DateTime(picked.year, picked.month, picked.day));
    }
  }
}

class _DateBar extends StatelessWidget {
  const _DateBar({
    required this.date,
    required this.isToday,
    required this.onPrevious,
    required this.onNext,
    required this.onPick,
  });

  final DateTime date;
  final bool isToday;
  final VoidCallback onPrevious;
  final VoidCallback? onNext;
  final VoidCallback onPick;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(8, 8, 8, 4),
      child: Row(
        children: [
          IconButton(
            onPressed: onPrevious,
            icon: const Icon(Icons.chevron_left_rounded),
            tooltip: 'Previous day',
          ),
          Expanded(
            child: TextButton(
              onPressed: onPick,
              child: Text(
                isToday ? 'Today' : DateFormat('EEE, d MMM yyyy').format(date),
                style: const TextStyle(fontWeight: FontWeight.w700),
              ),
            ),
          ),
          IconButton(
            onPressed: onNext,
            icon: const Icon(Icons.chevron_right_rounded),
            tooltip: 'Next day',
          ),
        ],
      ),
    );
  }
}

class _Summary extends StatelessWidget {
  const _Summary({
    required this.total,
    required this.present,
    required this.late,
  });

  final int total;
  final int present;
  final int late;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Card(
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 14, horizontal: 8),
        child: Row(
          children: [
            _Stat(label: 'Recorded', value: '$total', tint: scheme.primary),
            _Stat(
              label: 'Punched in',
              value: '$present',
              tint: AppTheme.success(context),
            ),
            _Stat(
              label: 'Late',
              value: '$late',
              tint: late > 0 ? AppTheme.warning(context) : scheme.outline,
            ),
          ],
        ),
      ),
    );
  }
}

class _Stat extends StatelessWidget {
  const _Stat({required this.label, required this.value, required this.tint});

  final String label;
  final String value;
  final Color tint;

  @override
  Widget build(BuildContext context) {
    return Expanded(
      child: Column(
        children: [
          Text(
            value,
            style: Theme.of(context).textTheme.titleLarge?.copyWith(
                  fontWeight: FontWeight.w800,
                  color: tint,
                  fontFeatures: const [FontFeature.tabularFigures()],
                ),
          ),
          const SizedBox(height: 2),
          Text(
            label,
            style: Theme.of(context).textTheme.labelSmall?.copyWith(
                  color: Theme.of(context).colorScheme.onSurfaceVariant,
                ),
          ),
        ],
      ),
    );
  }
}

class _Row extends StatelessWidget {
  const _Row({required this.row});

  final TeamAttendanceRow row;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final time = DateFormat('h:mm a');

    return Card(
      child: ListTile(
        leading: Container(
          height: 38,
          width: 38,
          alignment: Alignment.center,
          decoration: BoxDecoration(
            color: (row.punchedIn ? AppTheme.success(context) : scheme.outline)
                .withValues(alpha: 0.14),
            shape: BoxShape.circle,
          ),
          child: Icon(
            row.punchedIn
                ? Icons.check_rounded
                : Icons.remove_rounded,
            size: 18,
            color: row.punchedIn ? AppTheme.success(context) : scheme.outline,
          ),
        ),
        title: Text(
          // "Unnamed" was what every row said, because this endpoint returns a
          // userId and no person. The name is joined on in the repository; the
          // employee code is the fallback, and both being absent is now rare
          // enough to be worth showing plainly rather than hiding.
          row.employeeName ?? row.employeeCode ?? 'Employee ${row.userId}',
          style: const TextStyle(fontWeight: FontWeight.w600),
        ),
        subtitle: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              row.punchedIn
                  ? '${time.format(row.punchInAt!)}'
                      '${row.punchOutAt != null ? ' – ${time.format(row.punchOutAt!)}' : ''}'
                  : 'No punch recorded',
            ),
            /*
              Where the punch happened, for the punches where that is a
              question. An office punch is not geofenced, so a location on it
              says nothing; a field punch outside its site is the thing
              somebody actually wants to see, so it is called out rather than
              left as two numbers to interpret.
            */
            if (row.punchedIn && (row.mode != null || row.hasLocation))
              Padding(
                padding: const EdgeInsets.only(top: 2),
                child: Row(
                  children: [
                    Icon(
                      row.hasLocation
                          ? Icons.place_outlined
                          : Icons.business_outlined,
                      size: 13,
                      color: row.withinGeofence == false
                          ? AppTheme.warning(context)
                          : scheme.outline,
                    ),
                    const SizedBox(width: 3),
                    Expanded(
                      child: Text(
                        [
                          if (row.mode != null) row.mode!,
                          if (row.withinGeofence == false) 'outside site',
                          if (row.hasLocation)
                            '${row.inLatitude!.toStringAsFixed(4)}, '
                                '${row.inLongitude!.toStringAsFixed(4)}',
                        ].join(' · '),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: TextStyle(
                          fontSize: 11,
                          color: row.withinGeofence == false
                              ? AppTheme.warning(context)
                              : scheme.outline,
                        ),
                      ),
                    ),
                  ],
                ),
              ),
          ],
        ),
        trailing: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          crossAxisAlignment: CrossAxisAlignment.end,
          children: [
            Text(
              row.workedLabel,
              style: const TextStyle(
                fontWeight: FontWeight.w700,
                fontFeatures: [FontFeature.tabularFigures()],
              ),
            ),
            if (row.isLate)
              Text(
                '${row.lateMinutes}m late',
                style: Theme.of(context).textTheme.labelSmall?.copyWith(
                      color: AppTheme.warning(context),
                      fontWeight: FontWeight.w600,
                    ),
              ),
          ],
        ),
      ),
    );
  }
}
