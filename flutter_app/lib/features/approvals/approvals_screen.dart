import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import '../../models/leave.dart';
import '../../providers/app_providers.dart';
import '../../themes/app_theme.dart';
import '../../widgets/states.dart';

final pendingApprovalsProvider = FutureProvider.autoDispose<List<LeaveRequest>>(
  (ref) => ref.watch(workRepositoryProvider).pendingApprovals(),
);

final teamTodayProvider =
    FutureProvider.autoDispose<List<Map<String, dynamic>>>(
      (ref) => ref.watch(workRepositoryProvider).teamToday(),
    );

/// What a manager needs on a phone: decide leave, and see who is in today.
///
/// Offered only to someone holding LEAVE_APPROVE — the server enforces it too,
/// so hiding the tab is a courtesy rather than the control.
class ApprovalsScreen extends ConsumerWidget {
  const ApprovalsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return DefaultTabController(
      length: 2,
      child: Scaffold(
        appBar: AppBar(
          title: const Text('Approvals'),
          bottom: const TabBar(
            tabs: [
              Tab(text: 'Leave requests'),
              Tab(text: 'Team today'),
            ],
          ),
        ),
        body: const TabBarView(children: [_LeaveQueue(), _TeamToday()]),
      ),
    );
  }
}

class _LeaveQueue extends ConsumerWidget {
  const _LeaveQueue();

  Future<void> _decide(
    BuildContext context,
    WidgetRef ref,
    LeaveRequest request,
    bool approve,
  ) async {
    final commentController = TextEditingController();

    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: Text(approve ? 'Approve this leave?' : 'Reject this leave?'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              '${request.leaveTypeName ?? 'Leave'} · '
              '${DateFormat('d MMM').format(request.fromDate)} – '
              '${DateFormat('d MMM yyyy').format(request.toDate)}',
            ),
            const SizedBox(height: 14),
            TextField(
              controller: commentController,
              maxLines: 2,
              maxLength: 300,
              decoration: InputDecoration(
                labelText: approve ? 'Comment (optional)' : 'Reason',
                alignLabelWithHint: true,
              ),
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext, false),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(dialogContext, true),
            style: approve
                ? null
                : FilledButton.styleFrom(
                    backgroundColor: Theme.of(dialogContext).colorScheme.error,
                  ),
            child: Text(approve ? 'Approve' : 'Reject'),
          ),
        ],
      ),
    );

    final comment = commentController.text;
    commentController.dispose();
    if (confirmed != true || !context.mounted) return;

    try {
      await ref
          .read(workRepositoryProvider)
          .decideLeave(request.id, approve: approve, comment: comment);
      // Reloaded from the server rather than removed locally: if the decision
      // was refused, the row must stay in the queue.
      ref.invalidate(pendingApprovalsProvider);
      if (!context.mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(approve ? 'Leave approved' : 'Leave rejected')),
      );
    } catch (e) {
      if (!context.mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(e.toString()),
          backgroundColor: Theme.of(context).colorScheme.error,
        ),
      );
    }
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(pendingApprovalsProvider);

    return RefreshIndicator(
      onRefresh: () async {
        ref.invalidate(pendingApprovalsProvider);
        await ref.read(pendingApprovalsProvider.future);
      },
      child: async.when(
        loading: () => const LoadingList(itemHeight: 108),
        error: (e, _) => ListView(
          physics: const AlwaysScrollableScrollPhysics(),
          children: [
            SizedBox(
              height: MediaQuery.sizeOf(context).height * 0.6,
              child: ErrorState(
                message: e.toString(),
                onRetry: () => ref.invalidate(pendingApprovalsProvider),
              ),
            ),
          ],
        ),
        data: (items) => items.isEmpty
            ? ListView(
                physics: const AlwaysScrollableScrollPhysics(),
                children: [
                  SizedBox(
                    height: MediaQuery.sizeOf(context).height * 0.6,
                    child: const EmptyState(
                      icon: Icons.inbox_rounded,
                      title: 'Nothing waiting',
                      description: 'Requests sent to you will appear here.',
                    ),
                  ),
                ],
              )
            : ListView.separated(
                physics: const AlwaysScrollableScrollPhysics(),
                padding: const EdgeInsets.all(16),
                itemCount: items.length,
                separatorBuilder: (_, __) => const SizedBox(height: 10),
                itemBuilder: (context, i) {
                  final r = items[i];
                  return Card(
                    child: Padding(
                      padding: const EdgeInsets.all(14),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            r.leaveTypeName ?? 'Leave',
                            style: const TextStyle(fontWeight: FontWeight.w700),
                          ),
                          const SizedBox(height: 3),
                          Text(
                            '${DateFormat('d MMM').format(r.fromDate)} – '
                            '${DateFormat('d MMM yyyy').format(r.toDate)}'
                            '${r.workingDays != null ? ' · ${r.workingDays} day(s)' : ''}',
                            style: Theme.of(context).textTheme.bodySmall,
                          ),
                          if (r.reason?.isNotEmpty == true) ...[
                            const SizedBox(height: 6),
                            Text(
                              r.reason!,
                              style: Theme.of(context).textTheme.bodySmall
                                  ?.copyWith(
                                    color: Theme.of(
                                      context,
                                    ).colorScheme.onSurfaceVariant,
                                  ),
                            ),
                          ],
                          const SizedBox(height: 12),
                          Row(
                            children: [
                              Expanded(
                                child: OutlinedButton(
                                  onPressed: () =>
                                      _decide(context, ref, r, false),
                                  style: OutlinedButton.styleFrom(
                                    foregroundColor: AppTheme.danger(context),
                                    minimumSize: const Size.fromHeight(40),
                                  ),
                                  child: const Text('Reject'),
                                ),
                              ),
                              const SizedBox(width: 10),
                              Expanded(
                                child: FilledButton(
                                  onPressed: () =>
                                      _decide(context, ref, r, true),
                                  style: FilledButton.styleFrom(
                                    minimumSize: const Size.fromHeight(40),
                                  ),
                                  child: const Text('Approve'),
                                ),
                              ),
                            ],
                          ),
                        ],
                      ),
                    ),
                  );
                },
              ),
      ),
    );
  }
}

