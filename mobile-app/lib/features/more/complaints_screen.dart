import 'package:flutter/material.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import '../../models/complaint.dart';
import '../../providers/app_providers.dart';
import '../../themes/app_theme.dart';
import '../../widgets/states.dart';

final myComplaintsProvider = FutureProvider.autoDispose<List<Complaint>>(
  (ref) => ref.watch(workRepositoryProvider).myComplaints(),
);

final complaintRecipientsProvider =
    FutureProvider.autoDispose<List<ComplaintRecipient>>(
  (ref) => ref.watch(workRepositoryProvider).complaintRecipients(),
);

/// Complaints and needs raised with HR.
///
/// `/complaints/mine`, so this is only ever what the signed-in person raised —
/// never a colleague's. That matters more here than on most screens: a
/// complaint often names somebody, and the endpoint that returns everything is
/// deliberately not the one this calls.
class ComplaintsScreen extends ConsumerWidget {
  const ComplaintsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(myComplaintsProvider);

    return Scaffold(
      appBar: AppBar(title: const Text('Complaints')),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => _raise(context, ref),
        icon: const Icon(Icons.add_rounded),
        label: const Text('Raise'),
      ),
      body: RefreshIndicator(
        onRefresh: () async => ref.invalidate(myComplaintsProvider),
        child: async.when(
          loading: () => const LoadingList(),
          error: (e, _) => ListView(
            physics: const AlwaysScrollableScrollPhysics(),
            children: [
              const SizedBox(height: 60),
              ErrorState(
                message: '$e',
                onRetry: () => ref.invalidate(myComplaintsProvider),
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
                    icon: Icons.forum_outlined,
                    title: 'Nothing raised',
                    description:
                        'Anything you raise with HR appears here, with their reply.',
                  ),
                ],
              );
            }
            return ListView.separated(
              physics: const AlwaysScrollableScrollPhysics(),
              padding: const EdgeInsets.fromLTRB(16, 16, 16, 96),
              itemCount: rows.length,
              separatorBuilder: (_, __) => const SizedBox(height: 10),
              itemBuilder: (context, i) {
                final card = _ComplaintCard(complaint: rows[i]);
                if (i > 8) return card;
                return card
                    .animate()
                    .fadeIn(delay: (i * 40).ms, duration: 220.ms)
                    .slideY(begin: 0.06, end: 0, curve: Curves.easeOutCubic);
              },
            );
          },
        ),
      ),
    );
  }

  Future<void> _raise(BuildContext context, WidgetRef ref) async {
    final saved = await showModalBottomSheet<bool>(
      context: context,
      isScrollControlled: true,
      useSafeArea: true,
      builder: (_) => const RaiseComplaintSheet(),
    );
    if (saved == true) ref.invalidate(myComplaintsProvider);
  }
}

/// The colour a status is shown in.
///
/// Resolved is green, rejected is red, and the two in between are not the same
/// thing: OPEN means nobody has looked, IN_REVIEW means somebody has. Painting
/// both amber would lose the only distinction that tells you whether to chase.
Color _statusTint(BuildContext context, String status) {
  switch (status) {
    case 'RESOLVED':
      return AppTheme.success(context);
    case 'REJECTED':
      return AppTheme.danger(context);
    case 'IN_REVIEW':
      return Theme.of(context).colorScheme.primary;
    default:
      return AppTheme.warning(context);
  }
}

String _statusLabel(String status) => switch (status) {
      'IN_REVIEW' => 'In review',
      'RESOLVED' => 'Resolved',
      'REJECTED' => 'Rejected',
      _ => 'Open',
    };

class _ComplaintCard extends StatelessWidget {
  const _ComplaintCard({required this.complaint});

  final Complaint complaint;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final tint = _statusTint(context, complaint.status);

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Expanded(
                  child: Text(
                    complaint.subject,
                    style: const TextStyle(fontWeight: FontWeight.w700),
                  ),
                ),
                const SizedBox(width: 10),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 4),
                  decoration: BoxDecoration(
                    color: tint.withValues(alpha: 0.14),
                    borderRadius: BorderRadius.circular(20),
                  ),
                  child: Text(
                    _statusLabel(complaint.status),
                    style: TextStyle(
                      color: tint,
                      fontSize: 11,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 6),
            Text(
              [
                if (complaint.referenceCode != null) complaint.referenceCode!,
                if (complaint.kind == 'NEED') 'Need' else 'Complaint',
                if (complaint.createdAt != null)
                  DateFormat('d MMM').format(complaint.createdAt!),
              ].join(' · '),
              style: Theme.of(context)
                  .textTheme
                  .labelSmall
                  ?.copyWith(color: scheme.onSurfaceVariant),
            ),
            if (complaint.description != null) ...[
              const SizedBox(height: 10),
              Text(
                complaint.description!,
                maxLines: 3,
                overflow: TextOverflow.ellipsis,
                style: Theme.of(context).textTheme.bodySmall,
              ),
            ],
            // HR's reply, set apart from the complaint rather than run on after
            // it — whose words these are is the whole point of the box.
            if (complaint.hrResponse != null) ...[
              const SizedBox(height: 12),
              Container(
                width: double.infinity,
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: scheme.primary.withValues(alpha: 0.07),
                  borderRadius: BorderRadius.circular(10),
                  border: Border.all(
                    color: scheme.primary.withValues(alpha: 0.18),
                  ),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      complaint.handledByName == null
                          ? 'HR replied'
                          : 'Reply from ${complaint.handledByName}',
                      style: Theme.of(context).textTheme.labelSmall?.copyWith(
                            color: scheme.primary,
                            fontWeight: FontWeight.w700,
                          ),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      complaint.hrResponse!,
                      style: Theme.of(context).textTheme.bodySmall,
                    ),
                  ],
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }
}

