import 'package:flutter/material.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import '../../models/complaint.dart';
import '../../providers/app_providers.dart';
import '../../themes/app_theme.dart';
import '../../widgets/states.dart';
import '../../widgets/ui_kit.dart';

final myComplaintsProvider = FutureProvider.autoDispose<List<Complaint>>(
  (ref) => ref.watch(workRepositoryProvider).myComplaints(),
);

/*
  Every complaint, for the people who review them.

  Separate from myComplaintsProvider because they answer different questions
  and a reviewer needs both: what is waiting on me, and what I raised myself.
  Only requested when the permission is held -- the server refuses it
  otherwise, and asking anyway would put an error on a screen that is working.
*/
final allComplaintsProvider = FutureProvider.autoDispose<List<Complaint>>(
  (ref) => ref.watch(workRepositoryProvider).allComplaints(),
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
class ComplaintsScreen extends ConsumerStatefulWidget {
  const ComplaintsScreen({super.key});

  @override
  ConsumerState<ComplaintsScreen> createState() => _ComplaintsScreenState();
}

/// Which complaints a reviewer is looking at.
enum _Scope { addressedToMe, mine, all }

class _ComplaintsScreenState extends ConsumerState<ComplaintsScreen> {
  _Scope _scope = _Scope.addressedToMe;

  @override
  Widget build(BuildContext context) {
    final user = ref.watch(currentUserProvider);
    /*
      Who reviews. The same two permissions the web page uses -- HR through
      COMPLAINT_MANAGE, the administrators and the CTO through USER_MANAGE.
      The server checks them again on every call; hiding the tabs is a
      courtesy, never the control.
    */
    final canReview =
        (user?.can('USER_MANAGE') ?? false) ||
        (user?.can('COMPLAINT_MANAGE') ?? false);

    /*
      The CTO and the system administrators receive complaints rather than
      raise them: every recipient list offers them, and there is nobody above
      them to address one to. HR keeps the button, because HR can address
      theirs to the CTO.
    */
    final isTop = user?.isCompanyAdmin ?? false;

    return canReview
        ? _reviewer(context, user?.id, isTop)
        : _mine(context, showRaise: !isTop);
  }

  // ---- what everybody else sees: their own, exactly as before -------------
  Widget _mine(BuildContext context, {required bool showRaise}) {
    final async = ref.watch(myComplaintsProvider);

    return Scaffold(
      appBar: AppBar(title: const Text('Complaints')),
      floatingActionButton: showRaise
          ? FloatingActionButton.extended(
              onPressed: () => _raise(context, ref),
              icon: const Icon(Icons.add_rounded),
              label: const Text('Raise'),
            )
          : null,
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

  // ---- what a reviewer sees ---------------------------------------------
  Widget _reviewer(BuildContext context, int? meId, bool isTop) {
    final async = ref.watch(allComplaintsProvider);

    return Scaffold(
      appBar: AppBar(title: const Text('Complaints')),
      floatingActionButton: isTop
          ? null
          : FloatingActionButton.extended(
              onPressed: () => _raise(context, ref),
              icon: const Icon(Icons.add_rounded),
              label: const Text('Raise'),
            ),
      body: RefreshIndicator(
        onRefresh: () async => ref.invalidate(allComplaintsProvider),
        child: async.when(
          loading: () => const LoadingList(),
          error: (e, _) => ListView(
            physics: const AlwaysScrollableScrollPhysics(),
            children: [
              const SizedBox(height: 60),
              ErrorState(
                message: '$e',
                onRetry: () => ref.invalidate(allComplaintsProvider),
              ),
            ],
          ),
          data: (everything) {
            final toMe =
                everything.where((c) => c.requestedTo == meId).toList();
            final byMe = everything.where((c) => c.raisedBy == meId).toList();
            final rows = switch (_scope) {
              _Scope.addressedToMe => toMe,
              _Scope.mine => byMe,
              _Scope.all => everything,
            };

            return Column(
              children: [
                _ScopeBar(
                  scope: _scope,
                  toMe: toMe.length,
                  mine: byMe.length,
                  all: everything.length,
                  onChanged: (s) => setState(() => _scope = s),
                ),
                Expanded(
                  child: rows.isEmpty
                      ? ListView(
                          physics: const AlwaysScrollableScrollPhysics(),
                          children: const [
                            SizedBox(height: 60),
                            EmptyState(
                              icon: Icons.forum_outlined,
                              title: 'Nothing here',
                              description:
                                  'Complaints appear here as they are raised.',
                            ),
                          ],
                        )
                      : ListView.separated(
                          physics: const AlwaysScrollableScrollPhysics(),
                          padding:
                              const EdgeInsets.fromLTRB(16, 12, 16, 96),
                          itemCount: rows.length,
                          separatorBuilder: (_, __) =>
                              const SizedBox(height: 10),
                          itemBuilder: (context, i) => _ComplaintCard(
                            complaint: rows[i],
                            /*
                              Only the person it was addressed to decides it,
                              never its author, and not once it is settled --
                              a second response would erase the answer the
                              submitter has already been given.
                            */
                            onRespond: (rows[i].requestedTo == meId &&
                                    rows[i].raisedBy != meId &&
                                    !rows[i].isClosed)
                                ? () => _respond(context, rows[i])
                                : null,
                          ),
                        ),
                ),
              ],
            );
          },
        ),
      ),
    );
  }

  Future<void> _respond(BuildContext context, Complaint c) async {
    final saved = await showModalBottomSheet<bool>(
      context: context,
      isScrollControlled: true,
      useSafeArea: true,
      builder: (_) => RespondComplaintSheet(complaint: c),
    );
    if (saved == true) {
      ref
        ..invalidate(allComplaintsProvider)
        ..invalidate(myComplaintsProvider);
    }
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
  const _ComplaintCard({required this.complaint, this.onRespond});

  final Complaint complaint;

  /// Given only to the person who may actually decide this one. Null for
  /// everybody else, and the button is simply absent rather than disabled.
  final VoidCallback? onRespond;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final tint = _statusTint(context, complaint.status);

    return Container(
      decoration: UI.card(context),
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
              ].join(' \u00b7 '),
              style: Theme.of(context)
                  .textTheme
                  .labelSmall
                  ?.copyWith(color: scheme.onSurfaceVariant),
            ),
            /*
              Both ends of the complaint. Three people can review, so a card
              that names only the person who raised it leaves everyone
              guessing whose it is.
            */
            if (complaint.raisedByName != null ||
                complaint.requestedToName != null) ...[
              const SizedBox(height: 4),
              Text(
                [
                  if (complaint.raisedByName != null)
                    'From ${complaint.raisedByName}',
                  if (complaint.requestedToName != null)
                    'To ${complaint.requestedToName}',
                ].join('  \u00b7  '),
                style: Theme.of(context)
                    .textTheme
                    .labelSmall
                    ?.copyWith(color: scheme.onSurfaceVariant),
              ),
            ],
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
            if (onRespond != null) ...[
              const SizedBox(height: 12),
              SizedBox(
                width: double.infinity,
                child: FilledButton.tonalIcon(
                  onPressed: onRespond,
                  icon: const Icon(Icons.reply_rounded, size: 18),
                  label: const Text('Respond'),
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }
}

/// Mine, addressed to me, or everybody's.
///
/// Shown as counts so the number of complaints actually waiting on this
/// person is visible without switching to find out.
class _ScopeBar extends StatelessWidget {
  const _ScopeBar({
    required this.scope,
    required this.toMe,
    required this.mine,
    required this.all,
    required this.onChanged,
  });

  final _Scope scope;
  final int toMe;
  final int mine;
  final int all;
  final ValueChanged<_Scope> onChanged;

  @override
  Widget build(BuildContext context) {
    final items = <(_Scope, String)>[
      (_Scope.addressedToMe, 'To me ($toMe)'),
      (_Scope.mine, 'Mine ($mine)'),
      (_Scope.all, 'All ($all)'),
    ];
    return SingleChildScrollView(
      scrollDirection: Axis.horizontal,
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 4),
      child: Row(
        children: [
          for (final (value, label) in items)
            Padding(
              padding: const EdgeInsets.only(right: 8),
              child: ChoiceChip(
                label: Text(label),
                selected: scope == value,
                onSelected: (_) => onChanged(value),
              ),
            ),
        ],
      ),
    );
  }
}

/// Settling a complaint: a step forward, and what to tell the person.
class RespondComplaintSheet extends ConsumerStatefulWidget {
  const RespondComplaintSheet({super.key, required this.complaint});

  final Complaint complaint;

  @override
  ConsumerState<RespondComplaintSheet> createState() =>
      _RespondComplaintSheetState();
}

class _RespondComplaintSheetState
    extends ConsumerState<RespondComplaintSheet> {
  /*
    Where a complaint may go next -- and never where it already is.

    A step is a move forward or it is not offered: an open complaint that
    offers "Open" lets somebody save a change that changes nothing, and a
    settled one cannot move at all.
  */
  static const Map<String, List<String>> _next = {
    'OPEN': ['IN_REVIEW', 'RESOLVED', 'REJECTED'],
    'IN_REVIEW': ['RESOLVED', 'REJECTED'],
    'RESOLVED': <String>[],
    'REJECTED': <String>[],
  };

  late final List<String> _allowed = _next[widget.complaint.status] ?? const [];
  late String _status = _allowed.isEmpty ? widget.complaint.status : _allowed.first;
  final _reply = TextEditingController();
  bool _busy = false;

  @override
  void dispose() {
    _reply.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    if (_busy || _allowed.isEmpty) return;
    setState(() => _busy = true);
    final messenger = ScaffoldMessenger.of(context);
    final navigator = Navigator.of(context);
    try {
      await ref.read(workRepositoryProvider).respondToComplaint(
            widget.complaint.id,
            status: _status,
            response: _reply.text,
          );
      navigator.pop(true);
      messenger
        ..hideCurrentSnackBar()
        ..showSnackBar(const SnackBar(content: Text('Complaint updated')));
    } catch (e) {
      if (!mounted) return;
      setState(() => _busy = false);
      messenger
        ..hideCurrentSnackBar()
        ..showSnackBar(SnackBar(content: Text('$e')));
    }
  }

  @override
  Widget build(BuildContext context) {
    final c = widget.complaint;
    return Padding(
      padding: EdgeInsets.fromLTRB(
        20,
        20,
        20,
        MediaQuery.viewInsetsOf(context).bottom + 20,
      ),
      child: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(
              c.subject,
              style: Theme.of(context)
                  .textTheme
                  .titleMedium
                  ?.copyWith(fontWeight: FontWeight.w700),
            ),
            if (c.raisedByName != null) ...[
              const SizedBox(height: 4),
              Text(
                'Raised by ${c.raisedByName}',
                style: Theme.of(context).textTheme.labelSmall,
              ),
            ],
            if (c.description != null) ...[
              const SizedBox(height: 12),
              Text(c.description!,
                  style: Theme.of(context).textTheme.bodySmall),
            ],
            const SizedBox(height: 18),
            const Text('Status',
                style: TextStyle(fontWeight: FontWeight.w600, fontSize: 13)),
            const SizedBox(height: 8),
            Wrap(
              spacing: 8,
              children: [
                for (final st in _allowed)
                  ChoiceChip(
                    label: Text(_statusLabel(st)),
                    selected: _status == st,
                    onSelected: (_) => setState(() => _status = st),
                  ),
              ],
            ),
            const SizedBox(height: 18),
            TextField(
              controller: _reply,
              minLines: 3,
              maxLines: 6,
              decoration: const InputDecoration(
                labelText: 'Response',
                hintText: 'What to tell the person who raised this',
                alignLabelWithHint: true,
              ),
            ),
            const SizedBox(height: 18),
            FilledButton(
              onPressed: _busy || _allowed.isEmpty ? null : _save,
              child: _busy
                  ? const SizedBox(
                      height: 18,
                      width: 18,
                      child: CircularProgressIndicator(strokeWidth: 2.2),
                    )
                  : const Text('Save response'),
            ),
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
                initialValue: _priority,
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
              /*
                Required, as on the website.

                It was optional, defaulting to "Any HR" -- and a complaint
                addressed to nobody is the reason none of them reached the
                person they were meant for. Somebody raising a complaint knows
                who it is about; making them say so is the whole point of the
                field, and it is what decides who may respond.
              */
              recipients.when(
                loading: () => const LinearProgressIndicator(minHeight: 2),
                error: (e, __) => Text(
                  'Could not load who this can be sent to. $e',
                  style: TextStyle(
                    fontSize: 12,
                    color: Theme.of(context).colorScheme.error,
                  ),
                ),
                data: (people) => people.isEmpty
                    ? Text(
                        'There is nobody set up to receive complaints yet.',
                        style: TextStyle(
                          fontSize: 12,
                          color: Theme.of(context).colorScheme.error,
                        ),
                      )
                    : DropdownButtonFormField<int?>(
                        initialValue: _requestedTo,
                        isExpanded: true,
                        decoration: const InputDecoration(
                          labelText: 'Send to *',
                        ),
                        items: [
                          for (final p in people)
                            DropdownMenuItem<int?>(
                              value: p.id,
                              child: Text(p.name, overflow: TextOverflow.ellipsis),
                            ),
                        ],
                        validator: (v) =>
                            v == null ? 'Choose who this goes to' : null,
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
