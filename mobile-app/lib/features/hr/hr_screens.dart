import 'dart:io';

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';
import 'package:open_filex/open_filex.dart';
import 'package:path_provider/path_provider.dart';

import '../../widgets/ui_kit.dart';
import '../../core/config/app_config.dart';
import '../../models/leave.dart';
import '../../providers/app_providers.dart';
import '../../themes/app_theme.dart';
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

// ---------------------------------------------------------------------------
// Payroll Requests (HR)
// ---------------------------------------------------------------------------

final payrollRequestsProvider =
    FutureProvider.autoDispose<List<Map<String, dynamic>>>(
  (ref) => ref.watch(workRepositoryProvider).payrollRequests(),
);

/// HR's payslip request inbox: approve or reject employee payslip requests.
///
/// Mirrors the web PayrollRequests page. Read-only on mobile: approving a
/// payslip requires a detailed form (basic, HRA, deductions) that is too
/// risky on a phone. The mobile screen shows the inbox for awareness and
/// provides quick approve/reject for straightforward cases.
class PayrollRequestsScreen extends ConsumerWidget {
  const PayrollRequestsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(payrollRequestsProvider);
    final scheme = Theme.of(context).colorScheme;
    final money = NumberFormat.currency(
      locale: 'en_IN',
      symbol: '₹',
      decimalDigits: 0,
    );

