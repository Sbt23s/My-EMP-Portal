import 'package:flutter/material.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../models/directory_person.dart';
import '../../providers/app_providers.dart';
import '../../widgets/states.dart';

final directoryProvider = FutureProvider.autoDispose<List<DirectoryPerson>>(
  (ref) => ref.watch(workRepositoryProvider).directory(),
);

/// Colleagues: who they are and how to reach them.
///
/// The web Employees page is a management screen — it creates accounts, edits
/// bank details and offboards people. None of that is here. On a phone the
/// question is "what is Priya's number", and a screen that answers only that
/// cannot accidentally offboard somebody with a mis-tap.
///
/// Filtered in the browser rather than by asking the server on every keystroke:
/// the list is a few hundred rows already in memory, and a request per letter on
/// a phone connection is slower than the filtering it replaces.
class DirectoryScreen extends ConsumerStatefulWidget {
  const DirectoryScreen({super.key});

  @override
  ConsumerState<DirectoryScreen> createState() => _DirectoryScreenState();
}

class _DirectoryScreenState extends ConsumerState<DirectoryScreen> {
  final _search = TextEditingController();
  String _query = '';

  @override
  void dispose() {
    _search.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final async = ref.watch(directoryProvider);

    return Scaffold(
      appBar: AppBar(title: const Text('Directory')),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 12, 16, 8),
            child: TextField(
              controller: _search,
              onChanged: (v) => setState(() => _query = v.trim().toLowerCase()),
              textInputAction: TextInputAction.search,
              decoration: InputDecoration(
                hintText: 'Search name, code or team',
                prefixIcon: const Icon(Icons.search_rounded),
                suffixIcon: _query.isEmpty
                    ? null
                    : IconButton(
                        icon: const Icon(Icons.close_rounded),
                        tooltip: 'Clear',
                        onPressed: () {
                          _search.clear();
                          setState(() => _query = '');
                        },
                      ),
              ),
            ),
          ),
          Expanded(
            child: RefreshIndicator(
              onRefresh: () async => ref.invalidate(directoryProvider),
              child: async.when(
                loading: () => const LoadingList(),
                error: (e, _) => ListView(
                  physics: const AlwaysScrollableScrollPhysics(),
                  children: [
                    const SizedBox(height: 60),
                    ErrorState(
                      message: '$e',
                      onRetry: () => ref.invalidate(directoryProvider),
                    ),
                  ],
                ),
                data: (people) {
                  final matches = _query.isEmpty
                      ? people
                      : people.where((p) {
                          return p.name.toLowerCase().contains(_query) ||
                              (p.employeeCode ?? '').toLowerCase().contains(_query) ||
                              p.team.toLowerCase().contains(_query) ||
                              (p.email ?? '').toLowerCase().contains(_query);
                        }).toList();

                  if (matches.isEmpty) {
                    return ListView(
                      physics: const AlwaysScrollableScrollPhysics(),
                      children: [
                        const SizedBox(height: 60),
                        EmptyState(
                          icon: Icons.person_search_outlined,
                          title: _query.isEmpty
                              ? 'Nobody to show'
                              : 'No one matches "$_query"',
                          description: _query.isEmpty
                              ? 'The directory came back empty.'
                              : 'Try a name, an employee code or a team.',
                        ),
                      ],
                    );
                  }

                  return ListView.separated(
                    physics: const AlwaysScrollableScrollPhysics(),
                    padding: const EdgeInsets.fromLTRB(16, 4, 16, 24),
                    itemCount: matches.length,
                    separatorBuilder: (_, __) => const SizedBox(height: 8),
                    itemBuilder: (context, i) {
                      final row = _PersonCard(person: matches[i]);
                      // Only the first handful animate. Staggering three hundred
                      // rows would mean the last one arriving twelve seconds
                      // after the first.
                      if (i > 8) return row;
                      return row
                          .animate()
                          .fadeIn(delay: (i * 35).ms, duration: 200.ms)
                          .slideY(begin: 0.06, end: 0, curve: Curves.easeOutCubic);
                    },
                  );
                },
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _PersonCard extends StatelessWidget {
  const _PersonCard({required this.person});

  final DirectoryPerson person;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Card(
      child: ListTile(
        onTap: () => _showDetail(context, person),
        leading: CircleAvatar(
          backgroundColor: scheme.primary.withValues(alpha: 0.14),
          child: Text(
            person.initials,
            style: TextStyle(
              color: scheme.primary,
              fontWeight: FontWeight.w700,
              fontSize: 13,
            ),
          ),
        ),
        title: Text(
          person.name,
          style: const TextStyle(fontWeight: FontWeight.w600),
        ),
        subtitle: Text(
          [person.employeeCode, person.team].whereType<String>().join(' · '),
        ),
        trailing: const Icon(Icons.chevron_right_rounded),
      ),
    );
  }

  void _showDetail(BuildContext context, DirectoryPerson p) {
    showModalBottomSheet<void>(
      context: context,
      useSafeArea: true,
      builder: (_) => SafeArea(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(20, 20, 20, 28),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                p.name,
                style: Theme.of(context)
                    .textTheme
                    .titleMedium
                    ?.copyWith(fontWeight: FontWeight.w700),
              ),
              const SizedBox(height: 4),
              Text(
                [p.employeeCode, p.team].whereType<String>().join(' · '),
                style: Theme.of(context).textTheme.bodySmall?.copyWith(
                      color: Theme.of(context).colorScheme.onSurfaceVariant,
                    ),
              ),
              const SizedBox(height: 18),
              if (p.email != null)
                _DetailRow(icon: Icons.mail_outline_rounded, value: p.email!),
              if (p.phone != null)
                _DetailRow(icon: Icons.phone_outlined, value: p.phone!),
              if (p.email == null && p.phone == null)
                Text(
                  'No contact details on file.',
                  style: Theme.of(context).textTheme.bodySmall?.copyWith(
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

class _DetailRow extends StatelessWidget {
  const _DetailRow({required this.icon, required this.value});

  final IconData icon;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: Row(
        children: [
          Icon(icon, size: 18, color: Theme.of(context).colorScheme.onSurfaceVariant),
          const SizedBox(width: 12),
          Expanded(child: SelectableText(value)),
        ],
      ),
    );
  }
}