/// Raising something with HR.
class RaiseComplaintSheet extends ConsumerStatefulWidget {
  const RaiseComplaintSheet({super.key});

  @override
  ConsumerState<RaiseComplaintSheet> createState() =>
      _RaiseComplaintSheetState();
}

class _RaiseComplaintSheetState extends ConsumerState<RaiseComplaintSheet> {
  final _formKey = GlobalKey<FormState>();
  final _subject = TextEditingController();
  final _description = TextEditingController();

  String _kind = 'COMPLAINT';
  String _priority = 'MEDIUM';
  int? _requestedTo;
  bool _busy = false;

  @override
  void dispose() {
    _subject.dispose();
    _description.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (_busy) return;
    if (!(_formKey.currentState?.validate() ?? false)) return;

    setState(() => _busy = true);
    try {
      await ref.read(workRepositoryProvider).raiseComplaint(
            subject: _subject.text,
            description: _description.text,
            kind: _kind,
            priority: _priority,
            requestedTo: _requestedTo,
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
    final recipients = ref.watch(complaintRecipientsProvider);

    return Padding(
      padding: EdgeInsets.only(bottom: MediaQuery.of(context).viewInsets.bottom),
      child: SingleChildScrollView(
        padding: const EdgeInsets.fromLTRB(20, 20, 20, 28),
        child: Form(
          key: _formKey,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text(
                'Raise with HR',
                style: Theme.of(context)
                    .textTheme
                    .titleMedium
                    ?.copyWith(fontWeight: FontWeight.w700),
              ),
              const SizedBox(height: 18),
              SegmentedButton<String>(
                segments: const [
                  ButtonSegment(value: 'COMPLAINT', label: Text('Complaint')),
                  ButtonSegment(value: 'NEED', label: Text('Need')),
                ],
                selected: {_kind},
                onSelectionChanged: _busy
                    ? null
                    : (s) => setState(() => _kind = s.first),
              ),
              const SizedBox(height: 16),
              TextFormField(
                controller: _subject,
                enabled: !_busy,
                maxLength: 200,
                textCapitalization: TextCapitalization.sentences,
                decoration: const InputDecoration(
                  labelText: 'Subject',
                  // The server caps this at 200; showing the counter means the
                  // limit is met while typing rather than on rejection.
                  counterText: '',
                ),
                validator: (v) => (v == null || v.trim().isEmpty)
                    ? 'A short subject, please'
                    : null,
              ),
              const SizedBox(height: 14),
              TextFormField(
                controller: _description,
                enabled: !_busy,
                maxLines: 5,
                textCapitalization: TextCapitalization.sentences,
                decoration: const InputDecoration(
                  labelText: 'What happened',
                  alignLabelWithHint: true,
                ),
                validator: (v) => (v == null || v.trim().isEmpty)
                    ? 'Please describe it'
                    : null,
              ),
              const SizedBox(height: 14),
              DropdownButtonFormField<String>(
                value: _priority,
                decoration: const InputDecoration(labelText: 'Priority'),
                items: const [
                  DropdownMenuItem(value: 'LOW', child: Text('Low')),
                  DropdownMenuItem(value: 'MEDIUM', child: Text('Medium')),
                  DropdownMenuItem(value: 'HIGH', child: Text('High')),
                ],
                onChanged: _busy
                    ? null
                    : (v) => setState(() => _priority = v ?? 'MEDIUM'),
              ),
              const SizedBox(height: 14),
              // Optional by design: leaving it unset means any HR can pick it
              // up, which is the server's own default and usually the right
              // answer. A required field here would make somebody choose a name
              // they may have no reason to prefer.
              recipients.when(
                loading: () => const LinearProgressIndicator(minHeight: 2),
                error: (_, __) => const SizedBox.shrink(),
                data: (people) => people.isEmpty
                    ? const SizedBox.shrink()
                    : DropdownButtonFormField<int?>(
                        value: _requestedTo,
                        isExpanded: true,
                        decoration: const InputDecoration(
                          labelText: 'Address to (optional)',
                        ),
                        items: [
                          const DropdownMenuItem<int?>(
                            value: null,
                            child: Text('Any HR'),
                          ),
                          for (final p in people)
                            DropdownMenuItem<int?>(
                              value: p.id,
                              child: Text(p.name, overflow: TextOverflow.ellipsis),
                            ),
                        ],
                        onChanged:
                            _busy ? null : (v) => setState(() => _requestedTo = v),
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
                    : const Text('Submit'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
