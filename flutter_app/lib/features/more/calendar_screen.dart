import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import '../../models/work_items.dart';
import '../../providers/app_providers.dart';
import '../../widgets/states.dart';

/// The month being shown, as its first day. Kept outside the widget so moving
/// between months does not throw away what the previous month already loaded.
final calendarMonthProvider = StateProvider<DateTime>((ref) {
  final now = DateTime.now();
  return DateTime(now.year, now.month);
});

/// Events for [calendarMonthProvider], as a list ordered by date.
///
/// The whole month is fetched in one request rather than a request per day.
/// A month has thirty-odd days and the hosting account has twenty database
/// connections; thirty requests to render one screen would be a poor trade.
final calendarEventsProvider = FutureProvider.autoDispose<List<CalendarEvent>>((
  ref,
) async {
  final month = ref.watch(calendarMonthProvider);
  // Day 0 of the following month is the last day of this one, which avoids
  // hand-writing the length of February.
  final last = DateTime(month.year, month.month + 1, 0);
  final events = await ref
      .watch(workRepositoryProvider)
      .calendarEvents(from: month, to: last);

  final sorted = [...events]..sort((a, b) {
    final da = a.date, db = b.date;
    if (da == null && db == null) return 0;
    if (da == null) return 1; // undated entries sink to the bottom
    if (db == null) return -1;
    return da.compareTo(db);
  });
  return sorted;
});

class CalendarScreen extends ConsumerWidget {
  const CalendarScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final month = ref.watch(calendarMonthProvider);
    final async = ref.watch(calendarEventsProvider);

    return Scaffold(
      appBar: AppBar(title: const Text('Calendar')),
      body: Column(
        children: [
          _MonthBar(
            month: month,
            onShift: (delta) => ref
                .read(calendarMonthProvider.notifier)
                .update((m) => DateTime(m.year, m.month + delta)),
          ),
          const Divider(height: 1),
          Expanded(
            child: RefreshIndicator(
              onRefresh: () async => ref.invalidate(calendarEventsProvider),
              child: async.when(
                loading: () => const LoadingList(),
                error: (e, _) => ListView(
                  physics: const AlwaysScrollableScrollPhysics(),
                  children: [
                    const SizedBox(height: 80),
                    ErrorState(
                      message: e.toString(),
                      onRetry: () => ref.invalidate(calendarEventsProvider),
                    ),
                  ],
                ),
                data: (events) {
                  if (events.isEmpty) {
                    return ListView(
                      physics: const AlwaysScrollableScrollPhysics(),
                      children: const [
                        SizedBox(height: 80),
                        EmptyState(
                          icon: Icons.event_outlined,
                          title: 'Nothing this month',
                          description:
                              'Holidays, birthdays and meetings appear here.',
                        ),
                      ],
                    );
                  }
                  return ListView.builder(
                    physics: const AlwaysScrollableScrollPhysics(),
                    padding: const EdgeInsets.fromLTRB(16, 12, 16, 24),
                    itemCount: events.length,
                    itemBuilder: (context, i) => _EventCard(events[i]),
                  );
                },
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _MonthBar extends StatelessWidget {
  const _MonthBar({required this.month, required this.onShift});

  final DateTime month;
  final void Function(int delta) onShift;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          IconButton(
            onPressed: () => onShift(-1),
            icon: const Icon(Icons.chevron_left_rounded),
            tooltip: 'Previous month',
          ),
          Text(
            DateFormat('MMMM yyyy').format(month),
            style: Theme.of(
              context,
            ).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w700),
          ),
          IconButton(
            onPressed: () => onShift(1),
            icon: const Icon(Icons.chevron_right_rounded),
            tooltip: 'Next month',
          ),
        ],
      ),
    );
  }
}

class _EventCard extends StatelessWidget {
  const _EventCard(this.event);

  final CalendarEvent event;

  /// A birthday and a fire drill should not look alike at a glance.
  static const _icons = <String, IconData>{
    'BIRTHDAY': Icons.cake_outlined,
    'ANNIVERSARY': Icons.workspace_premium_outlined,
    'CELEBRATION': Icons.celebration_outlined,
    'MEETING': Icons.groups_outlined,
    'TRAINING': Icons.school_outlined,
    'HOLIDAY': Icons.beach_access_outlined,
  };

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final when = <String>[
      if (event.date != null) DateFormat('EEE d MMM').format(event.date!),
      if (event.endDate != null && event.endDate != event.date)
        '→ ${DateFormat('d MMM').format(event.endDate!)}',
      if (event.startTime != null && event.startTime!.isNotEmpty)
        [
          event.startTime,
          if (event.endTime != null && event.endTime!.isNotEmpty)
            event.endTime,
        ].join('–'),
      if (event.location != null && event.location!.isNotEmpty)
        event.location!,
    ].join(' · ');

    return Card(
      margin: const EdgeInsets.only(bottom: 10),
      child: ListTile(
        leading: CircleAvatar(
          backgroundColor: theme.colorScheme.secondaryContainer,
          foregroundColor: theme.colorScheme.onSecondaryContainer,
          child: Icon(_icons[event.type] ?? Icons.event_outlined, size: 20),
        ),
        title: Text(
          event.title,
          style: const TextStyle(fontWeight: FontWeight.w600),
        ),
        subtitle: Text(
          [
            if (when.isNotEmpty) when,
            if (event.description != null && event.description!.isNotEmpty)
              event.description!,
          ].join('\n'),
        ),
        isThreeLine:
            event.description != null && event.description!.isNotEmpty,
        // Null means the whole company, which needs no label — a team name is
        // only worth the space when it narrows things down.
        trailing: (event.audienceTeam?.isNotEmpty ?? false)
            ? Chip(
                label: Text(
                  event.audienceTeam!,
                  style: theme.textTheme.labelSmall,
                ),
                visualDensity: VisualDensity.compact,
              )
            : null,
      ),
    );
  }
}
