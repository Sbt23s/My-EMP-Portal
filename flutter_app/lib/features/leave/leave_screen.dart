import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import '../../core/network/api_envelope.dart';
import '../../models/leave.dart';
import '../../providers/app_providers.dart';
import '../../themes/app_theme.dart';
import '../../widgets/states.dart';
import 'apply_leave_sheet.dart';

final myLeaveProvider = FutureProvider.autoDispose<Paged<LeaveRequest>>(
  (ref) => ref.watch(workRepositoryProvider).myLeave(),
);

final leaveBalancesProvider = FutureProvider.autoDispose<List<LeaveBalance>>(
  (ref) => ref.watch(workRepositoryProvider).leaveBalances(),
);

class LeaveScreen extends ConsumerWidget {
  const LeaveScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final requests = ref.watch(myLeaveProvider);
    final balances = ref.watch(leaveBalancesProvider);

    return Scaffold(
      appBar: AppBar(title: const Text('Leave')),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () async {
          final applied = await showModalBottomSheet<bool>(
            context: context,
            isScrollControlled: true,
            useSafeArea: true,
            builder: (_) => const ApplyLeaveSheet(),
          );
          if (applied == true) {
            ref
              ..invalidate(myLeaveProvider)
              ..invalidate(leaveBalancesProvider);
          }
        },
        icon: const Icon(Icons.add_rounded),
        label: const Text('Apply'),
      ),
      body: RefreshIndicator(
        onRefresh: () async {
          ref
            ..invalidate(myLeaveProvider)
            ..invalidate(leaveBalancesProvider);
          await ref.read(myLeaveProvider.future);
        },
        child: ListView(
          physics: const AlwaysScrollableScrollPhysics(),
          padding: const EdgeInsets.fromLTRB(16, 16, 16, 96),
          children: [
            balances.maybeWhen(
              data: (list) => list.isEmpty
                  ? const SizedBox.shrink()
                  : SizedBox(
                      height: 108,
                      child: ListView.separated(
                        scrollDirection: Axis.horizontal,
                        itemCount: list.length,
                        separatorBuilder: (_, __) => const SizedBox(width: 12),
                        itemBuilder: (_, i) => SizedBox(
                          width: 150,
                          child: StatCard(
                            label: list[i].leaveTypeName,
                            value: '${list[i].available}',
                            icon: Icons.beach_access_rounded,
                          ),
                        ),
                      ),
                    ),
              orElse: () => const SizedBox.shrink(),
            ),
            const SizedBox(height: 20),
            Text(
              'My requests',
              style: Theme.of(context).textTheme.titleSmall?.copyWith(
                fontWeight: FontWeight.w700,
                letterSpacing: 0.3,
              ),
            ),
            const SizedBox(height: 12),
            requests.when(
              loading: () =>
                  const SizedBox(height: 300, child: LoadingList(itemCount: 4)),
              error: (e, _) => SizedBox(
                height: 300,
                child: ErrorState(
                  message: e.toString(),
                  onRetry: () => ref.invalidate(myLeaveProvider),
                ),
              ),
              data: (page) => page.isEmpty
                  ? const SizedBox(
                      height: 300,
                      child: EmptyState(
                        icon: Icons.event_available_rounded,
                        title: 'No leave requests',
                        description: 'Anything you apply for will appear here.',
                      ),
                    )
                  : Column(
                      children: [
                        for (final r in page.items) ...[
                          _LeaveTile(
                            request: r,
                            onCancel: r.canCancel
                                ? () async {
                                    final confirmed = await showDialog<bool>(
                                      context: context,
                                      builder: (dialogContext) => AlertDialog(
                                        title: const Text(
                                          'Withdraw this request?',
                                        ),
                                        content: Text(
                                          '${r.leaveTypeName ?? 'Leave'} · '
                                          '${DateFormat('d MMM').format(r.fromDate)} – '
                                          '${DateFormat('d MMM').format(r.toDate)}',
                                        ),
                                        actions: [
                                          TextButton(
                                            onPressed: () => Navigator.pop(
                                              dialogContext,
                                              false,
                                            ),
                                            child: const Text('Keep it'),
                                          ),
                                          FilledButton(
                                            onPressed: () => Navigator.pop(
                                              dialogContext,
                                              true,
                                            ),
                                            child: const Text('Withdraw'),
                                          ),
                                        ],
                                      ),
                                    );
                                    if (confirmed != true) return;
                                    try {
                                      await ref
                                          .read(workRepositoryProvider)
                                          .cancelLeave(r.id);
                                      ref
                                        ..invalidate(myLeaveProvider)
                                        ..invalidate(leaveBalancesProvider);
                                    } catch (e) {
                                      if (!context.mounted) return;
                                      ScaffoldMessenger.of(
                                        context,
                                      ).showSnackBar(
                                        SnackBar(
                                          content: Text(e.toString()),
                                          backgroundColor: Theme.of(
                                            context,
                                          ).colorScheme.error,
                                        ),
                                      );
                                    }
                                  }
                                : null,
                          ),
                          const SizedBox(height: 10),
                        ],
                      ],
                    ),
            ),
          ],
        ),
      ),
    );
  }
}

class _LeaveTile extends StatelessWidget {
  const _LeaveTile({required this.request, this.onCancel});

  final LeaveRequest request;
  final VoidCallback? onCancel;

  Color _statusColour(BuildContext context) {
    if (request.isApproved) return AppTheme.success(context);
    if (request.isRejected) return AppTheme.danger(context);
    return AppTheme.warning(context);
  }

  @override
  Widget build(BuildContext context) {
    final colour = _statusColour(context);
    final scheme = Theme.of(context).colorScheme;

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Container(
              width: 4,
              height: 46,
              decoration: BoxDecoration(
                color: colour,
                borderRadius: BorderRadius.circular(2),
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    request.leaveTypeName ?? 'Leave',
                    style: const TextStyle(fontWeight: FontWeight.w600),
                  ),
                  const SizedBox(height: 2),
                  Text(
                    '${DateFormat('d MMM').format(request.fromDate)} – '
                    '${DateFormat('d MMM yyyy').format(request.toDate)}'
                    '${request.workingDays != null ? ' · ${request.workingDays} day(s)' : ''}',
                    style: Theme.of(context).textTheme.bodySmall?.copyWith(
                      color: scheme.onSurfaceVariant,
                    ),
                  ),
                  if (request.reason?.isNotEmpty == true) ...[
                    const SizedBox(height: 4),
                    Text(
                      request.reason!,
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                      style: Theme.of(context).textTheme.bodySmall,
                    ),
                  ],
                ],
              ),
            ),
            const SizedBox(width: 8),
            Column(
              crossAxisAlignment: CrossAxisAlignment.end,
              children: [
                Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 10,
                    vertical: 4,
                  ),
                  decoration: BoxDecoration(
                    color: colour.withValues(alpha: 0.12),
                    borderRadius: BorderRadius.circular(20),
                  ),
                  child: Text(
                    request.status,
                    style: TextStyle(
                      color: colour,
                      fontSize: 11,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                ),
                if (onCancel != null)
                  TextButton(
                    onPressed: onCancel,
                    style: TextButton.styleFrom(
                      padding: const EdgeInsets.symmetric(horizontal: 8),
                      minimumSize: Size.zero,
                      tapTargetSize: MaterialTapTargetSize.shrinkWrap,
                    ),
                    child: const Text(
                      'Withdraw',
                      style: TextStyle(fontSize: 12),
                    ),
                  ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
