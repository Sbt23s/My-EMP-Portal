import 'package:flutter/material.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import '../../models/work_report.dart';
import '../../providers/app_providers.dart';
import '../../widgets/states.dart';
import '../../widgets/date_field.dart';

final myWorkReportsProvider = FutureProvider.autoDispose<List<WorkReport>>(
  (ref) => ref.watch(workRepositoryProvider).myWorkReports(),
);

/// What you did, day by day.
///
/// The portal's Work Reports page, on a phone. Grouped by date rather than
/// listed flat: a fortnight of rows is fifty entries, and the question somebody
/// actually has is "did I file anything on Tuesday", which a flat list makes
/// them scan for.
class WorkReportsScreen extends ConsumerWidget {
  const WorkReportsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(myWorkReportsProvider);

    return Scaffold(
      appBar: AppBar(title: const Text('Work reports')),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => _openSheet(context, ref),
        icon: const Icon(Icons.add_rounded),
        label: const Text('Add'),
      ),
      body: RefreshIndicator(
        onRefresh: () async => ref.invalidate(myWorkReportsProvider),
        child: async.when(
          loading: () => const LoadingList(),
          error: (e, _) => ListView(
            physics: const AlwaysScrollableScrollPhysics(),
            children: [
              const SizedBox(height: 80),
              ErrorState(message: '$e', onRetry: () => ref.invalidate(myWorkReportsProvider)),
            ],
          ),
          data: (reports) {
            if (reports.isEmpty) {
              return ListView(
                physics: const AlwaysScrollableScrollPhysics(),
                children: const [
                  SizedBox(height: 80),
                  EmptyState(
                    icon: Icons.description_outlined,
                    title: 'No work reports yet',
                    description: 'Add one for today and it will appear here.',
                  ),
                ],
              );
            }

            final groups = _byDate(reports);
            final dates = groups.keys.toList()..sort((a, b) => b.compareTo(a));

            return ListView.builder(
              physics: const AlwaysScrollableScrollPhysics(),
              padding: const EdgeInsets.fromLTRB(16, 16, 16, 96),
              itemCount: dates.length,
              itemBuilder: (context, i) {
                final date = dates[i];
                final rows = groups[date]!;
                final hours = rows.fold<double>(0, (sum, r) => sum + r.workHours);

                return _DayGroup(date: date, rows: rows, totalHours: hours)
                    .animate()
                    .fadeIn(delay: (i.clamp(0, 8) * 40).ms, duration: 220.ms)
                    .slideY(begin: 0.06, end: 0, curve: Curves.easeOutCubic);
              },
            );
          },
        ),
      ),
    );
  }

  /// Keyed by the day alone — two rows filed at different times on the same date
  /// belong together, and a DateTime carrying a time would put them in
  /// different groups.
  static Map<DateTime, List<WorkReport>> _byDate(List<WorkReport> reports) {
    final out = <DateTime, List<WorkReport>>{};
    for (final r in reports) {
      final key = DateTime(r.workDate.year, r.workDate.month, r.workDate.day);
      out.putIfAbsent(key, () => []).add(r);
    }
    return out;
  }

  Future<void> _openSheet(BuildContext context, WidgetRef ref) async {
    final saved = await showModalBottomSheet<bool>(
      context: context,
      isScrollControlled: true,
      useSafeArea: true,
      builder: (_) => const SubmitWorkReportSheet(),
    );
    if (saved == true) ref.invalidate(myWorkReportsProvider);
  }
}

class _DayGroup extends StatelessWidget {
  const _DayGroup({
    required this.date,
    required this.rows,
    required this.totalHours,
  });

  final DateTime date;
  final List<WorkReport> rows;
  final double totalHours;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final today = DateTime.now();
    final isToday = date.year == today.year &&
        date.month == today.month &&
        date.day == today.day;

    return Padding(
      padding: const EdgeInsets.only(bottom: 16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Text(
                isToday ? 'Today' : DateFormat('EEE, d MMM').format(date),
                style: Theme.of(context)
                    .textTheme
                    .titleSmall
                    ?.copyWith(fontWeight: FontWeight.w700),
              ),
              const Spacer(),
              Text(
                // Trailing zero dropped: "7h" reads better than "7.0h", and the
                // half-days that do need it still show as "7.5h".
                '${_trim(totalHours)}h',
                style: Theme.of(context).textTheme.labelMedium?.copyWith(
                      color: scheme.primary,
                      fontWeight: FontWeight.w700,
                      fontFeatures: const [FontFeature.tabularFigures()],
                    ),
              ),
            ],
          ),
          const SizedBox(height: 8),
          Card(
            child: Column(
              children: [
                for (var i = 0; i < rows.length; i++) ...[
                  if (i > 0) const Divider(height: 1),
                  ListTile(
                    title: Text(
                      rows[i].projectName,
                      style: const TextStyle(fontWeight: FontWeight.w600),
                    ),
                    subtitle: rows[i].taskDescription == null
                        ? null
                        : Text(rows[i].taskDescription!),
                    trailing: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      crossAxisAlignment: CrossAxisAlignment.end,
                      children: [
                        Text(
                          '${_trim(rows[i].workHours)}h',
                          style: const TextStyle(
                            fontWeight: FontWeight.w700,
                            fontFeatures: [FontFeature.tabularFigures()],
                          ),
                        ),
                        if (rows[i].attachmentCount > 0)
                          Text(
                            '${rows[i].attachmentCount} file'
                            '${rows[i].attachmentCount == 1 ? '' : 's'}',
                            style: Theme.of(context)
                                .textTheme
                                .labelSmall
                                ?.copyWith(color: scheme.onSurfaceVariant),
                          ),
                      ],
                    ),
                  ),
                ],
              ],
            ),
          ),
        ],
      ),
    );
  }

  static String _trim(double value) {
    final fixed = value.toStringAsFixed(1);
    return fixed.endsWith('.0') ? fixed.substring(0, fixed.length - 2) : fixed;
  }
}

