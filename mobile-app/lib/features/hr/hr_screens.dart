import 'dart:io';

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';
import 'package:open_filex/open_filex.dart';
import 'package:path_provider/path_provider.dart';

import '../../core/config/app_config.dart';
import '../../models/leave.dart';
import '../../providers/app_providers.dart';
import '../../widgets/states.dart';
import '../../providers/cache.dart';

/// The screens HR and team leads reach that an employee does not.
///
/// Gathered in one file because they are the same shape — a list read from one
/// endpoint — and four files of near-identical scaffolding is harder to keep
/// consistent than one. They are separated by permission, not by nature.

// ---------------------------------------------------------------------------
// Leave policies
// ---------------------------------------------------------------------------

final leaveTypesProvider = FutureProvider.autoDispose<List<LeaveType>>(
  (ref) {
  cacheFor(ref, cacheLong);
  return ref.watch(workRepositoryProvider).allLeaveTypes();
});

final holidaysProvider =
    FutureProvider.autoDispose<List<Map<String, dynamic>>>(
  (ref) => ref.watch(workRepositoryProvider).holidays(DateTime.now().year),
);

/// What the company allows, and when it closes.
///
/// Read-only. The web page can add a holiday and change an allowance; both
/// change what every employee is entitled to, and a mis-tap on a phone is not
/// the way to do that. The list is what HR needs when somebody asks "how many
/// casual days do I get" — which is the question that actually arrives by
/// message.
class LeavePoliciesScreen extends ConsumerWidget {
  const LeavePoliciesScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final types = ref.watch(leaveTypesProvider);
    final holidays = ref.watch(holidaysProvider);
    final scheme = Theme.of(context).colorScheme;

    return Scaffold(
      appBar: AppBar(title: const Text('Leave policies')),
      body: RefreshIndicator(
        onRefresh: () async {
          ref
            ..invalidate(leaveTypesProvider)
            ..invalidate(holidaysProvider);
        },
        child: ListView(
          physics: const AlwaysScrollableScrollPhysics(),
          padding: const EdgeInsets.all(16),
          children: [
            const _SectionTitle('Leave types'),
            types.when(
              // Bounded: LoadingList is a ListView and this sits inside one.
              // A scrollable given unbounded height throws during layout, which
              // is a crash on every visit rather than a cosmetic problem.
              loading: () => const SizedBox(height: 160, child: LoadingList(itemCount: 3)),
              error: (e, _) => ErrorState(
                message: '$e',
                onRetry: () => ref.invalidate(leaveTypesProvider),
              ),
              data: (list) => list.isEmpty
                  ? const _Empty('No leave types configured.')
                  : Card(
                      child: Column(
                        children: [
                          for (var i = 0; i < list.length; i++) ...[
                            if (i > 0) const Divider(height: 1),
                            ListTile(
                              title: Text(
                                list[i].name,
                                style: const TextStyle(fontWeight: FontWeight.w600),
                              ),
                              trailing: Text(
                                '${list[i].maxDaysPerYear ?? '—'} days',
                                style: TextStyle(
                                  color: scheme.primary,
                                  fontWeight: FontWeight.w700,
                                ),
                              ),
                            ),
                          ],
                        ],
                      ),
                    ),
            ),
            const SizedBox(height: 24),
            _SectionTitle('Holidays ${DateTime.now().year}'),
            holidays.when(
              loading: () => const SizedBox(height: 160, child: LoadingList(itemCount: 3)),
              error: (e, _) => ErrorState(
                message: '$e',
                onRetry: () => ref.invalidate(holidaysProvider),
              ),
              data: (list) => list.isEmpty
                  ? const _Empty('No holidays set for this year.')
                  : Card(
                      child: Column(
                        children: [
                          for (var i = 0; i < list.length; i++) ...[
                            if (i > 0) const Divider(height: 1),
                            ListTile(
                              dense: true,
                              title: Text(list[i]['name']?.toString() ?? 'Holiday'),
                              trailing: Text(
                                _shortDate(list[i]['holidayDate']),
                                style: TextStyle(color: scheme.onSurfaceVariant),
                              ),
                            ),
                          ],
                        ],
                      ),
                    ),
            ),
            const SizedBox(height: 12),
            Text(
              'Read-only here. Adding a holiday or changing an allowance affects '
              'everybody, so it is done on the web portal.',
              style: Theme.of(context)
                  .textTheme
                  .bodySmall
                  ?.copyWith(color: scheme.onSurfaceVariant),
            ),
          ],
        ),
      ),
    );
  }

  static String _shortDate(dynamic raw) {
    final d = DateTime.tryParse(raw?.toString() ?? '');
    return d == null ? '—' : DateFormat('EEE, d MMM').format(d);
  }
}

