import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import '../../models/directory_person.dart';
import '../../providers/app_providers.dart';
import '../../widgets/date_field.dart';

/// Colleagues a task can be handed to.
///
/// The server decides who comes back -- a Team Leader sees their team, HR sees
/// the company -- so this does not filter. Asking for the wrong people is not
/// something the phone should be arbitrating.
final assignableePeopleProvider =
    FutureProvider.autoDispose<List<DirectoryPerson>>(
  (ref) => ref.watch(workRepositoryProvider).directory(),
);

/// Handing a task to somebody. Pops `true` when one was actually created.
///
/// A Team Leader holds TASK_ASSIGN and HR holds USER_MANAGE; the website could
/// assign work and the phone could only report progress on work already
/// assigned. The server checks the authority again, so a sheet that opens for
/// the wrong person still cannot create anything.
class AssignTaskSheet extends ConsumerStatefulWidget {
  const AssignTaskSheet({super.key});

  @override
  ConsumerState<AssignTaskSheet> createState() => _AssignTaskSheetState();
}

class _AssignTaskSheetState extends ConsumerState<AssignTaskSheet> {
  final _formKey = GlobalKey<FormState>();
  final _title = TextEditingController();
  final _description = TextEditingController();

  int? _assignedTo;
  DateTime? _dueDate;
  String _priority = 'MEDIUM';
  bool _busy = false;

  @override
  void dispose() {
    _title.dispose();
    _description.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (_busy) return;
    if (!(_formKey.currentState?.validate() ?? false)) return;

    setState(() => _busy = true);
    final messenger = ScaffoldMessenger.of(context);
    final navigator = Navigator.of(context);
    try {
      await ref.read(workRepositoryProvider).assignTask(
            title: _title.text,
            assignedTo: _assignedTo!,
            description: _description.text,
            dueDate: _dueDate,
            priority: _priority,
          );
      navigator.pop(true);
      messenger
        ..hideCurrentSnackBar()
        ..showSnackBar(const SnackBar(content: Text('Task assigned')));
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
    final people = ref.watch(assignableePeopleProvider);

    return Padding(
      padding: EdgeInsets.fromLTRB(
        20,
        20,
        20,
        MediaQuery.viewInsetsOf(context).bottom + 20,
      ),
      child: SingleChildScrollView(
        child: Form(
          key: _formKey,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text(
                'Assign a task',
                style: Theme.of(context)
                    .textTheme
                    .titleMedium
                    ?.copyWith(fontWeight: FontWeight.w700),
              ),
              const SizedBox(height: 16),

              TextFormField(
                controller: _title,
                enabled: !_busy,
                textCapitalization: TextCapitalization.sentences,
                decoration: const InputDecoration(labelText: 'Title *'),
                validator: (v) => (v == null || v.trim().isEmpty)
                    ? 'Say what the task is'
                    : null,
              ),
              const SizedBox(height: 12),

              // Required: a task assigned to nobody is not a task.
              people.when(
                loading: () => const LinearProgressIndicator(minHeight: 2),
                error: (e, __) => Text(
                  'Could not load who this can go to. $e',
                  style: TextStyle(
                    fontSize: 12,
                    color: Theme.of(context).colorScheme.error,
                  ),
                ),
                data: (list) => list.isEmpty
                    ? Text(
                        'There is nobody you can assign a task to.',
                        style: TextStyle(
                          fontSize: 12,
                          color: Theme.of(context).colorScheme.error,
                        ),
                      )
                    : DropdownButtonFormField<int>(
                        initialValue: _assignedTo,
                        isExpanded: true,
                        decoration:
                            const InputDecoration(labelText: 'Assign to *'),
                        items: [
                          for (final person in list)
                            DropdownMenuItem<int>(
                              value: person.id,
                              child: Text(
                                person.name,
                                overflow: TextOverflow.ellipsis,
                              ),
                            ),
                        ],
                        validator: (v) =>
                            v == null ? 'Choose who does this' : null,
                        onChanged: _busy
                            ? null
                            : (v) => setState(() => _assignedTo = v),
                      ),
              ),
              const SizedBox(height: 12),

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
              const SizedBox(height: 12),

              // A tap opens the picker; the field itself is never typed into,
              // which is how the rest of the app takes a date.
              InkWell(
                onTap: _busy
                    ? null
                    : () async {
                        final now = DateTime.now();
                        final picked = await pickDate(
                          context,
                          initialDate: _dueDate ?? now,
                          firstDate: now.subtract(const Duration(days: 365)),
                          lastDate: now.add(const Duration(days: 365 * 2)),
                        );
                        if (picked != null) {
                          setState(() => _dueDate = picked);
                        }
                      },
                child: InputDecorator(
                  decoration: const InputDecoration(labelText: 'Due date'),
                  child: Text(
                    _dueDate == null
                        ? 'Not set'
                        : DateFormat('d MMM yyyy').format(_dueDate!),
                  ),
                ),
              ),
              const SizedBox(height: 12),

              TextFormField(
                controller: _description,
                enabled: !_busy,
                minLines: 3,
                maxLines: 6,
                textCapitalization: TextCapitalization.sentences,
                decoration: const InputDecoration(
                  labelText: 'Details',
                  alignLabelWithHint: true,
                ),
              ),
              const SizedBox(height: 20),

              FilledButton(
                onPressed: _busy ? null : _submit,
                child: _busy
                    ? const SizedBox(
                        height: 22,
                        width: 22,
                        child: CircularProgressIndicator(strokeWidth: 2.4),
                      )
                    : const Text('Assign'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
