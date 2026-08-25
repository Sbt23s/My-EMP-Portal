import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import '../../core/network/api_envelope.dart';
import '../../models/leave.dart';
import '../../providers/app_providers.dart';
import '../../themes/app_theme.dart';
import '../../widgets/states.dart';
import '../../widgets/ui_kit.dart';
import 'apply_leave_sheet.dart';
import '../../providers/cache.dart';

final myLeaveProvider = FutureProvider.autoDispose<Paged<LeaveRequest>>(
  (ref) => ref.watch(workRepositoryProvider).myLeave(),
);

final leaveBalancesProvider = FutureProvider.autoDispose<List<LeaveBalance>>(
  (ref) => ref.watch(workRepositoryProvider).leaveBalances(),
);

/// The leave types this company has, whether or not anybody holds a balance.
final leaveTypesForBalanceProvider = FutureProvider.autoDispose<List<LeaveType>>(
  (ref) {
  cacheFor(ref, cacheLong);
  return ref.watch(workRepositoryProvider).leaveTypes();
});

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
            /*
             * Every leave type, not only the ones with a balance row.
             *
             * This showed /leave/balances alone, and a type nobody has been
             * allocated yet has no row there — so a company that had configured
             * its leave types but not its allocations saw an empty space where
             * the balances belong, and no way to tell that from "you have no
             * leave". The web page merges the two lists for exactly this
             * reason; so does this now.
             */
            const SectionHeader('Balances'),
            _Balances(types: ref.watch(leaveTypesForBalanceProvider), balances: balances),
            const SizedBox(height: 24),
            const SectionHeader('My Leaves'),
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

    return Container(
      margin: const EdgeInsets.only(bottom: 10),
      decoration: UI.card(context),
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

/// One row per leave type: allocated, used, and what is left.
///
/// A bar rather than a bare number because "8" means nothing without the total
/// beside it — the question people actually have is how much of the year's
/// allowance is gone.
class _Balances extends StatelessWidget {
  const _Balances({required this.types, required this.balances});

  final AsyncValue<List<LeaveType>> types;
  final AsyncValue<List<LeaveBalance>> balances;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;

    if (types.isLoading || balances.isLoading) {
      // Bounded. LoadingList is itself a ListView, and this sits inside the
      // page's ListView — a scrollable given unbounded height throws during
      // layout, which is a crash on every visit while the balances load rather
      // than a cosmetic problem.
      return const SizedBox(height: 180, child: LoadingList(itemCount: 3));
    }

    final typeList = types.valueOrNull ?? const <LeaveType>[];
    final balanceList = balances.valueOrNull ?? const <LeaveBalance>[];

    // Keyed by name rather than id: a balance row carries the type's name, and
    // matching on that is what the web does — the ids do not always line up
    // across the two endpoints.
    final byName = {
      for (final b in balanceList) b.leaveTypeName.toLowerCase(): b,
    };

    // Types first, so a type with no allocation still appears; then any balance
    // whose type is not in the list, so nothing is silently dropped.
    final rows = <(String, LeaveBalance?)>[
      for (final t in typeList) (t.name, byName[t.name.toLowerCase()]),
      for (final b in balanceList)
        if (!typeList.any((t) => t.name.toLowerCase() == b.leaveTypeName.toLowerCase()))
          (b.leaveTypeName, b),
    ];

    if (rows.isEmpty) {
      return const SizedBox(
        height: 200,
        child: EmptyState(
          icon: Icons.beach_access_outlined,
          title: 'No leave types set up',
          description: 'Your company has not configured any leave yet.',
        ),
      );
    }

    return Container(
      decoration: UI.card(context),
      clipBehavior: Clip.antiAlias,
      child: Column(
        children: [
          for (var i = 0; i < rows.length; i++) ...[
            if (i > 0) const Divider(height: 1),
            _BalanceRow(name: rows[i].$1, balance: rows[i].$2, scheme: scheme),
          ],
        ],
      ),
    );
  }
}

class _BalanceRow extends StatelessWidget {
  const _BalanceRow({
    required this.name,
    required this.balance,
    required this.scheme,
  });

  final String name;
  final LeaveBalance? balance;
  final ColorScheme scheme;

  @override
  Widget build(BuildContext context) {
    final allocated = balance?.allocated ?? 0;
    final used = balance?.used ?? 0;
    final available = balance?.available ?? 0;
    final hasAllocation = balance != null && allocated > 0;

    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 14, 16, 14),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: Text(
                  name,
                  style: const TextStyle(fontWeight: FontWeight.w600),
                ),
              ),
              if (hasAllocation)
                Text.rich(
                  TextSpan(
                    children: [
                      TextSpan(
                        text: '$available',
                        style: TextStyle(
                          color: scheme.primary,
                          fontWeight: FontWeight.w800,
                          fontSize: 16,
                        ),
                      ),
                      TextSpan(
                        text: ' of $allocated',
                        style: TextStyle(color: scheme.onSurfaceVariant, fontSize: 13),
                      ),
                    ],
                  ),
                )
              else
                // Said plainly. A zero here would read as "you have used it all"
                // when the truth is that nothing was ever allocated.
                Text(
                  'Not allocated',
                  style: TextStyle(color: scheme.onSurfaceVariant, fontSize: 13),
                ),
            ],
          ),
          if (hasAllocation) ...[
            const SizedBox(height: 8),
            ClipRRect(
              borderRadius: BorderRadius.circular(4),
              child: LinearProgressIndicator(
                value: (used / allocated).clamp(0.0, 1.0),
                minHeight: 6,
                backgroundColor: scheme.primary.withValues(alpha: 0.12),
              ),
            ),
            const SizedBox(height: 5),
            Text(
              '$used used',
              style: TextStyle(color: scheme.onSurfaceVariant, fontSize: 11.5),
            ),
          ],
        ],
      ),
    );
  }
}
