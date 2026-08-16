import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../providers/app_providers.dart';
import '../../providers/theme_provider.dart';
import '../../widgets/states.dart';

class ProfileScreen extends ConsumerWidget {
  const ProfileScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final user = ref.watch(currentUserProvider);
    final scheme = Theme.of(context).colorScheme;

    if (user == null) {
      return const Scaffold(
        body: EmptyState(
          icon: Icons.person_off_outlined,
          title: 'Not signed in',
        ),
      );
    }

    return Scaffold(
      appBar: AppBar(
        title: const Text('Profile'),
        actions: [
          // The portal keeps this in its top bar; on a phone the top bar of the
          // page somebody's own settings live on is the equivalent place.
          Builder(
            builder: (context) {
              final mode = ref.watch(themeModeProvider);
              final dark = Theme.of(context).brightness == Brightness.dark;
              return IconButton(
                tooltip: switch (mode) {
                  ThemeMode.system => 'Following your phone',
                  ThemeMode.light => 'Light',
                  ThemeMode.dark => 'Dark',
                },
                onPressed: () => ref
                    .read(themeModeProvider.notifier)
                    .toggle(Theme.of(context).brightness),
                icon: Icon(
                  dark ? Icons.light_mode_outlined : Icons.dark_mode_outlined,
                ),
              );
            },
          ),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Card(
            child: Padding(
              padding: const EdgeInsets.all(20),
              child: Column(
                children: [
                  CircleAvatar(
                    radius: 36,
                    backgroundColor: scheme.primary.withValues(alpha: 0.14),
                    child: Text(
                      user.initials,
                      style: TextStyle(
                        fontSize: 24,
                        fontWeight: FontWeight.w700,
                        color: scheme.primary,
                      ),
                    ),
                  ),
                  const SizedBox(height: 14),
                  Text(
                    user.name,
                    textAlign: TextAlign.center,
                    style: Theme.of(context).textTheme.titleLarge?.copyWith(
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    '@${user.username}',
                    style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                      color: scheme.onSurfaceVariant,
                    ),
                  ),
                  if (user.employeeCode?.isNotEmpty == true) ...[
                    const SizedBox(height: 10),
                    Container(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 12,
                        vertical: 5,
                      ),
                      decoration: BoxDecoration(
                        color: scheme.surfaceContainerHighest,
                        borderRadius: BorderRadius.circular(20),
                      ),
                      child: Text(
                        user.employeeCode!,
                        style: const TextStyle(
                          fontWeight: FontWeight.w600,
                          fontSize: 12,
                        ),
                      ),
                    ),
                  ],
                ],
              ),
            ),
          ),
          const SizedBox(height: 16),
          Card(
            child: Column(
              children: [
                if (user.email?.isNotEmpty == true)
                  _Row(
                    icon: Icons.mail_outline_rounded,
                    label: 'Email',
                    value: user.email!,
                  ),
                if (user.phone?.isNotEmpty == true) ...[
                  const Divider(height: 1),
                  _Row(
                    icon: Icons.phone_outlined,
                    label: 'Phone',
                    value: user.phone!,
                  ),
                ],
                if (user.companyName?.isNotEmpty == true) ...[
                  const Divider(height: 1),
                  _Row(
                    icon: Icons.apartment_rounded,
                    label: 'Company',
                    value: user.companyName!,
                  ),
                ],
                if (user.roles.isNotEmpty) ...[
                  const Divider(height: 1),
                  _Row(
                    icon: Icons.badge_outlined,
                    label: 'Role',
                    value: user.roles.join(', '),
                  ),
                ],
              ],
            ),
          ),
          const SizedBox(height: 24),
          OutlinedButton.icon(
            onPressed: () async {
              final confirmed = await showDialog<bool>(
                context: context,
                builder: (dialogContext) => AlertDialog(
                  title: const Text('Sign out?'),
                  content: const Text(
                    'You will need your password to sign back in.',
                  ),
                  actions: [
                    TextButton(
                      onPressed: () => Navigator.pop(dialogContext, false),
                      child: const Text('Stay'),
                    ),
                    FilledButton(
                      onPressed: () => Navigator.pop(dialogContext, true),
                      child: const Text('Sign out'),
                    ),
                  ],
                ),
              );
              if (confirmed == true) {
                await ref.read(authProvider.notifier).signOut();
              }
            },
            style: OutlinedButton.styleFrom(foregroundColor: scheme.error),
            icon: const Icon(Icons.logout_rounded),
            label: const Text('Sign out'),
          ),
          const SizedBox(height: 24),
        ],
      ),
    );
  }
}

class _Row extends StatelessWidget {
  const _Row({required this.icon, required this.label, required this.value});

  final IconData icon;
  final String label;
  final String value;

  @override
  Widget build(BuildContext context) => ListTile(
    leading: Icon(icon, size: 20),
    title: Text(label, style: Theme.of(context).textTheme.bodySmall),
    subtitle: Text(value, style: const TextStyle(fontWeight: FontWeight.w500)),
  );
}
