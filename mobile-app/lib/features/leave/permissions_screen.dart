import 'package:flutter/material.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import '../../models/leave.dart';
import '../../providers/app_providers.dart';
import '../../widgets/states.dart';
import '../../widgets/date_field.dart';

final myPermissionsProvider =
    FutureProvider.autoDispose<List<PermissionRequestItem>>(
  (ref) => ref.watch(workRepositoryProvider).myPermissions(),
);

final permissionApproversProvider =
    FutureProvider.autoDispose<List<Map<String, dynamic>>>(
  (ref) => ref.watch(workRepositoryProvider).permissionApprovers(),
);

/// Short permissions — an hour or two off, not a whole day.
///
/// A separate thing from leave, and the portal treats it that way: it has its
/// own endpoint, its own approver list and its own status. Folding it into the
/// Leave screen would have made a two-hour dentist appointment look like it
/// spends a day of somebody's balance, which is exactly what it does not do.
class PermissionsScreen extends ConsumerWidget {
  const PermissionsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(myPermissionsProvider);

    return Scaffold(
      appBar: AppBar(title: const Text('Permission')),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => _request(context, ref),
        icon: const Icon(Icons.add_rounded),
        label: const Text('Request'),
      ),
      body: RefreshIndicator(
        onRefresh: () async => ref.invalidate(myPermissionsProvider),
        child: async.when(
          loading: () => const LoadingList(),
          error: (e, _) => ListView(
            physics: const AlwaysScrollableScrollPhysics(),
            children: [
              const SizedBox(height: 80),
              ErrorState(
                message: '$e',
                onRetry: () => ref.invalidate(myPermissionsProvider),
              ),
            ],
          ),
          data: (items) {
            if (items.isEmpty) {
              return ListView(
                physics: const AlwaysScrollableScrollPhysics(),
                children: const [
                  SizedBox(height: 80),
                  EmptyState(
                    icon: Icons.schedule_rounded,
                    title: 'No permission requests',
                    description:
                        'Ask for an hour or two off without using a leave day.',
                  ),
                ],
              );
            }

            return ListView.builder(
              physics: const AlwaysScrollableScrollPhysics(),
              padding: const EdgeInsets.fromLTRB(16, 16, 16, 96),
              itemCount: items.length,
              itemBuilder: (context, i) => _Card(
                item: items[i],
                onCancel: () => _cancel(context, ref, items[i]),
              )
                  .animate()
                  .fadeIn(delay: (i.clamp(0, 8) * 40).ms, duration: 220.ms)
                  .slideY(begin: 0.06, end: 0, curve: Curves.easeOutCubic),
            );
          },
        ),
      ),
    );
  }

  Future<void> _request(BuildContext context, WidgetRef ref) async {
    final made = await showModalBottomSheet<bool>(
      context: context,
      isScrollControlled: true,
      useSafeArea: true,
      builder: (_) => const RequestPermissionSheet(),
    );
    if (made == true) ref.invalidate(myPermissionsProvider);
  }

  Future<void> _cancel(
    BuildContext context,
    WidgetRef ref,
    PermissionRequestItem item,
  ) async {
    final sure = await showDialog<bool>(
      context: context,
      builder: (c) => AlertDialog(
        title: const Text('Withdraw this request?'),
        content: Text(
          '${DateFormat('d MMM').format(item.requestDate)}, '
          '${item.fromTime}–${item.toTime}',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(c).pop(false),
            child: const Text('Keep it'),
          ),
          FilledButton(
            onPressed: () => Navigator.of(c).pop(true),
            child: const Text('Withdraw'),
          ),
        ],
      ),
    );
    if (sure != true) return;

    try {
      await ref.read(workRepositoryProvider).cancelPermission(item.id);
      ref.invalidate(myPermissionsProvider);
    } catch (e) {
      if (!context.mounted) return;
      ScaffoldMessenger.of(context)
        ..hideCurrentSnackBar()
        ..showSnackBar(SnackBar(content: Text('$e')));
    }
  }
}

class _Card extends StatelessWidget {
  const _Card({required this.item, required this.onCancel});

