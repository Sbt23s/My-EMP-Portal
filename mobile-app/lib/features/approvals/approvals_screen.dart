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
/// Permission requests addressed to this approver.
final permissionQueueProvider =
    FutureProvider.autoDispose<List<PermissionRequestItem>>(
  (ref) => ref.watch(workRepositoryProvider).permissionsForMe(),
);

class ApprovalsScreen extends ConsumerWidget {
  const ApprovalsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    /*
      Three tabs for three lists.

      The controller was built for two while the view held three, so the
      permission queue -- written, wired and asking the server for the right
      rows -- could not be reached at all: a Team Leader had no way to decide
      a permission from the phone.
    */
    return DefaultTabController(
      length: 3,
      child: Scaffold(
        appBar: AppBar(
          title: const Text('Approvals'),
          bottom: const TabBar(
            isScrollable: true,
            tabAlignment: TabAlignment.start,
            tabs: [
              Tab(text: 'Leave requests'),
              Tab(text: 'Permissions'),
              Tab(text: 'Team today'),
            ],
          ),
        ),
        body: const TabBarView(
          children: [_LeaveQueue(), _PermissionQueue(), _TeamToday()],
        ),
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

/// Short permissions waiting on this approver.
///
/// A separate queue from leave, because they are separate on the server: a
/// different endpoint, a different decision call, and a different thing being
/// asked for. Team leads and HR could approve leave from the phone but had to
/// find a browser to approve an hour off, which is the request that is most
/// often urgent.
class _PermissionQueue extends ConsumerWidget {
  const _PermissionQueue();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(permissionQueueProvider);

    return RefreshIndicator(
      onRefresh: () async => ref.invalidate(permissionQueueProvider),
      child: async.when(
        loading: () => const LoadingList(),
        error: (e, _) => ListView(
          physics: const AlwaysScrollableScrollPhysics(),
          children: [
            const SizedBox(height: 80),
            ErrorState(
              message: '$e',
              onRetry: () => ref.invalidate(permissionQueueProvider),
            ),
          ],
        ),
        data: (all) {
          // Pending first and only — a decided request belongs in a history,
          // and a queue that keeps them makes the one waiting harder to find.
          final pending = all.where((p) => p.isPending).toList();
          if (pending.isEmpty) {
            return ListView(
              physics: const AlwaysScrollableScrollPhysics(),
              children: const [
                SizedBox(height: 80),
                EmptyState(
                  icon: Icons.schedule_rounded,
                  title: 'Nothing waiting',
                  description: 'Permission requests sent to you appear here.',
                ),
              ],
            );
          }

          return ListView.builder(
            physics: const AlwaysScrollableScrollPhysics(),
            padding: const EdgeInsets.all(16),
            itemCount: pending.length,
            itemBuilder: (context, i) =>
                _PermissionCard(item: pending[i]),
          );
        },
      ),
    );
  }
}

class _PermissionCard extends ConsumerStatefulWidget {
  const _PermissionCard({required this.item});
  final PermissionRequestItem item;

  @override
  ConsumerState<_PermissionCard> createState() => _PermissionCardState();
}

class _PermissionCardState extends ConsumerState<_PermissionCard> {
  bool _busy = false;

  Future<void> _decide(bool approve) async {
    if (_busy) return;

    String? comment;
    if (!approve) {
      // A rejection with no reason is the one people come back to ask about.
      comment = await showDialog<String>(
        context: context,
        builder: (c) {
          final controller = TextEditingController();
          return AlertDialog(
            title: const Text('Why is it refused?'),
            content: TextField(
              controller: controller,
              autofocus: true,
              maxLines: 3,
              decoration: const InputDecoration(hintText: 'A short reason'),
            ),
            actions: [
              TextButton(
                onPressed: () => Navigator.of(c).pop(),
                child: const Text('Cancel'),
              ),
              FilledButton(
                onPressed: () => Navigator.of(c).pop(controller.text),
                child: const Text('Refuse'),
              ),
            ],
          );
        },
      );
      if (comment == null) return; // cancelled
    }

    setState(() => _busy = true);
    try {
      await ref.read(workRepositoryProvider).decidePermission(
            widget.item.id,
            approve: approve,
            comment: comment,
          );
      ref.invalidate(permissionQueueProvider);
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
    final scheme = Theme.of(context).colorScheme;
    final p = widget.item;

    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: Card(
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                p.employeeName ?? 'Employee',
                style: const TextStyle(fontWeight: FontWeight.w700),
              ),
              const SizedBox(height: 4),
              Text(
                '${DateFormat('EEE, d MMM').format(p.requestDate)}  ·  '
                '${p.fromTime}–${p.toTime}'
                '${p.hours == null ? '' : '  ·  ${p.hours}h'}',
                style: TextStyle(color: scheme.onSurfaceVariant, fontSize: 13),
              ),
              if (p.reason != null) ...[
                const SizedBox(height: 8),
                Text(p.reason!, style: const TextStyle(height: 1.35)),
              ],
              const SizedBox(height: 12),
              if (_busy)
                const Center(
                  child: SizedBox(
                    height: 20,
                    width: 20,
                    child: CircularProgressIndicator(strokeWidth: 2.2),
                  ),
                )
              else
                Row(
                  children: [
                    Expanded(
                      child: OutlinedButton(
                        onPressed: () => _decide(false),
                        style: OutlinedButton.styleFrom(
                          foregroundColor: scheme.error,
                        ),
                        child: const Text('Refuse'),
                      ),
                    ),
                    const SizedBox(width: 10),
                    Expanded(
                      child: FilledButton(
                        onPressed: () => _decide(true),
                        child: const Text('Approve'),
                      ),
                    ),
                  ],
                ),
            ],
          ),
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