// ---------------------------------------------------------------------------
// Payroll
// ---------------------------------------------------------------------------

final salariesProvider =
    FutureProvider.autoDispose<List<Map<String, dynamic>>>(
  (ref) => ref.watch(workRepositoryProvider).salaries(),
);

/// Standing salaries, and the months payroll has been run for.
///
/// Also read-only, and for a stronger reason than the policies above: running
/// payroll writes money. That belongs on a screen somebody sits down at, not on
/// a phone in a corridor.
class PayrollScreen extends ConsumerWidget {
  const PayrollScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(salariesProvider);
    final money = NumberFormat.currency(locale: 'en_IN', symbol: '₹', decimalDigits: 0);

    return Scaffold(
      appBar: AppBar(title: const Text('Payroll')),
      body: RefreshIndicator(
        onRefresh: () async => ref.invalidate(salariesProvider),
        child: async.when(
          loading: () => const LoadingList(),
          error: (e, _) => ListView(
            physics: const AlwaysScrollableScrollPhysics(),
            children: [
              const SizedBox(height: 80),
              ErrorState(message: '$e', onRetry: () => ref.invalidate(salariesProvider)),
            ],
          ),
          data: (rows) {
            if (rows.isEmpty) {
              return ListView(
                physics: const AlwaysScrollableScrollPhysics(),
                children: const [
                  SizedBox(height: 80),
                  EmptyState(
                    icon: Icons.payments_outlined,
                    title: 'No salaries set',
                    description: 'Salary records appear here once they are entered.',
                  ),
                ],
              );
            }
            return ListView.separated(
              physics: const AlwaysScrollableScrollPhysics(),
              padding: const EdgeInsets.symmetric(vertical: 8),
              itemCount: rows.length,
              separatorBuilder: (_, __) => const Divider(height: 1, indent: 16),
              itemBuilder: (context, i) {
                final r = rows[i];
                final basic = num.tryParse('${r['basicSalary'] ?? r['basic'] ?? ''}');
                return ListTile(
                  title: Text(
                    r['employeeName']?.toString() ?? r['name']?.toString() ?? 'Employee',
                    style: const TextStyle(fontWeight: FontWeight.w600),
                  ),
                  subtitle: r['employeeCode'] == null
                      ? null
                      : Text(r['employeeCode'].toString()),
                  trailing: Text(
                    basic == null ? '—' : money.format(basic),
                    style: const TextStyle(
                      fontWeight: FontWeight.w700,
                      fontFeatures: [FontFeature.tabularFigures()],
                    ),
                  ),
                );
              },
            );
          },
        ),
      ),
    );
  }
}

// ---------------------------------------------------------------------------
// Onboarding
// ---------------------------------------------------------------------------

final onboardingIdsProvider = FutureProvider.autoDispose<List<int>>(
  (ref) => ref.watch(workRepositoryProvider).onboardingEmployeeIds(),
);

/// Who is still being onboarded, and what is left on their checklist.
class OnboardingScreen extends ConsumerWidget {
  const OnboardingScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(onboardingIdsProvider);

