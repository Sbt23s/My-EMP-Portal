import 'package:flutter/material.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/error/failures.dart';
import '../../models/directory_person.dart';
import '../../providers/app_providers.dart';
import '../../widgets/states.dart';
import '../../providers/cache.dart';

/// Permissions the server requires before it will return the staff directory.
///
/// `GET /users` is guarded by USER_MANAGE, ATTENDANCE_TEAM or DASHBOARD_EXEC.
/// An ordinary employee holds none of them.
const _directoryPermissions = <String>[
  'USER_MANAGE',
  'ATTENDANCE_TEAM',
  'DASHBOARD_EXEC',
];

final teamsPeopleProvider =
    FutureProvider.autoDispose<List<DirectoryPerson>>((ref) async {
  cacheFor(ref, cacheShort);
  final repo = ref.watch(workRepositoryProvider);
  final user = ref.watch(currentUserProvider);

  /*
   * Ask for what this person is allowed to see.
   *
   * The screen always fetched the whole staff directory, and the tile that
   * opens it is shown to everyone. An employee has none of the permissions
   * that endpoint requires, so opening Teams answered 403 and the screen
   * showed an error -- the page was simply broken for the largest group of
   * users in the company.
   *
   * A team leader, HR or an administrator still gets the full directory and
   * therefore every team. An employee gets their own team from
   * `/users/my-team`, which needs no special permission and returns the same
   * row type, so the grouping below is unchanged.
   */
  final mayReadDirectory =
      user != null && _directoryPermissions.any(user.can);

  if (!mayReadDirectory) {
    return (await repo.myTeam()).members;
  }

  try {
    return await repo.directory(size: 500);
  } on ForbiddenFailure {
    // Belt and braces: if the permission list and the server ever disagree,
    // fall back rather than show an error page for a screen that has
    // something useful to display.
    return (await repo.myTeam()).members;
  }
});

/// Who is on which team.
///
/// Grouped from the directory rather than fetched: the portal has no team table
/// — a "team" is the designation people share, which is how the web page builds
/// its groups too. Deriving it the same way from the same rows means the two
/// cannot disagree about who is on what.
///
/// Groups colleagues by the designation each one carries.
///
/// Reads the staff list itself rather than borrowing the Directory's
/// request at all.
class TeamsScreen extends ConsumerWidget {
  const TeamsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(teamsPeopleProvider);

    return Scaffold(
      appBar: AppBar(title: const Text('Teams')),
      body: RefreshIndicator(
        onRefresh: () async => ref.invalidate(teamsPeopleProvider),
        child: async.when(
          loading: () => const LoadingList(),
          error: (e, _) => ListView(
            physics: const AlwaysScrollableScrollPhysics(),
            children: [
              const SizedBox(height: 60),
              ErrorState(
                message: '$e',
                onRetry: () => ref.invalidate(teamsPeopleProvider),
              ),
            ],
          ),
          data: (people) {
            final teams = _group(people);
            if (teams.isEmpty) {
              return ListView(
                physics: const AlwaysScrollableScrollPhysics(),
                children: const [
                  SizedBox(height: 60),
                  EmptyState(
                    icon: Icons.groups_2_outlined,
                    title: 'No colleagues to show',
                    description: 'Nobody active was returned for your company.',
                  ),
                ],
              );
            }

            final names = teams.keys.toList()
              // Alphabetical, except "No team" last: it is a gap in the data,
              // not a team, and sorting it under N puts it in the middle of the
              // real ones.
              ..sort((a, b) {
                if (a == 'No team') return 1;
                if (b == 'No team') return -1;
                return a.toLowerCase().compareTo(b.toLowerCase());
              });

            return ListView.builder(
              physics: const AlwaysScrollableScrollPhysics(),
              padding: const EdgeInsets.fromLTRB(16, 16, 16, 24),
              itemCount: names.length,
              itemBuilder: (context, i) {
                final tile = _TeamTile(
                  name: names[i],
                  members: teams[names[i]]!,
                );
                if (i > 8) return tile;
                return tile
                    .animate()
                    .fadeIn(delay: (i * 40).ms, duration: 220.ms)
                    .slideY(begin: 0.06, end: 0, curve: Curves.easeOutCubic);
              },
            );
          },
        ),
      ),
    );
  }

  static Map<String, List<DirectoryPerson>> _group(List<DirectoryPerson> people) {
    final out = <String, List<DirectoryPerson>>{};
    for (final p in people) {
      out.putIfAbsent(p.team, () => []).add(p);
    }
    for (final list in out.values) {
      list.sort((a, b) => a.name.toLowerCase().compareTo(b.name.toLowerCase()));
    }
    return out;
  }
}

class _TeamTile extends StatelessWidget {
  const _TeamTile({required this.name, required this.members});

  final String name;
  final List<DirectoryPerson> members;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Padding(
      padding: const EdgeInsets.only(bottom: 10),
      child: Card(
        clipBehavior: Clip.antiAlias,
        child: ExpansionTile(
          // Collapsed by default: a company with a dozen teams would otherwise
          // open as one unbroken list of everybody, which is the Directory.
          shape: const Border(),
          leading: Container(
            height: 38,
            width: 38,
            alignment: Alignment.center,
            decoration: BoxDecoration(
              color: scheme.primary.withValues(alpha: 0.12),
              borderRadius: BorderRadius.circular(10),
            ),
            child: Icon(Icons.groups_outlined, size: 20, color: scheme.primary),
          ),
          title: Text(name, style: const TextStyle(fontWeight: FontWeight.w700)),
          subtitle: Text(
            '${members.length} ${members.length == 1 ? 'person' : 'people'}',
          ),
          children: [
            for (final m in members)
              ListTile(
                dense: true,
                leading: CircleAvatar(
                  radius: 15,
                  backgroundColor: scheme.primary.withValues(alpha: 0.12),
                  child: Text(
                    m.initials,
                    style: TextStyle(
                      fontSize: 11,
                      fontWeight: FontWeight.w700,
                      color: scheme.primary,
                    ),
                  ),
                ),
                title: Text(m.name),
                subtitle: m.employeeCode == null ? null : Text(m.employeeCode!),
              ),
          ],
        ),
      ),
    );
  }
}