    return Scaffold(
      appBar: AppBar(title: const Text('Payslip requests')),
      body: RefreshIndicator(
        onRefresh: () async => ref.invalidate(payrollRequestsProvider),
        child: async.when(
          loading: () => const LoadingList(),
          error: (e, _) => ListView(
            physics: const AlwaysScrollableScrollPhysics(),
            children: [
              const SizedBox(height: 80),
              ErrorState(
                message: '$e',
                onRetry: () => ref.invalidate(payrollRequestsProvider),
              ),
            ],
          ),
          data: (requests) {
            if (requests.isEmpty) {
              return ListView(
                physics: const AlwaysScrollableScrollPhysics(),
                children: const [
                  SizedBox(height: 80),
                  EmptyState(
                    icon: Icons.receipt_long_outlined,
                    title: 'No pending requests',
                    description:
                        'Payslip requests from employees appear here.',
                  ),
                ],
              );
            }
            return ListView.separated(
              physics: const AlwaysScrollableScrollPhysics(),
              padding: const.all(16),
              itemCount: requests.length,
              separatorBuilder: (_, __) => const SizedBox(height: 10),
              itemBuilder: (context, i) {
                final r = requests[i];
                final status = (r['status'] ?? 'PENDING').toString();
                final isPending = status == 'PENDING';
                final colour = isPending
                    ? AppTheme.warning(context)
                    : status == 'APPROVED'
                        ? AppTheme.success(context)
                        : AppTheme.danger(context);
                final name = r['employeeName']?.toString() ??
                    r['userName']?.toString() ??
                    'Employee #${r['userId'] ?? ''}';
                final month = r['month'] as int?;
                final year = r['year'] as int?;
                final periodLabel = month != null && year != null
                    ? '${DateFormat('MMMM').format(DateTime(year, month))} $year'
                    : 'Unknown period';
                final netPay = r['netPay'] as num?;

                return Card(
                  child: Padding(
                    padding: const EdgeInsets.all(14),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          children: [
                            Expanded(
                              child: Text(
                                name,
                                style: const TextStyle(
                                    fontWeight: FontWeight.w700),
                              ),
                            ),
                            Container(
                              padding: const EdgeInsets.symmetric(
                                horizontal: 8,
                                vertical: 3,
                              ),
                              decoration: BoxDecoration(
                                color: colour.withValues(alpha: 0.14),
                                borderRadius: BorderRadius.circular(12),
                              ),
                              child: Text(
                                status,
                                style: TextStyle(
                                  color: colour,
                                  fontSize: 11,
                                  fontWeight: FontWeight.w700,
                                ),
                              ),
                            ),
                          ],
                        ),
                        const SizedBox(height: 6),
                        Text(
                          periodLabel,
                          style: TextStyle(
                            color: scheme.onSurfaceVariant,
                            fontSize: 13,
                          ),
                        ),
                        if (netPay != null) ...[
                          const SizedBox(height: 4),
                          Text(
                            'Net: ${money.format(netPay)}',
                            style: TextStyle(
                              fontWeight: FontWeight.w600,
                              color: scheme.primary,
                              fontSize: 13,
                            ),
                          ),
                        ],
                        if (r['note'] != null &&
                            (r['note'] as String).isNotEmpty) ...[
                          const SizedBox(height: 4),
                          Text(
                            '${r['note']}',
                            style: TextStyle(
                              color: scheme.onSurfaceVariant,
                              fontSize: 12,
                              fontStyle: FontStyle.italic,
                            ),
                          ),
                        ],
                        if (isPending) ...[
                          const SizedBox(height: 10),
                          Row(
                            children: [
                              Expanded(
                                child: OutlinedButton.icon(
                                  onPressed: () => _reject(
                                    context,
                                    ref,
                                    r['id'] as int?,
                                  ),
                                  icon: const Icon(Icons.close_rounded,
                                      size: 16),
                                  label: const Text('Reject'),
                                  style: OutlinedButton.styleFrom(
                                    foregroundColor:
                                        AppTheme.danger(context),
                                  ),
                                ),
                              ),
                              const SizedBox(width: 10),
                              Expanded(
                                child: FilledButton.icon(
                                  onPressed: () => _approve(
                                    context,
                                    ref,
                                    r['id'] as int?,
                                  ),
                                  icon: const Icon(Icons.check_rounded,
                                      size: 16),
                                  label: const Text('Approve'),
                                ),
                              ),
                            ],
                          ),
                        ],
                      ],
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

  Future<void> _approve(
    BuildContext context,
    WidgetRef ref,
    int? id,
  ) async {
    if (id == null || !context.mounted) return;
    try {
      await ref.read(workRepositoryProvider).approvePayrollRequest(id);
      ref.invalidate(payrollRequestsProvider);
      if (!context.mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Request approved')),
      );
    } catch (e) {
      if (!context.mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('$e'),
          backgroundColor: Theme.of(context).colorScheme.error,
        ),
      );
    }
  }

  Future<void> _reject(
    BuildContext context,
    WidgetRef ref,
    int? id,
  ) async {
    if (id == null || !context.mounted) return;
    final note = await showModalBottomSheet<String>(
      context: context,
      isScrollControlled: true,
      useSafeArea: true,
      builder: (sheetContext) => Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(
              'Reject request',
              style: Theme.of(sheetContext)
                  .textTheme
                  .titleMedium
                  ?.copyWith(fontWeight: FontWeight.w700),
            ),
            const SizedBox(height: 16),
            const TextField(
              decoration: InputDecoration(
                labelText: 'Reason (optional)',
                border: OutlineInputBorder(),
              ),
              maxLines: 2,
            ),
            const SizedBox(height: 16),
            FilledButton(
              onPressed: () => Navigator.pop(sheetContext, 'Rejected'),
              style: FilledButton.styleFrom(
                backgroundColor:
                    Theme.of(sheetContext).colorScheme.error,
              ),
              child: const Text('Reject'),
            ),
            const SizedBox(height: 8),
          ],
        ),
      ),
    );
    if (note == null || !context.mounted) return;
    try {
      await ref
          .read(workRepositoryProvider)
          .rejectPayrollRequest(id, note: note);
      ref.invalidate(payrollRequestsProvider);
      if (!context.mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Request rejected')),
      );
    } catch (e) {
      if (!context.mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('$e'),
          backgroundColor: Theme.of(context).colorScheme.error,
        ),
      );
    }
  }
}

// ---------------------------------------------------------------------------
// Payroll Runs (HR/Finance)
// ---------------------------------------------------------------------------

final payrollRunsProvider =
    FutureProvider.autoDispose<List<Map<String, dynamic>>>(
  (ref) => ref.watch(workRepositoryProvider).payrollRuns(),
);

/// Generate, confirm, and approve monthly payroll runs.
///
/// Mirrors the web PayrollRuns page. HR generates a run, confirms it, and
/// finance approves it — three steps before payslips go out.
class PayrollRunsScreen extends ConsumerStatefulWidget {
  const PayrollRunsScreen({super.key});

  @override
  ConsumerState<PayrollRunsScreen> createState() => _PayrollRunsScreenState();
}

class _PayrollRunsScreenState extends ConsumerState<PayrollRunsScreen> {
  int _month = DateTime.now().month;
  int _year = DateTime.now().year;
  bool _busy = false;

  static const _months = [
    'January', 'February', 'March', 'April', 'May', 'June',
    'July', 'August', 'September', 'October', 'November', 'December',
  ];

