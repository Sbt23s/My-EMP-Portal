import 'package:flutter/material.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import '../../providers/app_providers.dart';
import '../../widgets/states.dart';

final auditLogProvider = FutureProvider.autoDispose<List<Map<String, dynamic>>>(
  (ref) => ref.watch(workRepositoryProvider).auditLog(size: 100),
);
final auditSummaryProvider =
    FutureProvider.autoDispose<Map<String, dynamic>>(
  (ref) async {
    final data = await ref.watch(workRepositoryProvider).auditSummary();
    return data is Map<String, dynamic> ? data : const {};
  },
);

/// The security trail: who did what, when, from where.
///
/// The portal's Audit Log page on a phone — for HR and admins, the same people
/// the web route lets in. The category counts sit on top, and each row expands
/// to the detail (request path, IP, device) the web table shows in columns.
class AuditScreen extends ConsumerWidget {
  const AuditScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(auditLogProvider);
    final summary = ref.watch(auditSummaryProvider);

    return Scaffold(
      appBar: AppBar(title: const Text('Audit log')),
      body: RefreshIndicator(
        onRefresh: () async {
          ref.invalidate(auditLogProvider);
          ref.invalidate(auditSummaryProvider);
        },
        child: async.when(
          loading: () => const LoadingList(),
          error: (e, _) => ListView(
            physics: const AlwaysScrollableScrollPhysics(),
            children: [
              const SizedBox(height: 80),
              ErrorState(
                message: '$e',
                onRetry: () => ref.invalidate(auditLogProvider),
              ),
            ],
          ),
          data: (rows) {
            if (rows.isEmpty) {
              return ListView(
                physics: const AlwaysScrollableScrollPhysics(),
                children: const [
                  SizedBox(height: 80),
                  EmptyState(
                    icon: Icons.history_rounded,
                    title: 'No audit entries yet',
                    description: 'Actions across the portal will be logged here.',
                  ),
                ],
              );
            }
            return ListView(
              physics: const AlwaysScrollableScrollPhysics(),
              padding: const EdgeInsets.fromLTRB(16, 16, 16, 24),
              children: [
                _SummaryStrip(summary: summary),
                const SizedBox(height: 16),
                for (var i = 0; i < rows.length; i++)
                  _AuditRow(entry: rows[i])
                      .animate()
                      .fadeIn(delay: (i.clamp(0, 10) * 30).ms, duration: 200.ms),
              ],
            );
          },
        ),
      ),
    );
  }
}

class _SummaryStrip extends StatelessWidget {
  const _SummaryStrip({required this.summary});

  final AsyncValue<Map<String, dynamic>> summary;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final total = summary.value?['total'];
    final refused = summary.value?['refused'];
    final categories = summary.value?['categories'];

    return Row(
      children: [
        _SummaryTile(
          icon: Icons.bolt_rounded,
          label: 'Actions',
          value: total == null ? '—' : '$total',
          color: scheme.primary,
        ),
        const SizedBox(width: 10),
        _SummaryTile(
          icon: Icons.gpp_bad_outlined,
          label: 'Refused',
          value: refused == null ? '—' : '$refused',
          color: AppTheme.danger(context),
        ),
        const SizedBox(width: 10),
        Expanded(
          child: _SummaryTile(
            icon: Icons.category_outlined,
            label: 'Categories',
            value: categories is List ? '${categories.length}' : '—',
            color: scheme.tertiary,
          ),
        ),
      ],
    );
  }
}

class _SummaryTile extends StatelessWidget {
  const _SummaryTile({
    required this.icon,
    required this.label,
    required this.value,
    required this.color,
  });

