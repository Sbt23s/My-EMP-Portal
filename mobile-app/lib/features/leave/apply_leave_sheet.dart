import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import '../../models/leave.dart';
import '../../providers/app_providers.dart';
import '../../providers/cache.dart';

final leaveTypesProvider = FutureProvider.autoDispose<List<LeaveType>>(
  (ref) {
  cacheFor(ref, cacheLong);
  return ref.watch(workRepositoryProvider).leaveTypes();
});

final approversProvider =
    FutureProvider.autoDispose<List<Map<String, dynamic>>>(
      (ref) => ref.watch(workRepositoryProvider).approvers(),
    );

/// Apply for leave. Pops `true` when something was actually created, so the
/// caller knows whether to reload.
class ApplyLeaveSheet extends ConsumerStatefulWidget {
  const ApplyLeaveSheet({super.key});

  @override
  ConsumerState<ApplyLeaveSheet> createState() => _ApplyLeaveSheetState();
}

class _ApplyLeaveSheetState extends ConsumerState<ApplyLeaveSheet> {
  final _formKey = GlobalKey<FormState>();
  final _reason = TextEditingController();

  int? _typeId;
  int? _approverId;
  DateTime? _from;
  DateTime? _to;
  bool _busy = false;

  @override
  void dispose() {
    _reason.dispose();
    super.dispose();
  }

  /// Whole days only, and never in the past — the server checks this too.
  Future<void> _pickRange() async {
    final now = DateTime.now();
    final picked = await showDateRangePicker(
      context: context,
      firstDate: DateTime(now.year, now.month, now.day),
      lastDate: DateTime(now.year + 1, 12, 31),
      initialDateRange: _from != null && _to != null
          ? DateTimeRange(start: _from!, end: _to!)
          : null,
    );
    if (picked != null) {
      setState(() {
        _from = picked.start;
        _to = picked.end;
      });
    }
  }

  Future<void> _submit() async {
    if (_busy) return;
    if (!(_formKey.currentState?.validate() ?? false)) return;
    if (_from == null || _to == null) {
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('Choose the dates')));
      return;
    }

    setState(() => _busy = true);
    try {
      await ref
          .read(workRepositoryProvider)
          .applyForLeave(
            leaveTypeId: _typeId!,
            fromDate: _from!,
            toDate: _to!,
            reason: _reason.text,
            requestedTo: _approverId,
          );
      if (!mounted) return;
      Navigator.pop(context, true);
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('Leave request sent')));
    } catch (e) {
      if (!mounted) return;
      setState(() => _busy = false);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(e.toString()),
          backgroundColor: Theme.of(context).colorScheme.error,
        ),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final types = ref.watch(leaveTypesProvider);
    final approvers = ref.watch(approversProvider);

    return Padding(
      // Lifts the sheet above the keyboard instead of hiding the fields.
      padding: EdgeInsets.only(bottom: MediaQuery.viewInsetsOf(context).bottom),
      child: SingleChildScrollView(
        padding: const EdgeInsets.all(20),
        child: Form(
          key: _formKey,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Center(
                child: Container(
                  width: 40,
                  height: 4,
                  decoration: BoxDecoration(
                    color: Theme.of(context).colorScheme.outlineVariant,
                    borderRadius: BorderRadius.circular(2),
                  ),
                ),
              ),
              const SizedBox(height: 20),
              Text(
                'Apply for leave',
                style: Theme.of(
                  context,
                ).textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w700),
              ),
              const SizedBox(height: 20),
              types.when(
                loading: () => const Center(
                  child: Padding(
                    padding: EdgeInsets.all(20),
                    child: CircularProgressIndicator(),
                  ),
                ),
                error: (e, _) => Text(
                  'Could not load leave types: $e',
                  style: TextStyle(color: Theme.of(context).colorScheme.error),
                ),
                data: (list) => DropdownButtonFormField<int>(
                  initialValue: _typeId,
                  decoration: const InputDecoration(labelText: 'Leave type'),
                  items: [
                    for (final t in list)
                      DropdownMenuItem(value: t.id, child: Text(t.name)),
                  ],
                  onChanged: _busy ? null : (v) => setState(() => _typeId = v),
                  validator: (v) => v == null ? 'Choose a leave type' : null,
                ),
              ),
              const SizedBox(height: 14),
              InkWell(
                onTap: _busy ? null : _pickRange,
                borderRadius: BorderRadius.circular(12),
                child: InputDecorator(
                  decoration: const InputDecoration(
                    labelText: 'Dates',
                    prefixIcon: Icon(Icons.date_range_rounded),
                  ),
                  child: Text(
                    _from == null || _to == null
                        ? 'Choose dates'
                        : '${DateFormat('d MMM yyyy').format(_from!)} – '
                              '${DateFormat('d MMM yyyy').format(_to!)}',
                  ),
                ),
              ),
              const SizedBox(height: 14),
              approvers.maybeWhen(
                data: (list) => list.isEmpty
                    ? const SizedBox.shrink()
                    : DropdownButtonFormField<int>(
                        initialValue: _approverId,
                        decoration: const InputDecoration(labelText: 'Send to'),
                        items: [
                          for (final a in list)
                            DropdownMenuItem(
                              value: (a['id'] as num?)?.toInt(),
                              child: Text(a['name']?.toString() ?? 'Approver'),
                            ),
                        ],
                        onChanged: _busy
                            ? null
                            : (v) => setState(() => _approverId = v),
                      ),
                orElse: () => const SizedBox.shrink(),
              ),
              const SizedBox(height: 14),
              TextFormField(
                controller: _reason,
                maxLines: 3,
                maxLength: 500,
                enabled: !_busy,
                decoration: const InputDecoration(
                  labelText: 'Reason',
                  alignLabelWithHint: true,
                ),
              ),
              const SizedBox(height: 8),
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
              const SizedBox(height: 12),
            ],
          ),
        ),
      ),
    );
  }
}