  final PermissionRequestItem item;
  final VoidCallback onCancel;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final (colour, label) = switch (item.status.toUpperCase()) {
      'APPROVED' => (Colors.green, 'Approved'),
      'REJECTED' => (scheme.error, 'Rejected'),
      'CANCELLED' => (scheme.outline, 'Withdrawn'),
      _ => (Colors.orange, 'Pending'),
    };

    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: Card(
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Expanded(
                    child: Text(
                      DateFormat('EEE, d MMM yyyy').format(item.requestDate),
                      style: const TextStyle(fontWeight: FontWeight.w700),
                    ),
                  ),
                  Container(
                    padding:
                        const EdgeInsets.symmetric(horizontal: 9, vertical: 3),
                    decoration: BoxDecoration(
                      color: colour.withValues(alpha: 0.14),
                      borderRadius: BorderRadius.circular(20),
                    ),
                    child: Text(
                      label,
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
              Row(
                children: [
                  Icon(Icons.schedule_rounded, size: 15, color: scheme.onSurfaceVariant),
                  const SizedBox(width: 6),
                  Text(
                    '${item.fromTime} – ${item.toTime}'
                    '${item.hours == null ? '' : '  ·  ${item.hours}h'}',
                    style: TextStyle(color: scheme.onSurfaceVariant, fontSize: 13),
                  ),
                ],
              ),
              if (item.reason != null) ...[
                const SizedBox(height: 8),
                Text(item.reason!, style: const TextStyle(height: 1.35)),
              ],
              if (item.requestedToName != null) ...[
                const SizedBox(height: 8),
                Text(
                  'Sent to ${item.requestedToName}',
                  style: Theme.of(context)
                      .textTheme
                      .labelSmall
                      ?.copyWith(color: scheme.onSurfaceVariant),
                ),
              ],
              // The decision's reason, where there is one. A rejection with no
              // explanation is the thing people come back to ask about.
              if (item.decisionComment != null) ...[
                const SizedBox(height: 8),
                Container(
                  width: double.infinity,
                  padding: const EdgeInsets.all(10),
                  decoration: BoxDecoration(
                    color: colour.withValues(alpha: 0.08),
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: Text(
                    item.decidedByName == null
                        ? item.decisionComment!
                        : '${item.decidedByName}: ${item.decisionComment}',
                    style: const TextStyle(fontSize: 12.5, height: 1.35),
                  ),
                ),
              ],
              if (item.isPending) ...[
                const SizedBox(height: 10),
                Align(
                  alignment: Alignment.centerRight,
                  child: TextButton(
                    onPressed: onCancel,
                    child: const Text('Withdraw'),
                  ),
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }
}

/// Asking for a couple of hours.
class RequestPermissionSheet extends ConsumerStatefulWidget {
  const RequestPermissionSheet({super.key});

  @override
  ConsumerState<RequestPermissionSheet> createState() =>
      _RequestPermissionSheetState();
}

class _RequestPermissionSheetState
    extends ConsumerState<RequestPermissionSheet> {
  final _formKey = GlobalKey<FormState>();
  final _reason = TextEditingController();

  DateTime _date = DateTime.now();
  TimeOfDay _from = const TimeOfDay(hour: 10, minute: 0);
  TimeOfDay _to = const TimeOfDay(hour: 12, minute: 0);
  int? _approver;
  String _priority = 'MEDIUM';
  bool _busy = false;

  @override
  void dispose() {
    _reason.dispose();
    super.dispose();
  }

  /// "14:30" — the shape the server stores, not the shape a phone shows.
  ///
  /// TimeOfDay.format() is localised and would send "2:30 PM" on a device set to
  /// twelve-hour time, which the server does not parse.
  String _hhmm(TimeOfDay t) =>
      '${t.hour.toString().padLeft(2, '0')}:${t.minute.toString().padLeft(2, '0')}';

  int _minutes(TimeOfDay t) => t.hour * 60 + t.minute;

  Future<void> _submit() async {
    if (_busy) return;
    if (!(_formKey.currentState?.validate() ?? false)) return;

    if (_minutes(_to) <= _minutes(_from)) {
      ScaffoldMessenger.of(context)
        ..hideCurrentSnackBar()
        ..showSnackBar(
          const SnackBar(content: Text('The end time must be after the start.')),
        );
      return;
    }

    setState(() => _busy = true);
    try {
      await ref.read(workRepositoryProvider).applyForPermission(
            requestDate: _date,
            fromTime: _hhmm(_from),
            toTime: _hhmm(_to),
            reason: _reason.text,
            requestedTo: _approver,
            priority: _priority,
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
    final approvers = ref.watch(permissionApproversProvider);

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
                'Request permission',
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

              Row(
                children: [
                  Expanded(child: _timeField('From', _from, () => _pickTime(true))),
                  const SizedBox(width: 12),
                  Expanded(child: _timeField('To', _to, () => _pickTime(false))),
                ],
              ),
              const SizedBox(height: 14),

              // Optional on the server, so optional here — and the list can be
              // empty for somebody with no assigned approver, which must not
              // leave a dropdown that cannot be opened.
              approvers.maybeWhen(
                data: (list) => list.isEmpty
                    ? const SizedBox.shrink()
                    : DropdownButtonFormField<int>(
                        initialValue: _approver,
                        decoration: const InputDecoration(
                          labelText: 'Send to (optional)',
                          prefixIcon: Icon(Icons.person_outline_rounded),
                        ),
                        items: [
                          for (final a in list)
                            DropdownMenuItem(
                              value: (a['id'] as num?)?.toInt(),
                              child: Text(a['name']?.toString() ?? 'Approver'),
                            ),
                        ],
                        onChanged: _busy ? null : (v) => setState(() => _approver = v),
                      ),
                orElse: () => const SizedBox.shrink(),
              ),
              const SizedBox(height: 14),

              DropdownButtonFormField<String>(
                initialValue: _priority,
                decoration: const InputDecoration(
                  labelText: 'Priority',
                  prefixIcon: Icon(Icons.flag_outlined),
                ),
                items: const [
                  DropdownMenuItem(value: 'LOW', child: Text('Low')),
                  DropdownMenuItem(value: 'MEDIUM', child: Text('Medium')),
                  DropdownMenuItem(value: 'HIGH', child: Text('High')),
                ],
                onChanged: _busy ? null : (v) => setState(() => _priority = v ?? 'MEDIUM'),
              ),
              const SizedBox(height: 14),

              TextFormField(
                controller: _reason,
                enabled: !_busy,
                maxLines: 3,
                textCapitalization: TextCapitalization.sentences,
                decoration: const InputDecoration(
                  labelText: 'Reason',
                  alignLabelWithHint: true,
                ),
                validator: (v) => (v == null || v.trim().isEmpty)
                    ? 'Say why — whoever approves it will ask otherwise'
                    : null,
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
                    : const Text('Send request'),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _timeField(String label, TimeOfDay value, VoidCallback onTap) {
    return InkWell(
      onTap: _busy ? null : onTap,
      borderRadius: BorderRadius.circular(12),
      child: InputDecorator(
        decoration: InputDecoration(
          labelText: label,
          prefixIcon: const Icon(Icons.schedule_outlined),
        ),
        child: Text(_hhmm(value)),
      ),
    );
  }

  Future<void> _pickDate() async {
    final now = DateTime.now();
    final picked = await pickDate(
      context,
      initialDate: _date,
      // A week back for one already taken and being recorded late; three months
      // forward is as far as anybody plans an hour off.
      firstDate: now.subtract(const Duration(days: 7)),
      lastDate: now.add(const Duration(days: 90)),
    );
    if (picked != null && mounted) setState(() => _date = picked);
  }

  Future<void> _pickTime(bool isFrom) async {
    final picked = await showTimePicker(
      context: context,
      initialTime: isFrom ? _from : _to,
    );
    if (picked == null || !mounted) return;
    setState(() {
      if (isFrom) {
        _from = picked;
        // Keeping the window sane as the start moves. Without this, dragging the
        // start past the end leaves a request the server rejects for a reason
        // the form already knew.
        if (_minutes(_to) <= _minutes(picked)) {
          _to = TimeOfDay(
            hour: (picked.hour + 1).clamp(0, 23),
            minute: picked.minute,
          );
        }
      } else {
        _to = picked;
      }
    });
  }
}
