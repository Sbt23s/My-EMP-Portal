import 'package:flutter/material.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import '../../models/safety.dart';
import '../../providers/app_providers.dart';
import '../../themes/app_theme.dart';
import '../../widgets/states.dart';

final mySafetyProvider = FutureProvider.autoDispose<List<SafetyIncident>>(
  (ref) => ref.watch(workRepositoryProvider).mySafetyIncidents(),
);

/// Safety incidents: report one, see your own, and (staff) review and resolve.
///
/// The portal's Safety page on a phone. Any signed-in person can file a report
/// and read their own; somebody holding REPORT_VIEW sees every report and can
/// move one from OPEN through INVESTIGATING to RESOLVED / CLOSED — the same
/// boundary the server enforces, so the extra tab is a courtesy, not a control.
class SafetyScreen extends ConsumerWidget {
  const SafetyScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final user = ref.watch(currentUserProvider);
    final canReview = user?.can('REPORT_VIEW') ?? false;

    return DefaultTabController(
      length: canReview ? 2 : 1,
      child: Scaffold(
        appBar: AppBar(
          title: const Text('Safety'),
          bottom: canReview
              ? const TabBar(tabs: [
                  Tab(text: 'My reports'),
                  Tab(text: 'All reports'),
                ])
              : null,
        ),
        floatingActionButton: FloatingActionButton.extended(
          onPressed: () => _report(context, ref),
          icon: const Icon(Icons.add_alert_rounded),
          label: const Text('Report'),
        ),
        body: canReview
            ? const TabBarView(children: [
                _MyReportsTab(),
                _AllReportsTab(),
              ])
            : const _MyReportsTab(),
      ),
    );
  }

  Future<void> _report(BuildContext context, WidgetRef ref) async {
    final created = await showModalBottomSheet<bool>(
      context: context,
      isScrollControlled: true,
      showDragHandle: true,
      builder: (_) => const ReportIncidentSheet(),
    );
    if (created == true && context.mounted) {
      ref.invalidate(mySafetyProvider);
      ref.invalidate(allSafetyProvider);
    }
  }
}

final allSafetyProvider = FutureProvider.autoDispose<List<SafetyIncident>>(
  (ref) => ref.watch(workRepositoryProvider).allSafetyIncidents(),
);

class _MyReportsTab extends ConsumerWidget {
  const _MyReportsTab();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(mySafetyProvider);
    return RefreshIndicator(
      onRefresh: () async => ref.invalidate(mySafetyProvider),
      child: async.when(
        loading: () => const LoadingList(),
        error: (e, _) => ListView(
          physics: const AlwaysScrollableScrollPhysics(),
          children: [
            const SizedBox(height: 80),
            ErrorState(message: '$e', onRetry: () => ref.invalidate(mySafetyProvider)),
          ],
        ),
        data: (rows) {
          if (rows.isEmpty) {
            return ListView(
              physics: const AlwaysScrollableScrollPhysics(),
              children: const [
                SizedBox(height: 80),
                EmptyState(
                  icon: Icons.shield_outlined,
                  title: 'No incidents reported',
                  description:
                      'Something unsafe? Report it here — it goes straight to the people who can act.',
                ),
              ],
            );
          }
          return ListView.builder(
            physics: const AlwaysScrollableScrollPhysics(),
            padding: const EdgeInsets.fromLTRB(16, 16, 16, 96),
            itemCount: rows.length,
            itemBuilder: (context, i) {
              final row = rows[i];
              return _IncidentCard(incident: row)
                  .animate()
                  .fadeIn(delay: (i.clamp(0, 8) * 40).ms, duration: 220.ms)
                  .slideY(begin: 0.06, end: 0, curve: Curves.easeOutCubic);
            },
          );
        },
      ),
    );
  }
}

