import 'dart:io';

import 'package:file_picker/file_picker.dart';
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

/*
  What the people reporting to you filed.

  The repository could already fetch this and nothing ever called it, so a
  Team Leader or HR could file their own report on the phone but not read
  anybody else's -- the review half of the page existed only on the website.

  The server decides who may look; a refusal surfaces as a failure rather
  than an empty list, so the screen can say "not yours to see" instead of
  "nobody filed anything".
*/
final teamWorkReportsProvider = FutureProvider.autoDispose<List<WorkReport>>(
  (ref) => ref.watch(workRepositoryProvider).teamWorkReports(),
);

/// What you did, day by day.
///
/// The portal's Work Reports page, on a phone. Grouped by date rather than
/// listed flat: a fortnight of rows is fifty entries, and the question somebody
/// actually has is "did I file anything on Tuesday", which a flat list makes
/// them scan for.
class WorkReportsScreen extends ConsumerStatefulWidget {
  const WorkReportsScreen({super.key});

  @override
  ConsumerState<WorkReportsScreen> createState() => _WorkReportsScreenState();
}

class _WorkReportsScreenState extends ConsumerState<WorkReportsScreen> {
  bool _team = false;

  @override
  Widget build(BuildContext context) {
    final ref = this.ref;
    final user = ref.watch(currentUserProvider);
    /*
      Who may review. The same authorities the endpoint is gated on --
      REPORT_VIEW for a Team Leader, USER_MANAGE for HR and the
      administrators. Without either, the toggle is not offered and the
      screen behaves exactly as it did.
    */
    final canSeeTeam = (user?.can('REPORT_VIEW') ?? false) ||
        (user?.can('USER_MANAGE') ?? false);
    final showTeam = canSeeTeam && _team;
    final provider =
        showTeam ? teamWorkReportsProvider : myWorkReportsProvider;
    final async = ref.watch(provider);

    return Scaffold(
      appBar: AppBar(title: const Text('Work reports')),
      floatingActionButton: showTeam
          // Reviewing somebody else's reports is not the moment to file your
          // own, and the sheet files it as yours.
          ? null
          : FloatingActionButton.extended(
              onPressed: () => _openSheet(context, ref),
              icon: const Icon(Icons.add_rounded),
              label: const Text('Add'),
            ),
      body: Column(
        children: [
          if (canSeeTeam)
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 10, 16, 2),
              child: Row(
                children: [
                  for (final (isTeam, label) in const [
                    (false, 'My reports'),
                    (true, 'My team'),
                  ])
                    Padding(
                      padding: const EdgeInsets.only(right: 8),
                      child: ChoiceChip(
                        label: Text(label),
                        selected: _team == isTeam,
                        onSelected: (_) => setState(() => _team = isTeam),
                      ),
                    ),
                ],
              ),
            ),
          Expanded(
            child: RefreshIndicator(
        onRefresh: () async => ref.invalidate(provider),
        child: async.when(
          loading: () => const LoadingList(),
          error: (e, _) => ListView(
            physics: const AlwaysScrollableScrollPhysics(),
            children: [
              const SizedBox(height: 80),
              ErrorState(message: '$e', onRetry: () => ref.invalidate(provider)),
            ],
          ),
          data: (reports) {
            if (reports.isEmpty) {
              return ListView(
                physics: const AlwaysScrollableScrollPhysics(),
                children: [
                  const SizedBox(height: 80),
                  EmptyState(
                    icon: Icons.description_outlined,
                    title: showTeam
                        ? 'Nothing filed by your team yet'
                        : 'No work reports yet',
                    description: showTeam
                        ? 'Reports your team files will appear here.'
                        : 'Add one for today and it will appear here.',
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
        ),
        ],
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

  /*
    Files and links chosen before the report is sent.

    They cannot be uploaded until the report exists -- the endpoint attaches to
    a report id -- so they are held here and sent immediately after it is
    created. Keeping them in a list rather than uploading each on selection
    means somebody who changes their mind has nothing to undo on the server.
  */
  final List<File> _files = [];
  final List<String> _links = [];

  Future<void> _pickFiles() async {
    try {
      final picked = await FilePicker.platform.pickFiles(allowMultiple: true);
      if (picked == null) return;
      setState(() {
        for (final f in picked.files) {
          if (f.path != null) _files.add(File(f.path!));
        }
      });
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context)
        ..hideCurrentSnackBar()
        ..showSnackBar(const SnackBar(content: Text('Could not open the file picker.')));
    }
  }

  Future<void> _addLink() async {
    final controller = TextEditingController();
    final link = await showDialog<String>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Add a link'),
        content: TextField(
          controller: controller,
          autofocus: true,
          keyboardType: TextInputType.url,
          decoration: const InputDecoration(hintText: 'https://…'),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Cancel'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(context, controller.text.trim()),
            child: const Text('Add'),
          ),
        ],
      ),
    );
    if (link == null || link.isEmpty) return;
    // The server accepts http and https only, so anything else is refused here
    // where it can be explained rather than there where it cannot.
    if (!link.startsWith('http://') && !link.startsWith('https://')) {
      if (!mounted) return;
      ScaffoldMessenger.of(context)
        ..hideCurrentSnackBar()
        ..showSnackBar(const SnackBar(
          content: Text('A link has to start with http:// or https://'),
        ));
      return;
    }
    setState(() => _links.add(link));
  }

  Future<void> _submit() async {
    if (_busy) return;
    if (!(_formKey.currentState?.validate() ?? false)) return;

    setState(() => _busy = true);
    try {
      final report = await ref.read(workRepositoryProvider).submitWorkReport(
            workDate: _date,
            projectName: _project.text,
            workHours: double.parse(_hours.text.trim()),
            taskDescription: _task.text,
          );

      /*
        Attachments are a second request, because they attach to a report that
        has to exist first. A failure here is reported without discarding the
        report: the day's work is recorded either way, and losing a submitted
        report because a photo would not upload would be the worse outcome.
      */
      if (_files.isNotEmpty || _links.isNotEmpty) {
        try {
          await ref.read(workRepositoryProvider).addWorkReportAttachments(
                report.id,
                files: _files,
                links: _links,
              );
        } catch (e) {
          if (mounted) {
            ScaffoldMessenger.of(context)
              ..hideCurrentSnackBar()
              ..showSnackBar(const SnackBar(
                content: Text('Report saved, but the attachments did not upload.'),
                duration: Duration(seconds: 4),
              ));
          }
        }
      }

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
              const SizedBox(height: 18),
              /*
                Attachments, as the website has them: files and links.

                Both are offered because a work report often points at
                something that is not a file -- a pull request, a document in
                a shared drive -- and uploading a screenshot of a link is what
                people do when there is nowhere to put the link itself.
              */
              Row(
                children: [
                  Expanded(
                    child: OutlinedButton.icon(
                      onPressed: _busy ? null : _pickFiles,
                      icon: const Icon(Icons.attach_file_rounded, size: 18),
                      label: const Text('Add files'),
                    ),
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: OutlinedButton.icon(
                      onPressed: _busy ? null : _addLink,
                      icon: const Icon(Icons.link_rounded, size: 18),
                      label: const Text('Add link'),
                    ),
                  ),
                ],
              ),
              if (_files.isNotEmpty || _links.isNotEmpty) ...[
                const SizedBox(height: 10),
                Wrap(
                  spacing: 6,
                  runSpacing: 6,
                  children: [
                    for (final f in _files)
                      Chip(
                        avatar: const Icon(Icons.insert_drive_file_outlined, size: 16),
                        label: Text(
                          f.uri.pathSegments.last,
                          overflow: TextOverflow.ellipsis,
                        ),
                        // Removable while it is only a choice; once uploaded it
                        // belongs to the report and is the server's to remove.
                        onDeleted: _busy ? null : () => setState(() => _files.remove(f)),
                      ),
                    for (final l in _links)
                      Chip(
                        avatar: const Icon(Icons.link_rounded, size: 16),
                        label: Text(
                          Uri.tryParse(l)?.host ?? l,
                          overflow: TextOverflow.ellipsis,
                        ),
                        onDeleted: _busy ? null : () => setState(() => _links.remove(l)),
                      ),
                  ],
                ),
              ],
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
