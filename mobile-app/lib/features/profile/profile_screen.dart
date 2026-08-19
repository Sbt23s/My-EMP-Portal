import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../providers/app_providers.dart';
import '../../providers/theme_provider.dart';
import '../../widgets/states.dart';

/// Employee profile with all available info: name, username, email, phone,
/// employee code, company, designation, industry, roles, and team.
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
          // Avatar + name card
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
                          horizontal: 12, vertical: 5),
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

          // Contact info card
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
              ],
            ),
          ),
          const SizedBox(height: 12),

          // Company info card
          Card(
            child: Column(
              children: [
                if (user.companyName?.isNotEmpty == true)
                  _Row(
                    icon: Icons.apartment_rounded,
                    label: 'Company',
                    value: user.companyName!,
                  ),
                if (user.designation?.isNotEmpty == true) ...[
                  const Divider(height: 1),
                  _Row(
                    icon: Icons.work_outline_rounded,
                    label: 'Designation',
                    value: user.designation!,
                  ),
                ],
                if (user.industry?.isNotEmpty == true) ...[
                  const Divider(height: 1),
                  _Row(
                    icon: Icons.category_outlined,
                    label: 'Industry',
                    value: user.industry!,
                  ),
                ],
                if (user.team?.isNotEmpty == true) ...[
                  const Divider(height: 1),
                  _Row(
                    icon: Icons.groups_outlined,
                    label: 'Team',
                    value: user.team!,
                  ),
                ],
              ],
            ),
          ),
          const SizedBox(height: 12),

          // Roles card
          Card(
            child: Column(
              children: [
                if (user.roles.isNotEmpty)
                  _Row(
                    icon: Icons.badge_outlined,
                    label: 'Role${user.roles.length > 1 ? 's' : ''}',
                    value: user.roles.join(', '),
                  ),
              ],
            ),
          ),
          const SizedBox(height: 24),

          // Sign out button
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