class _AllReportsTab extends ConsumerWidget {
  const _AllReportsTab();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(allSafetyProvider);
    return RefreshIndicator(
      onRefresh: () async => ref.invalidate(allSafetyProvider),
      child: async.when(
        loading: () => const LoadingList(),
        error: (e, _) => ListView(
          physics: const AlwaysScrollableScrollPhysics(),
          children: [
            const SizedBox(height: 80),
            ErrorState(message: '$e', onRetry: () => ref.invalidate(allSafetyProvider)),
          ],
        ),
        data: (rows) {
          if (rows.isEmpty) {
            return ListView(
              physics: const AlwaysScrollableScrollPhysics(),
              children: const [
                SizedBox(height: 80),
                EmptyState(
                  icon: Icons.shield_outlined,
                  title: 'No reports to review',
                  description: 'Incidents filed by the team will appear here.',
                ),
              ],
            );
          }
          return ListView.builder(
            physics: const AlwaysScrollableScrollPhysics(),
            padding: const EdgeInsets.fromLTRB(16, 16, 16, 96),
            itemCount: rows.length,
            itemBuilder: (context, i) {
              final row = rows[i];
              return _IncidentCard(
                incident: row,
                canResolve: true,
              )
                  .animate()
                  .fadeIn(delay: (i.clamp(0, 8) * 40).ms, duration: 220.ms)
                  .slideY(begin: 0.06, end: 0, curve: Curves.easeOutCubic);
            },
          );
        },
      ),
    );
  }
}

class _IncidentCard extends StatelessWidget {
  const _IncidentCard({required this.incident, this.canResolve = false});

  final SafetyIncident incident;
  final bool canResolve;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final status = (incident.status ?? 'OPEN').toUpperCase();
    final (label, color) = switch (status) {
      'RESOLVED' || 'CLOSED' => (incident.status!, AppTheme.success(context)),
      'INVESTIGATING' => (incident.status!, AppTheme.warning(context)),
      _ => (incident.status ?? 'OPEN', scheme.error),
    };

    return Card(
      margin: const EdgeInsets.only(bottom: 12),
      child: InkWell(
        borderRadius: BorderRadius.circular(12),
        onTap: canResolve
            ? () => _resolve(context)
            : () => _detail(context),
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Container(
                    padding: const EdgeInsets.all(8),
                    decoration: BoxDecoration(
                      color: color.withValues(alpha: 0.12),
                      borderRadius: BorderRadius.circular(10),
                    ),
                    child: Icon(Icons.warning_amber_rounded, color: color, size: 20),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          _typeLabel(incident.incidentType),
                          style: const TextStyle(fontWeight: FontWeight.w700),
                        ),
                        if (incident.referenceCode != null)
                          Text(
                            incident.referenceCode!,
                            style: TextStyle(
                              fontSize: 12,
                              color: scheme.onSurfaceVariant,
                            ),
                          ),
                      ],
                    ),
                  ),
                  _StatusBadge(label: label, color: color),
                ],
              ),
              if (incident.description != null) ...[
                const SizedBox(height: 12),
                Text(incident.description!),
              ],
              const SizedBox(height: 12),
              Row(
                children: [
                  Icon(Icons.person_outline_rounded, size: 16, color: scheme.onSurfaceVariant),
                  const SizedBox(width: 6),
                  Text(
                    incident.anonymous
                        ? 'Anonymous'
                        : (incident.reportedByName ?? '—'),
                    style: TextStyle(fontSize: 12, color: scheme.onSurfaceVariant),
                  ),
                  const SizedBox(width: 16),
                  Icon(Icons.category_outlined, size: 16, color: scheme.onSurfaceVariant),
                  const SizedBox(width: 6),
                  Text(
                    _severityLabel(incident.severity),
                    style: TextStyle(fontSize: 12, color: scheme.onSurfaceVariant),
                  ),
                  const Spacer(),
                  if (canResolve)
                    const Icon(Icons.edit_rounded, size: 16)
                  else
                    const Icon(Icons.chevron_right_rounded, size: 18),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }

  void _detail(BuildContext context) {
    showModalBottomSheet<void>(
      context: context,
      showDragHandle: true,
      builder: (_) => _IncidentDetail(incident: incident),
    );
  }

  void _resolve(BuildContext context) {
    showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      showDragHandle: true,
      builder: (_) => _ResolveSheet(incident: incident),
    );
  }

  static String _typeLabel(String? type) {
    return switch (type?.toUpperCase()) {
      'NEAR_MISS' => 'Near miss',
      'MINOR_INJURY' => 'Minor injury',
      'MAJOR_INJURY' => 'Major injury',
      'PROPERTY_DAMAGE' => 'Property damage',
      'ENV_HAZARD' => 'Environmental hazard',
      _ => type ?? 'Incident',
    };
  }

  static String _severityLabel(String? severity) {
    return switch (severity?.toUpperCase()) {
      'LOW' => 'Low',
      'MEDIUM' => 'Medium',
      'HIGH' => 'High',
      'CRITICAL' => 'Critical',
      _ => severity ?? '—',
    };
  }
}

class _StatusBadge extends StatelessWidget {
  const _StatusBadge({required this.label, required this.color});

