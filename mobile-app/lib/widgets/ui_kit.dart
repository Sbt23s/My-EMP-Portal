import 'package:flutter/material.dart';

/// The shapes the reference design is built from.
///
/// Every screen in this app is one of a handful of things: a card that states
/// something, a tile you tap, a chip that shows a status, a row in a list. They
/// live here so that changing the look changes it everywhere at once, rather
/// than in thirty-five places that drift apart.
///
/// Colour comes from the theme, never from literals in a screen. The company
/// branding feature repaints the scheme at runtime, so a hard-coded indigo
/// would survive a rebrand the rest of the app did not.
class UI {
  const UI._();

  /// The reference uses one radius almost everywhere, and a larger one for the
  /// big feature cards. Two values, not seven.
  static const double radius = 16;
  static const double radiusLarge = 22;
  static const double gap = 14;

  static BorderRadius get br => BorderRadius.circular(radius);
  static BorderRadius get brLarge => BorderRadius.circular(radiusLarge);

  /// The purple gradient the hero cards use, derived from the active scheme so
  /// a branded company gets its own colour rather than the portal's indigo.
  static LinearGradient heroGradient(BuildContext context) {
    final p = Theme.of(context).colorScheme.primary;
    return LinearGradient(
      begin: Alignment.topLeft,
      end: Alignment.bottomRight,
      colors: [p, Color.lerp(p, Colors.black, 0.22)!],
    );
  }

  /// A plain surface card. The border does the separating in light mode; in
  /// dark mode a border on a dark surface reads as a seam, so it lifts the fill
  /// instead.
  static BoxDecoration card(BuildContext context, {double? r}) {
    final t = Theme.of(context);
    final dark = t.brightness == Brightness.dark;
    return BoxDecoration(
      color: t.cardColor,
      borderRadius: BorderRadius.circular(r ?? radius),
      border: dark ? null : Border.all(color: t.dividerColor.withValues(alpha: 0.55)),
      boxShadow: dark
          ? null
          : [
              BoxShadow(
                color: Colors.black.withValues(alpha: 0.04),
                blurRadius: 14,
                offset: const Offset(0, 4),
              ),
            ],
    );
  }
}

/// A section heading with an optional trailing action, as used above every
/// group in the reference ("Quick Actions" / "View all").
class SectionHeader extends StatelessWidget {
  const SectionHeader(this.title, {super.key, this.actionLabel, this.onAction});

  final String title;
  final String? actionLabel;
  final VoidCallback? onAction;

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.fromLTRB(2, 2, 2, 10),
      child: Row(
        children: [
          Expanded(
            child: Text(
              title,
              style: t.textTheme.titleSmall?.copyWith(fontWeight: FontWeight.w700),
            ),
          ),
          if (actionLabel != null)
            GestureDetector(
              onTap: onAction,
              behavior: HitTestBehavior.opaque,
              child: Padding(
                // A bare text button is a small target; this keeps it tappable
                // without changing how it looks.
                padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 6),
                child: Text(
                  actionLabel!,
                  style: t.textTheme.labelMedium?.copyWith(
                    color: t.colorScheme.primary,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ),
            ),
        ],
      ),
    );
  }
}

/// A status chip: the pill that says Approved / Pending / Rejected.
class StatusChip extends StatelessWidget {
  const StatusChip(this.label, {super.key, required this.color});

  final String label;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.13),
        borderRadius: BorderRadius.circular(20),
      ),
      child: Text(
        label,
        style: TextStyle(
          color: color,
          fontSize: 11.5,
          fontWeight: FontWeight.w700,
          letterSpacing: 0.1,
        ),
      ),
    );
  }
}

/// One of the small square shortcuts under "Quick Actions": a tinted icon and
/// a label beneath it.
class QuickAction extends StatelessWidget {
  const QuickAction({
    super.key,
    required this.icon,
    required this.label,
    required this.color,
    this.onTap,
  });

  final IconData icon;
  final String label;
  final Color color;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context);
    return InkWell(
      onTap: onTap,
      borderRadius: UI.br,
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 6),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              height: 46,
              width: 46,
              decoration: BoxDecoration(
                color: color.withValues(alpha: 0.13),
                borderRadius: BorderRadius.circular(14),
              ),
              child: Icon(icon, color: color, size: 22),
            ),
            const SizedBox(height: 7),
            Text(
              label,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              textAlign: TextAlign.center,
              style: t.textTheme.labelSmall?.copyWith(
                fontWeight: FontWeight.w600,
                fontSize: 11,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

/// A small stat: a number over a caption, as in the Overview strip.
class StatCell extends StatelessWidget {
  const StatCell({
    super.key,
    required this.value,
    required this.label,
    required this.color,
  });

  final String value;
  final String label;
  final Color color;

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context);
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        Text(
          value,
          style: t.textTheme.titleLarge?.copyWith(
            fontWeight: FontWeight.w800,
            color: color,
          ),
        ),
        const SizedBox(height: 2),
        Text(
          label,
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
          style: t.textTheme.labelSmall?.copyWith(
            color: t.textTheme.bodySmall?.color,
            fontSize: 11,
          ),
        ),
      ],
    );
  }
}