  final IconData icon;
  final String label;
  final String value;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return Expanded(
      child: Card(
        margin: EdgeInsets.zero,
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 12),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Icon(icon, size: 18, color: color),
              const SizedBox(height: 6),
              Text(
                value,
                style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 18),
              ),
              Text(
                label,
                style: TextStyle(
                  fontSize: 11,
                  color: Theme.of(context).colorScheme.onSurfaceVariant,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _AuditRow extends StatefulWidget {
  const _AuditRow({required this.entry});

  final Map<String, dynamic> entry;

  @override
  State<_AuditRow> createState() => _AuditRowState();
}

class _AuditRowState extends State<_AuditRow> {
  bool _open = false;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final category = widget.entry['category']?.toString() ?? 'SYSTEM';
    final action = widget.entry['action']?.toString() ?? '—';
    final name = widget.entry['name']?.toString() ?? widget.entry['username']?.toString();
    final at = DateTime.tryParse('${widget.entry['at']}');
    final atLabel = at == null ? '—' : DateFormat('d MMM, h:mm a').format(at);
    final succeeded = widget.entry['succeeded'] != false;
    final (tint, icon) = _categoryStyle(context, category);

    return Card(
      margin: const EdgeInsets.only(bottom: 8),
      child: InkWell(
        borderRadius: BorderRadius.circular(12),
        onTap: () => setState(() => _open = !_open),
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Container(
                    padding: const EdgeInsets.all(8),
                    decoration: BoxDecoration(
                      color: tint.withValues(alpha: 0.12),
                      borderRadius: BorderRadius.circular(10),
                    ),
                    child: Icon(icon, size: 18, color: tint),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          action,
                          style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 14),
                        ),
                        Text(
                          [
                            category,
                            if (name != null) name,
                          ].join(' · '),
                          style: TextStyle(
                            fontSize: 12,
                            color: scheme.onSurfaceVariant,
                          ),
                        ),
                      ],
                    ),
                  ),
                  Text(
                    atLabel,
                    style: TextStyle(fontSize: 11, color: scheme.onSurfaceVariant),
                  ),
                  const SizedBox(width: 4),
                  Icon(
                    succeeded
                        ? Icons.check_circle_rounded
                        : Icons.error_rounded,
                    size: 16,
                    color: succeeded ? AppTheme.success(context) : AppTheme.danger(context),
                  ),
                ],
              ),
              if (_open) ...[
                const Divider(height: 16),
                if (widget.entry['summary'] != null)
                  _Detail(label: 'Summary', value: widget.entry['summary'].toString()),
                if (widget.entry['detail'] != null)
                  _Detail(label: 'Detail', value: widget.entry['detail'].toString()),
                if (widget.entry['path'] != null)
                  _Detail(label: 'Path', value: widget.entry['path'].toString()),
                if (widget.entry['ipAddress'] != null)
                  _Detail(label: 'IP', value: widget.entry['ipAddress'].toString()),
                if (widget.entry['device'] != null)
                  _Detail(label: 'Device', value: widget.entry['device'].toString()),
              ],
            ],
          ),
        ),
      ),
    );
  }

  static (Color, IconData) _categoryStyle(BuildContext context, String category) {
    final scheme = Theme.of(context).colorScheme;
    return switch (category.toUpperCase()) {
      'PAYROLL' => (AppTheme.success(context), Icons.wallet_rounded),
      'EMPLOYEE' => (scheme.primary, Icons.people_outline_rounded),
      'ATTENDANCE' => (scheme.tertiary, Icons.timer_outlined),
      'LEAVE' => (AppTheme.warning(context), Icons.calendar_month_rounded),
      'FACE' => (scheme.secondary, Icons.face_rounded),
      'CHAT' => (AppTheme.success(context), Icons.forum_outlined),
      'SECURITY' => (AppTheme.danger(context), Icons.lock_outline_rounded),
      _ => (scheme.onSurfaceVariant, Icons.settings_outlined),
    };
  }
}

class _Detail extends StatelessWidget {
  const _Detail({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 2),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 64,
            child: Text(
              label,
              style: TextStyle(fontSize: 12, color: scheme.onSurfaceVariant),
            ),
          ),
          Expanded(
            child: Text(
              value,
              style: const TextStyle(fontSize: 12),
              maxLines: 3,
              overflow: TextOverflow.ellipsis,
            ),
          ),
        ],
      ),
    );
  }
}