  final String label;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.12),
        borderRadius: BorderRadius.circular(999),
      ),
      child: Text(
        label,
        style: TextStyle(fontSize: 11, fontWeight: FontWeight.w700, color: color),
      ),
    );
  }
}

class _IncidentDetail extends StatelessWidget {
  const _IncidentDetail({required this.incident});

  final SafetyIncident incident;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final when = incident.occurredAt ?? incident.createdAt;
    final whenLabel = when == null ? '—' : DateFormat('d MMM yyyy, h:mm a').format(when);

    return SafeArea(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(20, 0, 20, 24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(incident.description ?? '', style: const TextStyle(fontSize: 16)),
            const SizedBox(height: 16),
            _DetailRow(label: 'Type', value: _IncidentCard._typeLabel(incident.incidentType)),
            _DetailRow(label: 'Severity', value: _IncidentCard._severityLabel(incident.severity)),
            _DetailRow(label: 'Status', value: incident.status ?? '—'),
            if (incident.zone != null) _DetailRow(label: 'Zone', value: incident.zone!),
            _DetailRow(label: 'Occurred', value: whenLabel),
            _DetailRow(
              label: 'Reported by',
              value: incident.anonymous ? 'Anonymous' : (incident.reportedByName ?? '—'),
            ),
            if (incident.resolutionNotes != null)
              _DetailRow(label: 'Resolution', value: incident.resolutionNotes!),
          ],
        ),
      ),
    );
  }
}

class _DetailRow extends StatelessWidget {
  const _DetailRow({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 90,
            child: Text(
              label,
              style: TextStyle(color: scheme.onSurfaceVariant, fontSize: 13),
            ),
          ),
          Expanded(child: Text(value, style: const TextStyle(fontSize: 13))),
        ],
      ),
    );
  }
}

/// File a report. Pops `true` when one was actually created.
class ReportIncidentSheet extends ConsumerStatefulWidget {
  const ReportIncidentSheet({super.key});

  @override
  ConsumerState<ReportIncidentSheet> createState() => _ReportIncidentSheetState();
}

class _ReportIncidentSheetState extends ConsumerState<ReportIncidentSheet> {
  final _formKey = GlobalKey<FormState>();
  final _description = TextEditingController();
  final _zone = TextEditingController();