/// Filing a day's work.
///
/// Kept to the four fields the endpoint takes. Attachments are deliberately not
/// here: the server accepts them on a second call against the saved row, and a
/// file picker that half-works is worse than one that is honestly absent.
class SubmitWorkReportSheet extends ConsumerStatefulWidget {
  const SubmitWorkReportSheet({super.key});

  @override
  ConsumerState<SubmitWorkReportSheet> createState() =>
      _SubmitWorkReportSheetState();
}

class _SubmitWorkReportSheetState extends ConsumerState<SubmitWorkReportSheet> {
  final _formKey = GlobalKey<FormState>();
  final _project = TextEditingController();
  final _hours = TextEditingController();
  final _task = TextEditingController();

  DateTime _date = DateTime.now();
  bool _busy = false;

  @override
  void dispose() {
    _project.dispose();
    _hours.dispose();
    _task.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (_busy) return;
    if (!(_formKey.currentState?.validate() ?? false)) return;

    setState(() => _busy = true);
    try {
      await ref.read(workRepositoryProvider).submitWorkReport(
            workDate: _date,
            projectName: _project.text,
            workHours: double.parse(_hours.text.trim()),
            taskDescription: _task.text,
          );
      if (!mounted) return;
      Navigator.of(context).pop(true);
    } catch (e) {
      if (!mounted) return;
      setState(() => _busy = false);
      ScaffoldMessenger.of(context)
        ..hideCurrentSnackBar()
        ..showSnackBar(SnackBar(content: Text('$e')));
    }
  }

  @override
  Widget build(BuildContext context) {
    return Padding(
      // Lifts the sheet above the keyboard, so the field being typed into is
      // never the one hidden by it.
      padding: EdgeInsets.only(
        bottom: MediaQuery.of(context).viewInsets.bottom,
      ),
      child: SingleChildScrollView(
        padding: const EdgeInsets.fromLTRB(20, 20, 20, 28),
        child: Form(
          key: _formKey,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text(
                'Add work report',
                style: Theme.of(context)
                    .textTheme
                    .titleMedium
                    ?.copyWith(fontWeight: FontWeight.w700),
              ),
              const SizedBox(height: 18),
              InkWell(
                onTap: _busy ? null : _pickDate,
                borderRadius: BorderRadius.circular(12),
                child: InputDecorator(
                  decoration: const InputDecoration(
                    labelText: 'Date',
                    prefixIcon: Icon(Icons.calendar_today_outlined),
                  ),
                  child: Text(DateFormat('EEE, d MMM yyyy').format(_date)),
                ),
              ),
              const SizedBox(height: 14),
              TextFormField(
                controller: _project,
                enabled: !_busy,
                textCapitalization: TextCapitalization.sentences,
                decoration: const InputDecoration(
                  labelText: 'Project',
                  prefixIcon: Icon(Icons.folder_outlined),
                ),
                validator: (v) => (v == null || v.trim().isEmpty)
                    ? 'Which project was this for?'
                    : null,
              ),
              const SizedBox(height: 14),
              TextFormField(
                controller: _hours,
                enabled: !_busy,
                keyboardType: const TextInputType.numberWithOptions(decimal: true),
                decoration: const InputDecoration(
                  labelText: 'Hours',
                  prefixIcon: Icon(Icons.schedule_outlined),
                ),
                validator: (v) {
                  final text = v?.trim() ?? '';
                  if (text.isEmpty) return 'How many hours?';
                  final hours = double.tryParse(text);
                  if (hours == null) return 'Enter a number, like 7.5';
                  if (hours <= 0) return 'Must be more than zero';
                  // A day has 24 hours and the server will reject more; saying so
                  // here saves a round trip and an error nobody expected.
                  if (hours > 24) return "That's more than a day";
                  return null;
                },
              ),
              const SizedBox(height: 14),
              TextFormField(
                controller: _task,
                enabled: !_busy,
                maxLines: 4,
                textCapitalization: TextCapitalization.sentences,
                decoration: const InputDecoration(
                  labelText: 'What you did (optional)',
                  alignLabelWithHint: true,
                ),
              ),
              const SizedBox(height: 22),
              FilledButton(
                onPressed: _busy ? null : _submit,
                child: _busy
                    ? const SizedBox(
                        height: 22,
                        width: 22,
                        child: CircularProgressIndicator(strokeWidth: 2.4),
                      )
                    : const Text('Save'),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Future<void> _pickDate() async {
    final now = DateTime.now();
    final picked = await pickDate(
      context,
      initialDate: _date,
      // Sixty days back covers a genuine catch-up without offering a date range
      // nobody files against. No future dates: a report is what was done.
      firstDate: now.subtract(const Duration(days: 60)),
      lastDate: now,
    );
    if (picked != null && mounted) setState(() => _date = picked);
  }
}
