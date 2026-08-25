import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

import '../../models/work_items.dart';
import '../../themes/app_theme.dart';

/// One expense claim, and how its total was arrived at.
///
/// The list showed a place, a date and a figure. That is enough to recognise a
/// claim and not enough to check one: the question people actually have is
/// which part of it made up the amount, and — when a claim comes back
/// rejected — what the reason was. Both were only on the website.
class ClaimDetailSheet extends StatelessWidget {
  const ClaimDetailSheet({super.key, required this.claim});

  final ExpenseClaim claim;

  @override
  Widget build(BuildContext context) {
    final c = claim;
    final scheme = Theme.of(context).colorScheme;
    final money = NumberFormat.currency(
      locale: 'en_IN',
      symbol: '₹',
      decimalDigits: 2,
    );

    final statusColour = c.isApproved
        ? AppTheme.success(context)
        : c.isRejected
            ? scheme.error
            : AppTheme.warning(context);

    /// A line of the breakdown. Null amounts are skipped by the caller rather
    /// than rendered as zero — nothing claimed and nothing spent read
    /// differently to somebody checking a figure.
    Widget line(String label, String value, {bool strong = false}) => Padding(
          padding: const EdgeInsets.symmetric(vertical: 5),
          child: Row(
            children: [
              Expanded(
                child: Text(
                  label,
                  style: TextStyle(
                    fontSize: 13,
                    color: strong ? null : scheme.outline,
                    fontWeight: strong ? FontWeight.w700 : FontWeight.w400,
                  ),
                ),
              ),
              Text(
                value,
                style: TextStyle(
                  fontSize: 13.5,
                  fontWeight: strong ? FontWeight.w700 : FontWeight.w600,
                  fontFeatures: const [FontFeature.tabularFigures()],
                ),
              ),
            ],
          ),
        );

    return DraggableScrollableSheet(
      expand: false,
      initialChildSize: 0.7,
      maxChildSize: 0.95,
      minChildSize: 0.4,
      builder: (context, controller) => ListView(
        controller: controller,
        padding: const EdgeInsets.fromLTRB(20, 12, 20, 28),
        children: [
          Center(
            child: Container(
              width: 40,
              height: 4,
              decoration: BoxDecoration(
                color: scheme.outlineVariant,
                borderRadius: BorderRadius.circular(2),
              ),
            ),
          ),
          const SizedBox(height: 18),
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(
                child: Text(
                  [c.location, c.category]
                      .where((v) => v != null && v.isNotEmpty)
                      .join(' · '),
                  style: Theme.of(context)
                      .textTheme
                      .titleMedium
                      ?.copyWith(fontWeight: FontWeight.w700),
                ),
              ),
              const SizedBox(width: 8),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                decoration: BoxDecoration(
                  color: statusColour.withValues(alpha: 0.14),
                  borderRadius: BorderRadius.circular(AppTheme.radius),
                ),
                child: Text(
                  c.status,
                  style: TextStyle(
                    fontSize: 11,
                    fontWeight: FontWeight.w700,
                    color: statusColour,
                  ),
                ),
              ),
            ],
          ),
          if (c.date != null) ...[
            const SizedBox(height: 4),
            Text(
              DateFormat('EEEE, d MMMM yyyy').format(c.date!),
              style: TextStyle(fontSize: 12.5, color: scheme.outline),
            ),
          ],

          /*
            The reason, when there is one, immediately under the status.

            A rejected claim is the case where somebody opens this screen at
            all, so the explanation goes above the arithmetic rather than at
            the bottom of it.
          */
          if ((c.decisionComment ?? '').trim().isNotEmpty) ...[
            const SizedBox(height: 14),
            Container(
              width: double.infinity,
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: statusColour.withValues(alpha: 0.08),
                borderRadius: BorderRadius.circular(AppTheme.radius),
                border: Border.all(color: statusColour.withValues(alpha: 0.3)),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    c.isRejected ? 'Why it was rejected' : 'Note from the approver',
                    style: TextStyle(
                      fontSize: 11,
                      fontWeight: FontWeight.w700,
                      color: statusColour,
                    ),
                  ),
                  const SizedBox(height: 4),
                  Text(c.decisionComment!.trim()),
                ],
              ),
            ),
          ],

          const SizedBox(height: 20),
          Text(
            'Distance',
            style: Theme.of(context)
                .textTheme
                .labelLarge
                ?.copyWith(fontWeight: FontWeight.w700),
          ),
          const SizedBox(height: 2),
          if (c.totalKm != null) line('Total', '${c.totalKm} km'),
          if (c.hillsKm != null && c.hillsKm! > 0) line('Hills', '${c.hillsKm} km'),
          if (c.plainsKm != null && c.plainsKm! > 0) line('Plains', '${c.plainsKm} km'),
          if (c.totalKm == null && c.hillsKm == null && c.plainsKm == null)
            Text('No distance recorded.',
                style: TextStyle(fontSize: 12.5, color: scheme.outline)),

          const SizedBox(height: 18),
          Text(
            'Amount',
            style: Theme.of(context)
                .textTheme
                .labelLarge
                ?.copyWith(fontWeight: FontWeight.w700),
          ),
          const SizedBox(height: 2),
          if (c.totalAmount != null) line('Travel', money.format(c.totalAmount)),
          if (c.busFare != null && c.busFare != 0) line('Bus fare', money.format(c.busFare)),
          if (c.others != null && c.others != 0) line('Other', money.format(c.others)),
          const Divider(height: 18),
          line(
            'Claimed',
            money.format(c.grossTotal ?? c.totalAmount ?? 0),
            strong: true,
          ),

          if ((c.remarks ?? '').trim().isNotEmpty) ...[
            const SizedBox(height: 18),
            Text(
              'Remarks',
              style: Theme.of(context)
                  .textTheme
                  .labelLarge
                  ?.copyWith(fontWeight: FontWeight.w700),
            ),
            const SizedBox(height: 4),
            Text(c.remarks!.trim()),
          ],
        ],
      ),
    );
  }
}