    return Scaffold(
      appBar: AppBar(title: const Text('Onboarding')),
      body: RefreshIndicator(
        onRefresh: () async => ref.invalidate(onboardingIdsProvider),
        child: async.when(
          loading: () => const LoadingList(),
          error: (e, _) => ListView(
            physics: const AlwaysScrollableScrollPhysics(),
            children: [
              const SizedBox(height: 80),
              ErrorState(
                message: '$e',
                onRetry: () => ref.invalidate(onboardingIdsProvider),
              ),
            ],
          ),
          data: (ids) => ids.isEmpty
              ? ListView(
                  physics: const AlwaysScrollableScrollPhysics(),
                  children: const [
                    SizedBox(height: 80),
                    EmptyState(
                      icon: Icons.how_to_reg_outlined,
                      title: 'Nobody is onboarding',
                      description: 'New joiners with an open checklist appear here.',
                    ),
                  ],
                )
              : ListView.builder(
                  physics: const AlwaysScrollableScrollPhysics(),
                  padding: const EdgeInsets.all(16),
                  itemCount: ids.length,
                  itemBuilder: (context, i) => _OnboardingCard(userId: ids[i]),
                ),
        ),
      ),
    );
  }
}

class _OnboardingCard extends ConsumerWidget {
  const _OnboardingCard({required this.userId});
  final int userId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final tasks = ref.watch(
      FutureProvider.autoDispose(
        (r) => r.watch(workRepositoryProvider).onboardingTasks(userId),
      ),
    );

    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: Card(
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: tasks.when(
            loading: () => const SizedBox(
              height: 40,
              child: Center(child: CircularProgressIndicator(strokeWidth: 2)),
            ),
            error: (e, _) => Text('$e'),
            data: (list) {
              final done = list.where((t) => t['completed'] == true).length;
              final name = list.isEmpty
                  ? 'Employee #$userId'
                  : (list.first['employeeName']?.toString() ?? 'Employee #$userId');
              return Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Expanded(
                        child: Text(
                          name,
                          style: const TextStyle(fontWeight: FontWeight.w700),
                        ),
                      ),
                      Text('$done of ${list.length}'),
                    ],
                  ),
                  const SizedBox(height: 8),
                  LinearProgressIndicator(
                    value: list.isEmpty ? 0 : done / list.length,
                    minHeight: 6,
                    borderRadius: BorderRadius.circular(3),
                  ),
                  const SizedBox(height: 10),
                  for (final t in list)
                    Padding(
                      padding: const EdgeInsets.only(bottom: 4),
                      child: Row(
                        children: [
                          Icon(
                            t['completed'] == true
                                ? Icons.check_circle_rounded
                                : Icons.radio_button_unchecked_rounded,
                            size: 16,
                            color: t['completed'] == true
                                ? Colors.green
                                : Theme.of(context).colorScheme.outline,
                          ),
                          const SizedBox(width: 8),
                          Expanded(
                            child: Text(
                              t['title']?.toString() ?? t['name']?.toString() ?? 'Task',
                              style: const TextStyle(fontSize: 13),
                            ),
                          ),
                        ],
                      ),
                    ),
                ],
              );
            },
          ),
        ),
      ),
    );
  }
}

// ---------------------------------------------------------------------------
// Reports
// ---------------------------------------------------------------------------

/// Downloading a report and handing it to a spreadsheet app.
///
/// The endpoints answer with .xlsx bytes, not with data — there is nothing to
/// render, and reimplementing a spreadsheet to avoid a download would be a worse
/// answer than the download. So this saves the file and asks Android to open it
/// with whatever the phone uses for spreadsheets.
class ReportsScreen extends ConsumerStatefulWidget {
  const ReportsScreen({super.key});

  @override
  ConsumerState<ReportsScreen> createState() => _ReportsScreenState();
}

class _ReportsScreenState extends ConsumerState<ReportsScreen> {
  DateTimeRange _range = DateTimeRange(
    start: DateTime(DateTime.now().year, DateTime.now().month, 1),
    end: DateTime.now(),
  );
  String? _busy;