class _TeamToday extends ConsumerWidget {
  const _TeamToday();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(teamTodayProvider);
    final scheme = Theme.of(context).colorScheme;

    return RefreshIndicator(
      onRefresh: () async {
        ref.invalidate(teamTodayProvider);
        await ref.read(teamTodayProvider.future);
      },
      child: async.when(
        loading: () => const LoadingList(itemHeight: 64),
        error: (e, _) => ListView(
          physics: const AlwaysScrollableScrollPhysics(),
          children: [
            SizedBox(
              height: MediaQuery.sizeOf(context).height * 0.6,
              child: ErrorState(
                message: e.toString(),
                onRetry: () => ref.invalidate(teamTodayProvider),
              ),
            ),
          ],
        ),
        data: (rows) => rows.isEmpty
            ? ListView(
                physics: const AlwaysScrollableScrollPhysics(),
                children: [
                  SizedBox(
                    height: MediaQuery.sizeOf(context).height * 0.6,
                    child: const EmptyState(
                      icon: Icons.groups_outlined,
                      title: 'No team members',
                      description: 'Nobody reports to you yet.',
                    ),
                  ),
                ],
              )
            : ListView.separated(
                physics: const AlwaysScrollableScrollPhysics(),
                padding: const EdgeInsets.all(16),
                itemCount: rows.length,
                separatorBuilder: (_, __) => const SizedBox(height: 8),
                itemBuilder: (context, i) {
                  final row = rows[i];
                  final name = row['name']?.toString() ?? 'Employee';
                  final punchedIn =
                      row['punchedIn'] as bool? ??
                      row['present'] as bool? ??
                      row['punchInAt'] != null;
                  final at = DateTime.tryParse(
                    row['punchInAt']?.toString() ?? '',
                  );
                  final colour = punchedIn
                      ? AppTheme.success(context)
                      : scheme.onSurfaceVariant;

                  return Card(
                    child: ListTile(
                      leading: CircleAvatar(
                        radius: 18,
                        backgroundColor: colour.withValues(alpha: 0.14),
                        child: Icon(
                          punchedIn
                              ? Icons.check_rounded
                              : Icons.remove_rounded,
                          size: 18,
                          color: colour,
                        ),
                      ),
                      title: Text(
                        name,
                        style: const TextStyle(fontWeight: FontWeight.w600),
                      ),
                      subtitle: Text(
                        punchedIn
                            ? (at != null
                                  ? 'In at ${DateFormat('h:mm a').format(at)}'
                                  : 'Present')
                            : 'Not punched in',
                      ),
                      trailing: Text(
                        row['employeeCode']?.toString() ?? '',
                        style: TextStyle(
                          fontSize: 11,
                          color: scheme.onSurfaceVariant,
                        ),
                      ),
                    ),
                  );
                },
              ),
      ),
    );
  }
}
