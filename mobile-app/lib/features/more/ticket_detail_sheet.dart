import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import '../../models/work_items.dart';
import '../../providers/app_providers.dart';
import '../../themes/app_theme.dart';
import 'more_screen.dart' show ticketsProvider;

/// One support ticket, and the conversation on it.
///
/// The list could be tapped and nothing happened, so a ticket could be raised
/// and never read again from the phone -- the reply HR wrote was only visible
/// on the website. This is the other half of that: what was asked, what has
/// been said since, and a box to say the next thing.
class TicketDetailSheet extends ConsumerStatefulWidget {
  const TicketDetailSheet({super.key, required this.ticket});

  final Ticket ticket;

  @override
  ConsumerState<TicketDetailSheet> createState() => _TicketDetailSheetState();
}

class _TicketDetailSheetState extends ConsumerState<TicketDetailSheet> {
  final _reply = TextEditingController();
  List<Map<String, dynamic>>? _comments;
  bool _loading = true;
  bool _sending = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void dispose() {
    _reply.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    setState(() => _loading = true);
    try {
      final list = await ref
          .read(workRepositoryProvider)
          .ticketComments(widget.ticket.id);
      if (!mounted) return;
      setState(() {
        _comments = list;
        _loading = false;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _comments = const [];
        _loading = false;
      });
    }
  }

  Future<void> _send() async {
    final text = _reply.text.trim();
    if (text.isEmpty) return;
    setState(() {
      _sending = true;
      _error = null;
    });
    try {
      await ref.read(workRepositoryProvider).replyToTicket(widget.ticket.id, text);
      _reply.clear();
      // Re-read rather than appending locally, so what is shown is what the
      // server actually stored -- including the name and time it stamped on it.
      await _load();
      if (mounted) ref.invalidate(ticketsProvider);
    } catch (e) {
      if (mounted) setState(() => _error = 'Could not send that reply. Try again.');
    } finally {
      if (mounted) setState(() => _sending = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final t = widget.ticket;
    final scheme = Theme.of(context).colorScheme;
    final closed = t.isClosed;

    return Padding(
      // The keyboard, when the reply box has focus.
      padding: EdgeInsets.only(bottom: MediaQuery.of(context).viewInsets.bottom),
      child: DraggableScrollableSheet(
        expand: false,
        initialChildSize: 0.85,
        maxChildSize: 0.95,
        minChildSize: 0.5,
        builder: (context, controller) => Column(
          children: [
            const SizedBox(height: 10),
            Container(
              width: 40,
              height: 4,
              decoration: BoxDecoration(
                color: scheme.outlineVariant,
                borderRadius: BorderRadius.circular(2),
              ),
            ),
            Expanded(
              child: ListView(
                controller: controller,
                padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
                children: [
                  Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Expanded(
                        child: Text(
                          t.displayTitle,
                          style: Theme.of(context).textTheme.titleMedium
                              ?.copyWith(fontWeight: FontWeight.w700),
                        ),
                      ),
                      const SizedBox(width: 8),
                      Container(
                        padding: const EdgeInsets.symmetric(
                            horizontal: 10, vertical: 4),
                        decoration: BoxDecoration(
                          color: (closed
                                  ? AppTheme.success(context)
                                  : AppTheme.warning(context))
                              .withValues(alpha: 0.14),
                          borderRadius: BorderRadius.circular(AppTheme.radius),
                        ),
                        child: Text(
                          t.status,
                          style: TextStyle(
                            fontSize: 11,
                            fontWeight: FontWeight.w700,
                            color: closed
                                ? AppTheme.success(context)
                                : AppTheme.warning(context),
                          ),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 6),
                  Text(
                    [
                      if (t.referenceCode != null) t.referenceCode!,
                      if (t.type != null) t.type!,
                      if (t.priority != null) '${t.priority} priority',
                      if (t.createdAt != null)
                        DateFormat('d MMM yyyy').format(t.createdAt!),
                    ].join(' · '),
                    style: TextStyle(fontSize: 12, color: scheme.outline),
                  ),
                  if (t.assignedToName != null) ...[
                    const SizedBox(height: 4),
                    Text('Assigned to ${t.assignedToName}',
                        style: TextStyle(fontSize: 12, color: scheme.outline)),
                  ],
                  if ((t.description ?? '').trim().isNotEmpty) ...[
                    const SizedBox(height: 14),
                    Container(
                      width: double.infinity,
                      padding: const EdgeInsets.all(12),
                      decoration: BoxDecoration(
                        color: scheme.surfaceContainerHighest.withValues(alpha: 0.5),
                        borderRadius: BorderRadius.circular(AppTheme.radius),
                      ),
                      child: Text(t.description!),
                    ),
                  ],
                  const SizedBox(height: 18),
                  Text('Replies',
                      style: Theme.of(context).textTheme.titleSmall
                          ?.copyWith(fontWeight: FontWeight.w700)),
                  const SizedBox(height: 8),
                  if (_loading)
                    const Padding(
                      padding: EdgeInsets.symmetric(vertical: 20),
                      child: Center(child: CircularProgressIndicator()),
                    )
                  else if ((_comments ?? const []).isEmpty)
                    Padding(
                      padding: const EdgeInsets.symmetric(vertical: 14),
                      child: Text('No replies yet.',
                          style: TextStyle(color: scheme.outline, fontSize: 13)),
                    )
                  else
                    ...(_comments ?? const []).map((c) {
                      final who = (c['authorName'] ?? '').toString();
                      final when = c['createdAt']?.toString();
                      DateTime? at;
                      if (when != null) at = DateTime.tryParse(when);
                      return Padding(
                        padding: const EdgeInsets.only(bottom: 10),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              [
                                if (who.isNotEmpty) who,
                                if (at != null)
                                  DateFormat('d MMM, h:mm a').format(at),
                              ].join(' · '),
                              style: TextStyle(
                                  fontSize: 11,
                                  fontWeight: FontWeight.w600,
                                  color: scheme.outline),
                            ),
                            const SizedBox(height: 3),
                            Text((c['comment'] ?? '').toString()),
                          ],
                        ),
                      );
                    }),
                ],
              ),
            ),
            if (_error != null)
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 16),
                child: Text(_error!,
                    style: TextStyle(color: scheme.error, fontSize: 12)),
              ),
            /*
              A closed ticket is read-only. Replying to something already
              resolved sends a message nobody is waiting for, and the server
              would refuse it anyway -- better to say so than to let somebody
              type a paragraph and lose it.
            */
            if (!closed)
              Padding(
                padding: const EdgeInsets.fromLTRB(12, 4, 12, 12),
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.end,
                  children: [
                    Expanded(
                      child: TextField(
                        controller: _reply,
                        minLines: 1,
                        maxLines: 4,
                        textCapitalization: TextCapitalization.sentences,
                        decoration: const InputDecoration(
                          hintText: 'Write a reply…',
                          isDense: true,
                        ),
                      ),
                    ),
                    const SizedBox(width: 8),
                    IconButton.filled(
                      onPressed: _sending ? null : _send,
                      icon: _sending
                          ? const SizedBox(
                              width: 18,
                              height: 18,
                              child: CircularProgressIndicator(strokeWidth: 2),
                            )
                          : const Icon(Icons.send_rounded, size: 20),
                    ),
                  ],
                ),
              )
            else
              Padding(
                padding: const EdgeInsets.fromLTRB(16, 4, 16, 16),
                child: Text('This ticket is closed.',
                    style: TextStyle(color: scheme.outline, fontSize: 12)),
              ),
          ],
        ),
      ),
    );
  }
}