  Future<void> _download(String kind, String label) async {
    if (_busy != null) return;
    setState(() => _busy = kind);

    final fmt = DateFormat('yyyy-MM-dd');
    try {
      final dio = ref.read(apiClientProvider).raw;
      final query = kind == 'payroll'
          ? {'month': _range.end.month, 'year': _range.end.year}
          : {'from': fmt.format(_range.start), 'to': fmt.format(_range.end)};

      final res = await dio.get<List<int>>(
        '${AppConfig.apiBaseUrl}/reports/$kind',
        queryParameters: query,
        options: Options(responseType: ResponseType.bytes),
      );

      final dir = await getTemporaryDirectory();
      final file = File('${dir.path}/${kind}_report_${DateTime.now().millisecondsSinceEpoch}.xlsx');
      await file.writeAsBytes(res.data ?? const []);

      final opened = await OpenFilex.open(file.path);
      if (!mounted) return;

      if (opened.type != ResultType.done) {
        // Saved but nothing on the phone can open it. Saying where it went is
        // more use than "could not open file".
        ScaffoldMessenger.of(context)
          ..hideCurrentSnackBar()
          ..showSnackBar(
            SnackBar(
              content: Text(
                '$label saved, but no app here opens spreadsheets. '
                'Install Excel or Google Sheets to read it.',
              ),
              duration: const Duration(seconds: 6),
            ),
          );
      }
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context)
        ..hideCurrentSnackBar()
        ..showSnackBar(SnackBar(content: Text('$e')));
    } finally {
      if (mounted) setState(() => _busy = null);
    }
  }

  @override
  Widget build(BuildContext context) {
    final fmt = DateFormat('d MMM');
    final scheme = Theme.of(context).colorScheme;

    return Scaffold(
      appBar: AppBar(title: const Text('Reports')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Card(
            child: ListTile(
              leading: const Icon(Icons.date_range_rounded),
              title: const Text('Period'),
              subtitle: Text(
                '${fmt.format(_range.start)} – ${fmt.format(_range.end)}',
              ),
              trailing: const Icon(Icons.edit_calendar_outlined),
              onTap: () async {
                final picked = await showDateRangePicker(
                  context: context,
                  initialDateRange: _range,
                  firstDate: DateTime(DateTime.now().year - 2),
                  lastDate: DateTime.now(),
                );
                if (picked != null) setState(() => _range = picked);
              },
            ),
          ),
          const SizedBox(height: 16),
          const _SectionTitle('Download'),
          for (final r in const [
            ('attendance', 'Attendance report', Icons.access_time_rounded),
            ('leave', 'Leave report', Icons.event_note_rounded),
            ('payroll', 'Payroll report', Icons.payments_outlined),
          ])
            Padding(
              padding: const EdgeInsets.only(bottom: 10),
              child: Card(
                child: ListTile(
                  leading: Icon(r.$3, color: scheme.primary),
                  title: Text(r.$2),
                  subtitle: Text(
                    r.$1 == 'payroll'
                        // Payroll is billed by month, so a range would be a
                        // control that quietly does not apply.
                        ? 'For ${DateFormat('MMMM yyyy').format(_range.end)}'
                        : 'Excel file (.xlsx)',
                  ),
                  trailing: _busy == r.$1
                      ? const SizedBox(
                          height: 20,
                          width: 20,
                          child: CircularProgressIndicator(strokeWidth: 2.2),
                        )
                      : const Icon(Icons.download_rounded),
                  onTap: () => _download(r.$1, r.$2),
                ),
              ),
            ),
          const SizedBox(height: 8),
          Text(
            'Reports are spreadsheets. They open in Excel or Google Sheets once '
            'downloaded.',
            style: Theme.of(context)
                .textTheme
                .bodySmall
                ?.copyWith(color: scheme.onSurfaceVariant),
          ),
        ],
      ),
    );
  }
}

// ---------------------------------------------------------------------------

class _SectionTitle extends StatelessWidget {
  const _SectionTitle(this.text);
  final String text;

  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.only(bottom: 10),
        child: Text(
          text,
          style: Theme.of(context)
              .textTheme
              .titleSmall
              ?.copyWith(fontWeight: FontWeight.w700, letterSpacing: 0.3),
        ),
      );
}

class _Empty extends StatelessWidget {
  const _Empty(this.text);
  final String text;

  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.symmetric(vertical: 20),
        child: Center(
          child: Text(
            text,
            style: TextStyle(color: Theme.of(context).colorScheme.onSurfaceVariant),
          ),
        ),
      );
}