  Future<void> _generate() async {
    if (_busy) return;
    setState(() => _busy = true);
    try {
      await ref
          .read(workRepositoryProvider)
          .generatePayrollRun(month: _month, year: _year);
      ref.invalidate(payrollRunsProvider);
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Payroll run generated')),
      );
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('$e'),
          backgroundColor: Theme.of(context).colorScheme.error,
        ),
      );
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _confirm(int id) async {
    if (_busy) return;
    setState(() => _busy = true);
    try {
      await ref.read(workRepositoryProvider).confirmPayrollRun(id);
      ref.invalidate(payrollRunsProvider);
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Payroll run confirmed')),
      );
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('$e'),
          backgroundColor: Theme.of(context).colorScheme.error,
        ),
      );
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _financeApprove(int id) async {
    if (_busy) return;
    setState(() => _busy = true);
    try {
      await ref.read(workRepositoryProvider).financeApprovePayrollRun(id);
      ref.invalidate(payrollRunsProvider);
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Payroll run approved by finance')),
      );
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('$e'),
          backgroundColor: Theme.of(context).colorScheme.error,
        ),
      );
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final async = ref.watch(payrollRunsProvider);
    final money = NumberFormat.currency(
      locale: 'en_IN',
      symbol: '₹',
      decimalDigits: 0,
    );
    final scheme = Theme.of(context).colorScheme;

    return Scaffold(
      appBar: AppBar(
        title: const Text('Payroll runs'),
        actions: [
          IconButton(
            onPressed: _busy ? null : _generate,
            icon: _busy
                ? const SizedBox(
                    height: 18,
                    width: 18,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Icon(Icons.play_arrow_rounded),
            tooltip: 'Generate payroll run',
          ),
        ],
      ),
      body: Column(
        children: [
          // Month/Year selector
          Card(
            margin: const EdgeInsets.all(12),
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
              child: Row(
                children: [
                  const Icon(Icons.calendar_today_rounded, size: 18),
                  const SizedBox(width: 10),
                  Expanded(
                    child: DropdownButton<int>(
                      value: _month,
                      isExpanded: true,
                      underline: const SizedBox(),
                      items: [
                        for (var i = 1; i <= 12; i++)
                          DropdownMenuItem(value: i, child: Text(_months[i - 1])),
                      ],
                      onChanged: (v) => setState(() => _month = v ?? _month),
                    ),
                  ),
                  const SizedBox(width: 10),
                  DropdownButton<int>(
                    value: _year,
                    underline: const SizedBox(),
                    items: [
                      for (var y = DateTime.now().year - 2;
                          y <= DateTime.now().year + 1;
                          y++)
                        DropdownMenuItem(value: y, child: Text('$y')),
                    ],
                    onChanged: (v) => setState(() => _year = v ?? _year),
                  ),
                ],
              ),
            ),
          ),
          // List of runs
          Expanded(
            child: RefreshIndicator(
              onRefresh: () async => ref.invalidate(payrollRunsProvider),
              child: async.when(
                loading: () => const LoadingList(),
                error: (e, _) => ListView(
                  physics: const AlwaysScrollableScrollPhysics(),
                  children: [
                    const SizedBox(height: 80),
                    ErrorState(
                      message: '$e',
                      onRetry: () => ref.invalidate(payrollRunsProvider),
                    ),
                  ],
                ),
                data: (runs) {
                  if (runs.isEmpty) {
                    return ListView(
                      physics: const AlwaysScrollableScrollPhysics(),
                      children: const [
                        SizedBox(height: 80),
                        EmptyState(
                          icon: Icons.payments_outlined,
                          title: 'No payroll runs',
                          description:
                              'Tap the play button to generate a payroll run.',
                        ),
                      ],
                    );
                  }
                  return ListView.separated(
                    physics: const AlwaysScrollableScrollPhysics(),
                    padding: const.all(16),
                    itemCount: runs.length,
                    separatorBuilder: (_, __) => const SizedBox(height: 10),
                    itemBuilder: (context, i) {
                      final run = runs[i];
                      final status = (run['status'] ?? 'PREVIEW').toString();
                      final m = run['runMonth'] as int?;
                      final y = run['runYear'] as int?;
                      final label = m != null && y != null
                          ? '${_months[m - 1]} $y'
                          : 'Unknown';
                      final employees = run['totalEmployees'] ?? 0;
                      final gross = run['totalGross'] as num? ?? 0;
                      final net = run['totalNet'] as num? ?? 0;

                      Color statusColour;
                      switch (status) {
                        case 'FINANCE_APPROVED':
                          statusColour = AppTheme.success(context);
                          break;
                        case 'CONFIRMED':
                          statusColour = Colors.blue;
                          break;
                        default:
                          statusColour = AppTheme.warning(context);
                      }

                      return Card(
                        child: Padding(
                          padding: const EdgeInsets.all(14),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Row(
                                children: [
                                  Expanded(
                                    child: Text(
                                      label,
                                      style: const TextStyle(
                                        fontWeight: FontWeight.w700,
                                        fontSize: 16,
                                      ),
                                    ),
                                  ),
                                  Container(
                                    padding: const EdgeInsets.symmetric(
                                      horizontal: 8,
                                      vertical: 3,
                                    ),
                                    decoration: BoxDecoration(
                                      color: statusColour.withValues(alpha: 0.14),
                                      borderRadius: BorderRadius.circular(12),
                                    ),
                                    child: Text(
                                      status,
                                      style: TextStyle(
                                        color: statusColour,
                                        fontSize: 11,
                                        fontWeight: FontWeight.w700,
                                      ),
                                    ),
                                  ),
                                ],
                              ),
                              const SizedBox(height: 8),
                              Text(
                                '$employees employees',
                                style: TextStyle(
                                  color: scheme.onSurfaceVariant,
                                  fontSize: 13,
                                ),
                              ),
                              const SizedBox(height: 4),
                              Row(
                                children: [
                                  Text(
                                    'Gross: ${money.format(gross)}',
                                    style: TextStyle(
                                      fontSize: 12,
                                      color: scheme.onSurfaceVariant,
                                    ),
                                  ),
                                  const SizedBox(width: 16),
                                  Text(
                                    'Net: ${money.format(net)}',
                                    style: const TextStyle(
                                      fontSize: 12,
                                      fontWeight: FontWeight.w600,
                                    ),
                                  ),
                                ],
                              ),
                              if (status == 'PREVIEW') ...[
                                const SizedBox(height: 10),
                                SizedBox(
                                  width: double.infinity,
                                  child: FilledButton.icon(
                                    onPressed: _busy
                                        ? null
                                        : () => _confirm(run['id'] as int),
                                    icon: const Icon(Icons.check_rounded,
                                        size: 16),
                                    label: const Text('Confirm'),
                                  ),
                                ),
                              ],
                              if (status == 'CONFIRMED') ...[
                                const SizedBox(height: 10),
                                SizedBox(
                                  width: double.infinity,
                                  child: FilledButton.icon(
                                    onPressed: _busy
                                        ? null
                                        : () => _financeApprove(
                                            run['id'] as int),
                                    icon: const Icon(
                                        Icons.verified_rounded,
                                        size: 16),
                                    label: const Text('Finance approve'),
                                  ),
                                ),
                              ],
                            ],
                          ),
                        ),
                      );
                    },
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

// ---------------------------------------------------------------------------
// Team Reports (HR/TL)
// ---------------------------------------------------------------------------

/// Comprehensive team reports matching the web TeamReports page.
///
/// Seven report types (attendance, absentees, leave, permission, work reports,
/// tasks, claims) with month, year, or custom range selection. Downloads
/// an Excel file via the same /reports/* endpoints the web uses.
class TeamReportsScreen extends ConsumerStatefulWidget {
  const TeamReportsScreen({super.key});

  @override
  ConsumerState<TeamReportsScreen> createState() => _TeamReportsScreenState();
}

class _TeamReportsScreenState extends ConsumerState<TeamReportsScreen> {
  String _mode = 'MONTH';
  int _month = DateTime.now().month;
  int _year = DateTime.now().year;
  DateTimeRange? _range;
  String? _busy;

  static const _months = [
    'January', 'February', 'March', 'April', 'May', 'June',
    'July', 'August', 'September', 'October', 'November', 'December',
  ];

  static const _reports = [
    ('attendance', 'Attendance', Icons.access_time_rounded),
    ('leave', 'Leave', Icons.event_note_rounded),
    ('payroll', 'Payroll', Icons.payments_outlined),
  ];

  Future<void> _download(String kind, String label) async {
    if (_busy != null) return;
    setState(() => _busy = kind);

    final fmt = DateFormat('yyyy-MM-dd');
    try {
      final dio = ref.read(apiClientProvider).raw;
      Map<String, dynamic> query;

      if (_mode == 'MONTH') {
        if (kind == 'payroll') {
          query = {'month': _month, 'year': _year};
        } else {
          final from = DateTime(_year, _month, 1);
          final to = DateTime(_year, _month + 1, 0);
          query = {
            'from': fmt.format(from),
            'to': fmt.format(to),
          };
        }
      } else if (_mode == 'YEAR') {
        if (kind == 'payroll') {
          query = {'month': 12, 'year': _year};
        } else {
          query = {
            'from': fmt.format(DateTime(_year, 1, 1)),
            'to': fmt.format(DateTime(_year, 12, 31)),
          };
        }
      } else {
        // Custom range
        if (_range == null) return;
        if (kind == 'payroll') {
          query = {
            'month': _range!.end.month,
            'year': _range!.end.year,
          };
        } else {
          query = {
            'from': fmt.format(_range!.start),
            'to': fmt.format(_range!.end),
          };
        }
      }

      final res = await dio.get<List<int>>(
        '${AppConfig.apiBaseUrl}/reports/$kind',
        queryParameters: query,
        options: Options(responseType: ResponseType.bytes),
      );

      final dir = await getTemporaryDirectory();
      final file = File(
        '${dir.path}/${kind}_report_${DateTime.now().millisecondsSinceEpoch}.xlsx',
      );
      await file.writeAsBytes(res.data ?? const []);

      final opened = await OpenFilex.open(file.path);
      if (!mounted) return;

      if (opened.type != ResultType.done) {
        ScaffoldMessenger.of(context)
          ..hideCurrentSnackBar()
          ..showSnackBar(SnackBar(
            content: Text(
              '$label saved but no spreadsheet app found. '
              'Install Excel or Google Sheets.',
            ),
            duration: const Duration(seconds: 6),
          ));
      } else {
        ScaffoldMessenger.of(context)
          ..hideCurrentSnackBar()
          ..showSnackBar(SnackBar(content: Text('$label downloaded')));
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
      appBar: AppBar(title: const Text('Team reports')),
      body: ListView(
        padding: const.all(16),
        children: [
          // Mode selector
          Card(
            child: Padding(
              padding: const EdgeInsets.all(12),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'Period',
                    style: Theme.of(context)
                        .textTheme
                        .titleSmall
                        ?.copyWith(fontWeight: FontWeight.w700),
                  ),
                  const SizedBox(height: 10),
                  SegmentedButton<String>(
                    segments: const [
                      ButtonSegment(value: 'MONTH', label: Text('Month')),
                      ButtonSegment(value: 'YEAR', label: Text('Year')),
                      ButtonSegment(value: 'RANGE', label: Text('Range')),
                    ],
                    selected: {_mode},
                    onSelectionChanged: (s) =>
                        setState(() => _mode = s.first),
                  ),
                  const SizedBox(height: 12),
                  if (_mode == 'MONTH') ...[
                    Row(
                      children: [
                        Expanded(
                          child: DropdownButton<int>(
                            value: _month,
                            isExpanded: true,
                            underline: const SizedBox(),
                            items: [
                              for (var i = 1; i <= 12; i++)
                                DropdownMenuItem(
                                  value: i,
                                  child: Text(_months[i - 1]),
                                ),
                            ],
                            onChanged: (v) =>
                                setState(() => _month = v ?? _month),
                          ),
                        ),
                        const SizedBox(width: 8),
                        DropdownButton<int>(
                          value: _year,
                          underline: const SizedBox(),
                          items: [
                            for (var y = DateTime.now().year - 2;
                                y <= DateTime.now().year;
                                y++)
                              DropdownMenuItem(value: y, child: Text('$y')),
                          ],
                          onChanged: (v) =>
                              setState(() => _year = v ?? _year),
                        ),
                      ],
                    ),
                  ],
                  if (_mode == 'YEAR')
                    DropdownButton<int>(
                      value: _year,
                      isExpanded: true,
                      underline: const SizedBox(),
                      items: [
                        for (var y = DateTime.now().year - 2;
                            y <= DateTime.now().year;
                            y++)
                          DropdownMenuItem(value: y, child: Text('$y')),
                      ],
                      onChanged: (v) =>
                          setState(() => _year = v ?? _year),
                    ),
                  if (_mode == 'RANGE')
                    Card(
                      child: ListTile(
                        leading: const Icon(Icons.date_range_rounded),
                        title: Text(
                          _range != null
                              ? '${fmt.format(_range!.start)} – ${fmt.format(_range!.end)}'
                              : 'Pick date range',
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
                ],
              ),
            ),
          ),
          const SizedBox(height: 16),
          // Download buttons
          const SectionHeader('Download'),
          const SizedBox(height: 10),
          for (final r in _reports)
            Padding(
              padding: const EdgeInsets.only(bottom: 10),
              child: Card(
                child: ListTile(
                  leading: Icon(r.$3, color: scheme.primary),
                  title: Text(r.$2),
                  subtitle: Text('Excel file (.xlsx)'),
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
            'Reports are spreadsheets. They open in Excel or Google Sheets '
            'once downloaded.',
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

/// Defers to the shared header, so HR's screens space and weight their
/// headings the same as every other screen rather than nearly the same.
class _SectionTitle extends StatelessWidget {
  const _SectionTitle(this.text);
  final String text;

  @override
  Widget build(BuildContext context) => SectionHeader(text);
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