  static const _types = ['NEAR_MISS', 'MINOR_INJURY', 'MAJOR_INJURY', 'PROPERTY_DAMAGE', 'ENV_HAZARD'];
  static const _severities = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];

  String _type = 'NEAR_MISS';
  String _severity = 'MEDIUM';
  bool _anonymous = false;
  DateTime _occurredAt = DateTime.now();
  bool _busy = false;

  @override
  void dispose() {
    _description.dispose();
    _zone.dispose();
    super.dispose();
  }

  Future<void> _pickWhen() async {
    final now = DateTime.now();
    final picked = await showDatePicker(
      context: context,
      initialDate: _occurredAt,
      firstDate: DateTime(now.year - 1),
      lastDate: now,
    );
    if (picked != null && mounted) setState(() => _occurredAt = picked);
  }

  Future<void> _submit() async {
    if (_busy) return;
    if (!(_formKey.currentState?.validate() ?? false)) return;

    setState(() => _busy = true);
    try {
      await ref.read(workRepositoryProvider).reportSafetyIncident(
            incidentType: _type,
            description: _description.text.trim(),
            zone: _zone.text.trim(),
            anonymous: _anonymous,
            occurredAt: _occurredAt.toUtc().toIso8601String(),
            severity: _severity,
          );
      if (mounted) Navigator.of(context).pop(true);
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context)
        ..hideCurrentSnackBar()
        ..showSnackBar(
          SnackBar(
            content: Text('$e'),
            backgroundColor: Theme.of(context).colorScheme.error,
          ),
        );
      setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return SafeArea(
      child: Padding(
        padding: EdgeInsets.only(
          left: 20,
          right: 20,
          top: 4,
          bottom: MediaQuery.of(context).viewInsets.bottom + 24,
        ),
        child: Form(
          key: _formKey,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('Report an incident', style: Theme.of(context).textTheme.titleLarge),
              const SizedBox(height: 16),
              DropdownButtonFormField<String>(
                initialValue: _type,
                decoration: const InputDecoration(labelText: 'Type'),
                items: _types
                    .map((t) => DropdownMenuItem(
                          value: t,
                          child: Text(_IncidentCard._typeLabel(t)),
                        ))
                    .toList(),
                onChanged: (v) => setState(() => _type = v ?? _type),
              ),
              const SizedBox(height: 12),
              TextFormField(
                controller: _description,
                maxLines: 4,
                decoration: const InputDecoration(
                  labelText: 'Describe what happened',
                  alignLabelWithHint: true,
                ),
                validator: (v) =>
                    (v == null || v.trim().isEmpty) ? 'Please describe the incident' : null,
              ),
              const SizedBox(height: 12),
              TextFormField(
                controller: _zone,
                decoration: const InputDecoration(
                  labelText: 'Zone / location (optional)',
                ),
              ),
              const SizedBox(height: 12),
              DropdownButtonFormField<String>(
                initialValue: _severity,
                decoration: const InputDecoration(labelText: 'Severity'),
                items: _severities
                    .map((s) => DropdownMenuItem(
                          value: s,
                          child: Text(_IncidentCard._severityLabel(s)),
                        ))
                    .toList(),
                onChanged: (v) => setState(() => _severity = v ?? _severity),
              ),
              const SizedBox(height: 12),
              ListTile(
                contentPadding: EdgeInsets.zero,
                leading: const Icon(Icons.schedule_rounded),
                title: const Text('When it happened'),
                trailing: Text(
                  DateFormat('d MMM yyyy').format(_occurredAt),
                  style: TextStyle(color: scheme.primary, fontWeight: FontWeight.w600),
                ),
                onTap: _pickWhen,
              ),
              SwitchListTile(
                contentPadding: EdgeInsets.zero,
                title: const Text('Report anonymously'),
                subtitle: const Text('Your name is hidden from the report'),
                value: _anonymous,
                onChanged: (v) => setState(() => _anonymous = v),
              ),
              const SizedBox(height: 8),
              SizedBox(
                width: double.infinity,
                child: FilledButton.icon(
                  onPressed: _busy ? null : _submit,
                  icon: _busy
                      ? const SizedBox(
                          width: 18,
                          height: 18,
                          child: CircularProgressIndicator(strokeWidth: 2),
                        )
                      : const Icon(Icons.send_rounded),
                  label: Text(_busy ? 'Reporting…' : 'Report incident'),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// Staff resolution of one incident. Pops `true` when the status changed.
class _ResolveSheet extends ConsumerStatefulWidget {
  const _ResolveSheet({required this.incident});

  final SafetyIncident incident;

  @override
  ConsumerState<_ResolveSheet> createState() => _ResolveSheetState();
}

class _ResolveSheetState extends ConsumerState<_ResolveSheet> {
  final _notes = TextEditingController();
  static const _statuses = ['OPEN', 'INVESTIGATING', 'RESOLVED', 'CLOSED'];
  String _status = 'OPEN';
  bool _busy = false;

  @override
  void dispose() {
    _notes.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    if (_busy) return;
    setState(() => _busy = true);
    try {
      await ref.read(workRepositoryProvider).resolveSafetyIncident(
            widget.incident.id,
            status: _status,
            notes: _notes.text,
          );
      if (mounted) Navigator.of(context).pop(true);
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context)
        ..hideCurrentSnackBar()
        ..showSnackBar(SnackBar(content: Text('$e')));
      setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(20, 0, 20, 24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Update incident', style: Theme.of(context).textTheme.titleLarge),
            const SizedBox(height: 8),
            Text(
              widget.incident.referenceCode ?? 'Incident',
              style: TextStyle(color: Theme.of(context).colorScheme.onSurfaceVariant),
            ),
            const SizedBox(height: 16),
            DropdownButtonFormField<String>(
              initialValue: _status,
              decoration: const InputDecoration(labelText: 'Status'),
              items: _statuses
                  .map((s) => DropdownMenuItem(value: s, child: Text(s)))
                  .toList(),
              onChanged: (v) => setState(() => _status = v ?? _status),
            ),
            const SizedBox(height: 12),
            TextFormField(
              controller: _notes,
              maxLines: 3,
              decoration: const InputDecoration(
                labelText: 'Resolution notes (optional)',
                alignLabelWithHint: true,
              ),
            ),
            const SizedBox(height: 16),
            SizedBox(
              width: double.infinity,
              child: FilledButton.icon(
                onPressed: _busy ? null : _save,
                icon: _busy
                    ? const SizedBox(
                        width: 18,
                        height: 18,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    : const Icon(Icons.check_rounded),
                label: Text(_busy ? 'Saving…' : 'Save'),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
