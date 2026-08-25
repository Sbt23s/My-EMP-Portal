import 'package:flutter/material.dart';

import 'ui_kit.dart';

/// The greeting strip at the top of the dashboard: who you are, and your face.
///
/// The avatar falls back to initials rather than a placeholder image. Most
/// people here have no photo uploaded, and a generic silhouette repeated down
/// a list says less than the person's own initials.
class GreetingHeader extends StatelessWidget {
  const GreetingHeader({
    super.key,
    required this.greeting,
    required this.name,
    required this.subtitle,
    this.photoUrl,
    this.onAvatarTap,
    this.trailing,
  });

  final String greeting;
  final String name;
  final String subtitle;
  final String? photoUrl;
  final VoidCallback? onAvatarTap;
  final Widget? trailing;

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context);
    return Row(
      children: [
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                greeting,
                style: t.textTheme.bodySmall?.copyWith(fontSize: 12.5),
              ),
              const SizedBox(height: 2),
              Text(
                name,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: t.textTheme.titleMedium?.copyWith(
                  fontWeight: FontWeight.w800,
                ),
              ),
              if (subtitle.isNotEmpty)
                Text(
                  subtitle,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: t.textTheme.labelSmall?.copyWith(fontSize: 11.5),
                ),
            ],
          ),
        ),
        if (trailing != null) trailing!,
        const SizedBox(width: 6),
        GestureDetector(
          onTap: onAvatarTap,
          child: Avatar(name: name, photoUrl: photoUrl, size: 42),
        ),
      ],
    );
  }
}

/// A circular avatar: the photo when there is one, initials when there is not.
class Avatar extends StatelessWidget {
  const Avatar({
    super.key,
    required this.name,
    this.photoUrl,
    this.size = 40,
  });

  final String name;
  final String? photoUrl;
  final double size;

  static String initialsOf(String name) {
    final parts = name.trim().split(RegExp(r'\s+')).where((p) => p.isNotEmpty);
    if (parts.isEmpty) return '?';
    if (parts.length == 1) {
      final p = parts.first;
      return (p.length > 1 ? p.substring(0, 2) : p).toUpperCase();
    }
    return (parts.first[0] + parts.last[0]).toUpperCase();
  }

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final has = photoUrl != null && photoUrl!.trim().isNotEmpty;
    return Container(
      height: size,
      width: size,
      clipBehavior: Clip.antiAlias,
      decoration: BoxDecoration(
        shape: BoxShape.circle,
        color: scheme.primary.withValues(alpha: 0.14),
      ),
      child: has
          ? Image.network(
              photoUrl!,
              fit: BoxFit.cover,
              // A broken photo must not blank the row; initials still identify
              // the person.
              errorBuilder: (_, __, ___) => _initials(scheme),
            )
          : _initials(scheme),
    );
  }

  Widget _initials(ColorScheme scheme) => Center(
        child: Text(
          initialsOf(name),
          style: TextStyle(
            color: scheme.primary,
            fontWeight: FontWeight.w800,
            fontSize: size * 0.36,
          ),
        ),
      );
}

/// The large gradient card the reference puts the clock on.
///
/// It states one fact plainly — the time, and whether you are checked in —
/// and carries the single action that follows from it.
class HeroCard extends StatelessWidget {
  const HeroCard({
    super.key,
    required this.title,
    required this.subtitle,
    this.badge,
    this.action,
    this.onAction,
    this.actionBusy = false,
    this.footer,
  });

  final String title;
  final String subtitle;
  final String? badge;
  final String? action;
  final VoidCallback? onAction;
  final bool actionBusy;
  final Widget? footer;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        gradient: UI.heroGradient(context),
        borderRadius: UI.brLarge,
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(
                child: Text(
                  subtitle,
                  style: const TextStyle(
                    color: Colors.white70,
                    fontSize: 12.5,
                    fontWeight: FontWeight.w500,
                  ),
                ),
              ),
              if (badge != null)
                Container(
                  padding:
                      const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
                  decoration: BoxDecoration(
                    color: Colors.white.withValues(alpha: 0.22),
                    borderRadius: BorderRadius.circular(20),
                  ),
                  child: Text(
                    badge!,
                    style: const TextStyle(
                      color: Colors.white,
                      fontSize: 11,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                ),
            ],
          ),
          const SizedBox(height: 6),
          Text(
            title,
            style: const TextStyle(
              color: Colors.white,
              fontSize: 30,
              fontWeight: FontWeight.w800,
              height: 1.1,
            ),
          ),
          if (action != null) ...[
            const SizedBox(height: 14),
            SizedBox(
              height: 40,
              child: FilledButton(
                onPressed: actionBusy ? null : onAction,
                style: FilledButton.styleFrom(
                  backgroundColor: Colors.white.withValues(alpha: 0.22),
                  foregroundColor: Colors.white,
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(12),
                  ),
                ),
                child: actionBusy
                    ? const SizedBox(
                        height: 18,
                        width: 18,
                        child: CircularProgressIndicator(
                          strokeWidth: 2,
                          color: Colors.white,
                        ),
                      )
                    : Text(
                        action!,
                        style: const TextStyle(fontWeight: FontWeight.w700),
                      ),
              ),
            ),
          ],
          if (footer != null) ...[const SizedBox(height: 12), footer!],
        ],
      ),
    );
  }
}
