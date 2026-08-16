import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../models/directory_person.dart';
import '../../providers/app_providers.dart';
import '../../widgets/states.dart';

final employeesProvider =
    FutureProvider.autoDispose.family<List<DirectoryPerson>, String?>(
  (ref, query) =>
      ref.watch(workRepositoryProvider).directory(query: query, size: 500),
);

/// The company directory: every active person, searchable.
///
/// The portal's Employees page on a phone. The web page shows the same rows —
/// search box, then the paged table — with management actions for those who can
/// manage; on a phone the rows become cards and the actions stay out of the way
/// until a person is opened.
class EmployeesScreen extends ConsumerWidget {
  const EmployeesScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return const _EmployeesBody();
  }
}

class _EmployeesBody extends ConsumerStatefulWidget {
  const _EmployeesBody();

  @override
  ConsumerState<_EmployeesBody> createState() => _EmployeesBodyState();
}

class _EmployeesBodyState extends ConsumerState<_EmployeesBody> {
  final _search = TextEditingController();
  String? _query;
  Timer? _debounce;

  @override
  void dispose() {
    _debounce?.cancel();
    _search.dispose();
    super.dispose();
  }

  void _onSearch(String value) {
    _debounce?.cancel();
    _debounce = Timer(const Duration(milliseconds: 300), () {
      if (!mounted) return;
      setState(() => _query = value.trim().isEmpty ? null : value.trim());
    });
  }

  @override
  Widget build(BuildContext context) {
    final async = ref.watch(employeesProvider(_query));

    return Scaffold(
      appBar: AppBar(title: const Text('Employees')),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 12, 16, 4),
            child: TextField(
              controller: _search,
              onChanged: _onSearch,
              decoration: InputDecoration(
                hintText: 'Search name, code, designation…',
                prefixIcon: const Icon(Icons.search_rounded),
                suffixIcon: _search.text.isEmpty
                    ? null
                    : IconButton(
                        tooltip: 'Clear',
                        icon: const Icon(Icons.close_rounded),
                        onPressed: () {
                          _search.clear();
                          setState(() => _query = null);
                        },
                      ),
                isDense: true,
              ),
            ),
          ),
          Expanded(
            child: RefreshIndicator(
              onRefresh: () async => ref.invalidate(employeesProvider(_query)),
              child: async.when(
                loading: () => const LoadingList(),
                error: (e, _) => ListView(
                  physics: const AlwaysScrollableScrollPhysics(),
                  children: [
                    const SizedBox(height: 80),
                    ErrorState(
                      message: '$e',
                      onRetry: () => ref.invalidate(employeesProvider(_query)),
                    ),
                  ],
                ),
                data: (people) {
                  if (people.isEmpty) {
                    return ListView(
                      physics: const AlwaysScrollableScrollPhysics(),
                      children: [
                        const SizedBox(height: 80),
                        EmptyState(
                          icon: Icons.person_search_rounded,
                          title: _query == null
                              ? 'No employees found'
                              : 'Nothing matches “$_query”',
                          description: _query == null
                              ? 'Active employees will appear here.'
                              : 'Try a different name, code or designation.',
                        ),
                      ],
                    );
                  }
                  return ListView.builder(
                    physics: const AlwaysScrollableScrollPhysics(),
                    padding: const EdgeInsets.fromLTRB(16, 8, 16, 24),
                    itemCount: people.length,
                    itemBuilder: (context, i) {
                      final p = people[i];
                      return _EmployeeCard(person: p)
                          .animate()
                          .fadeIn(delay: (i.clamp(0, 10) * 30).ms, duration: 200.ms)
                          .slideY(begin: 0.05, end: 0, curve: Curves.easeOutCubic);
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

class _EmployeeCard extends StatelessWidget {
  const _EmployeeCard({required this.person});

  final DirectoryPerson person;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Card(
      margin: const EdgeInsets.only(bottom: 10),
      child: ListTile(
        contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
        leading: CircleAvatar(
          backgroundColor: scheme.primary.withValues(alpha: 0.12),
          child: Text(
            person.initials,
            style: TextStyle(
              color: scheme.primary,
              fontWeight: FontWeight.w700,
              fontSize: 14,
            ),
          ),
        ),
        title: Text(person.name, style: const TextStyle(fontWeight: FontWeight.w600)),
        subtitle: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            if (person.designationTitle != null) Text(person.designationTitle!),
            Text(
              [
                if (person.employeeCode != null) person.employeeCode!,
                if (person.email != null) person.email!,
              ].join(' · '),
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: TextStyle(fontSize: 12, color: scheme.onSurfaceVariant),
            ),
          ],
        ),
        isThreeLine: person.designationTitle != null,
        trailing: const Icon(Icons.chevron_right_rounded),
        onTap: () => _open(context),
      ),
    );
  }

  void _open(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    showModalBottomSheet<void>(
      context: context,
      showDragHandle: true,
      builder: (ctx) => SafeArea(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(20, 0, 20, 24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  CircleAvatar(
                    radius: 28,
                    backgroundColor: scheme.primary.withValues(alpha: 0.12),
                    child: Text(
                      person.initials,
                      style: TextStyle(
                        color: scheme.primary,
                        fontWeight: FontWeight.w700,
                        fontSize: 18,
                      ),
                    ),
                  ),
                  const SizedBox(width: 14),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          person.name,
                          style: Theme.of(context).textTheme.titleMedium,
                        ),
                        if (person.designationTitle != null)
                          Text(
                            person.designationTitle!,
                            style: TextStyle(color: scheme.onSurfaceVariant),
                          ),
                      ],
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 20),
              if (person.employeeCode != null)
                _InfoRow(icon: Icons.badge_outlined, value: person.employeeCode!),
              if (person.email != null)
                _InfoRow(icon: Icons.email_outlined, value: person.email!),
              if (person.phone != null)
                _InfoRow(icon: Icons.phone_outlined, value: person.phone!),
              if (person.roles.isNotEmpty)
                _InfoRow(icon: Icons.workspace_premium_outlined, value: person.roles.join(', ')),
            ],
          ),
        ),
      ),
    );
  }
}

class _InfoRow extends StatelessWidget {
  const _InfoRow({required this.icon, required this.value});

  final IconData icon;
  final String value;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 5),
      child: Row(
        children: [
          Icon(icon, size: 18, color: scheme.onSurfaceVariant),
          const SizedBox(width: 12),
          Expanded(child: Text(value)),
        ],
      ),
    );
  }
}
