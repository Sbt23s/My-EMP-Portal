import 'package:flutter/material.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import '../../models/celebration.dart';
import '../../models/my_team.dart';
import '../../providers/app_providers.dart';
import '../../themes/app_theme.dart';
import '../../widgets/states.dart';
import '../chat/chat_screen.dart';
import '../../providers/cache.dart';

final myTeamProvider = FutureProvider.autoDispose<MyTeam>(
  (ref) {
  cacheFor(ref, cacheShort);
  return ref.watch(workRepositoryProvider).myTeam();
});
final celebrationsProvider = FutureProvider.autoDispose<List<Celebration>>(
  (ref) {
  cacheFor(ref, cacheShort);
  return ref.watch(workRepositoryProvider).celebrations();
});
final onLeaveProvider = FutureProvider.autoDispose<List<dynamic>>(
  (ref) => ref.watch(workRepositoryProvider).onLeaveToday(),
);

/// The signed-in person's team: who is in it, who is celebrating, who is off.
///
/// The portal's My Team page on a phone. The web page assembles four widgets —
/// team roster, celebrations, on-leave, and a team chat rail — from separate
/// endpoints; this screen does the same, stacked for a narrow screen, and the
/// chat rail becomes a button that opens the team's channel.
class MyTeamScreen extends ConsumerWidget {
  const MyTeamScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final team = ref.watch(myTeamProvider);
    final celebrations = ref.watch(celebrationsProvider);
    final onLeave = ref.watch(onLeaveProvider);

    return Scaffold(
      appBar: AppBar(title: const Text('My team')),
      body: RefreshIndicator(
        onRefresh: () async {
          ref.invalidate(myTeamProvider);
          ref.invalidate(celebrationsProvider);
          ref.invalidate(onLeaveProvider);
        },
        child: team.when(
          loading: () => const LoadingList(),
          error: (e, _) => ListView(
            physics: const AlwaysScrollableScrollPhysics(),
            children: [
              const SizedBox(height: 80),
              ErrorState(
                message: '$e',
                onRetry: () => ref.invalidate(myTeamProvider),
              ),
            ],
          ),
          data: (myTeam) {
            final teamIds = myTeam.members.map((m) => m.id).toSet();

            return ListView(
              physics: const AlwaysScrollableScrollPhysics(),
              padding: const EdgeInsets.fromLTRB(16, 16, 16, 24),
              children: [
                _TeamHeader(name: myTeam.teamName, memberCount: myTeam.members.length),
                const SizedBox(height: 16),
                _TeamChatCard(team: myTeam),
                const SizedBox(height: 20),
                _SectionTitle('Celebrations', icon: Icons.celebration_outlined),
                const SizedBox(height: 8),
                _Celebrations(celebrations: celebrations, teamIds: teamIds),
                const SizedBox(height: 20),
                _SectionTitle('On leave today', icon: Icons.event_busy_rounded),
                const SizedBox(height: 8),
                _OnLeave(onLeave: onLeave, teamIds: teamIds),
                const SizedBox(height: 20),
                _SectionTitle('Team members', icon: Icons.people_outline_rounded),
                const SizedBox(height: 8),
                ...myTeam.members.map(
                  (m) => _MemberTile(
                    name: m.name,
                    code: m.employeeCode,
                    designation: m.designationTitle,
                  ),
                ),
              ],
            );
          },
        ),
      ),
    );
  }
}

class _TeamHeader extends StatelessWidget {
  const _TeamHeader({required this.name, required this.memberCount});

  final String name;
  final int memberCount;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Row(
      children: [
        Container(
          padding: const EdgeInsets.all(14),
          decoration: BoxDecoration(
            color: scheme.primary.withValues(alpha: 0.12),
            borderRadius: BorderRadius.circular(14),
          ),
          child: Icon(Icons.groups_2_rounded, color: scheme.primary, size: 28),
        ),
        const SizedBox(width: 14),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(name, style: Theme.of(context).textTheme.titleLarge),
              Text(
                '$memberCount member${memberCount == 1 ? '' : 's'}',
                style: TextStyle(color: scheme.onSurfaceVariant),
              ),
            ],
          ),
        ),
      ],
    );
  }
}

class _TeamChatCard extends ConsumerWidget {
  const _TeamChatCard({required this.team});

  final MyTeam team;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final scheme = Theme.of(context).colorScheme;
    return Card(
      child: ListTile(
        contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
        leading: Container(
          padding: const EdgeInsets.all(10),
          decoration: BoxDecoration(
            color: scheme.primary.withValues(alpha: 0.12),
            borderRadius: BorderRadius.circular(12),
          ),
          child: Icon(Icons.forum_rounded, color: scheme.primary),
        ),
        title: const Text('Team chat', style: TextStyle(fontWeight: FontWeight.w700)),
        subtitle: const Text('One channel for the whole team'),
        trailing: const Icon(Icons.chevron_right_rounded),
        onTap: () async {
          try {
            final channel = await ref.read(workRepositoryProvider).openTeamChat();
            if (!context.mounted) return;
            Navigator.of(context).push(
              MaterialPageRoute<void>(
                builder: (_) => ChatRoomScreen(channel: channel),
              ),
            );
          } catch (e) {
            if (!context.mounted) return;
            ScaffoldMessenger.of(context)
              ..hideCurrentSnackBar()
              ..showSnackBar(SnackBar(content: Text('$e')));
          }
        },
      ),
    );
  }
}

class _SectionTitle extends StatelessWidget {
  const _SectionTitle(this.title, {required this.icon});

  final String title;
  final IconData icon;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Row(
      children: [
        Icon(icon, size: 18, color: scheme.onSurfaceVariant),
        const SizedBox(width: 8),
        Text(
          title,
          style: TextStyle(
            fontWeight: FontWeight.w700,
            color: scheme.onSurfaceVariant,
          ),
        ),
      ],
    );
  }
}

class _Celebrations extends StatelessWidget {
  const _Celebrations({required this.celebrations, required this.teamIds});

  final AsyncValue<List<Celebration>> celebrations;
  final Set<int> teamIds;

  @override
  Widget build(BuildContext context) {
    return celebrations.when(
      loading: () => const LoadingList(itemCount: 2, itemHeight: 60),
      error: (e, _) => Text(
        'Could not load celebrations',
        style: TextStyle(color: Theme.of(context).colorScheme.error, fontSize: 13),
      ),
      data: (all) {
        final mine =
            all.where((c) => teamIds.contains(c.userId)).toList()
              ..sort((a, b) => a.daysUntil.compareTo(b.daysUntil));
        if (mine.isEmpty) {
          return const _QuietRow(
            icon: Icons.celebration_outlined,
            text: 'No birthdays or anniversaries coming up in your team.',
          );
        }
        return Column(
          children: [
            for (var i = 0; i < mine.length; i++)
              _CelebrationRow(celebration: mine[i])
                  .animate()
                  .fadeIn(delay: (i.clamp(0, 8) * 40).ms, duration: 200.ms),
          ],
        );
      },
    );
  }
}

class _CelebrationRow extends StatelessWidget {
  const _CelebrationRow({required this.celebration});

  final Celebration celebration;

  @override
  Widget build(BuildContext context) {
    final isBirthday = celebration.isBirthday;
    final accent = isBirthday ? AppTheme.success(context) : AppTheme.warning(context);
    final when = celebration.isToday
        ? 'Today'
        : celebration.daysUntil == 1
            ? 'Tomorrow'
            : 'In ${celebration.daysUntil} days';

    return ListTile(
      contentPadding: EdgeInsets.zero,
      leading: Container(
        padding: const EdgeInsets.all(10),
        decoration: BoxDecoration(
          color: accent.withValues(alpha: 0.12),
          borderRadius: BorderRadius.circular(12),
        ),
        child: Icon(
          isBirthday ? Icons.cake_rounded : Icons.work_history_rounded,
          color: accent,
          size: 20,
        ),
      ),
      title: Text(
        celebration.name,
        style: const TextStyle(fontWeight: FontWeight.w600),
      ),
      subtitle: Text(
        isBirthday
            ? (celebration.years != null
                ? 'Turns ${celebration.years} — $when'
                : 'Birthday — $when')
            : (celebration.years != null
                ? '${celebration.years} year${celebration.years == 1 ? '' : 's'} — $when'
                : 'Work anniversary — $when'),
      ),
      trailing: celebration.isToday
          ? Container(
              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
              decoration: BoxDecoration(
                color: accent.withValues(alpha: 0.12),
                borderRadius: BorderRadius.circular(999),
              ),
              child: Text(
                'Today',
                style: TextStyle(
                  fontSize: 11,
                  fontWeight: FontWeight.w700,
                  color: accent,
                ),
              ),
            )
          : null,
    );
  }
}

class _OnLeave extends StatelessWidget {
  const _OnLeave({required this.onLeave, required this.teamIds});

  final AsyncValue<List<dynamic>> onLeave;
  final Set<int> teamIds;

  @override
  Widget build(BuildContext context) {
    return onLeave.when(
      loading: () => const LoadingList(itemCount: 2, itemHeight: 60),
      error: (e, _) => Text(
        'Could not load who is off',
        style: TextStyle(color: Theme.of(context).colorScheme.error, fontSize: 13),
      ),
      data: (rows) {
        final mine = rows.where((r) {
          final id = (r is Map) ? (r['userId'] as num?)?.toInt() : null;
          return id != null && teamIds.contains(id);
        }).toList();
        if (mine.isEmpty) {
          return const _QuietRow(
            icon: Icons.event_busy_rounded,
            text: 'Nobody in your team is off today.',
          );
        }
        final fmt = DateFormat('d MMM');
        return Column(
          children: [
            for (final r in mine)
              if (r is Map)
                ListTile(
                  contentPadding: EdgeInsets.zero,
                  leading: CircleAvatar(
                    backgroundColor:
                        Theme.of(context).colorScheme.primary.withValues(alpha: 0.12),
                    child: Icon(
                      Icons.person_rounded,
                      color: Theme.of(context).colorScheme.primary,
                      size: 20,
                    ),
                  ),
                  title: Text(
                    r['employeeName']?.toString() ?? r['name']?.toString() ?? '—',
                    style: const TextStyle(fontWeight: FontWeight.w600),
                  ),
                  subtitle: Text(
                    '${fmt.format(DateTime.tryParse('${r['fromDate']}') ?? DateTime.now())}'
                    ' — ${fmt.format(DateTime.tryParse('${r['toDate']}') ?? DateTime.now())}',
                  ),
                  trailing: r['leaveTypeName'] == null
                      ? null
                      : Text(
                          r['leaveTypeName'].toString(),
                          style: TextStyle(
                            fontSize: 12,
                            color: Theme.of(context).colorScheme.onSurfaceVariant,
                          ),
                        ),
                ),
          ],
        );
      },
    );
  }
}

class _QuietRow extends StatelessWidget {
  const _QuietRow({required this.icon, required this.text});

  final IconData icon;
  final String text;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 8),
      child: Row(
        children: [
          Icon(icon, size: 18, color: scheme.onSurfaceVariant),
          const SizedBox(width: 10),
          Expanded(
            child: Text(
              text,
              style: TextStyle(color: scheme.onSurfaceVariant, fontSize: 13),
            ),
          ),
        ],
      ),
    );
  }
}

class _MemberTile extends StatelessWidget {
  const _MemberTile({
    required this.name,
    this.code,
    this.designation,
  });

  final String name;
  final String? code;
  final String? designation;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final initials = name
        .trim()
        .split(RegExp(r'\s+'))
        .where((p) => p.isNotEmpty)
        .take(2)
        .map((p) => p[0].toUpperCase())
        .join();

    return ListTile(
      contentPadding: EdgeInsets.zero,
      leading: CircleAvatar(
        backgroundColor: scheme.primary.withValues(alpha: 0.12),
        child: Text(
          initials.isEmpty ? '?' : initials,
          style: TextStyle(color: scheme.primary, fontWeight: FontWeight.w700, fontSize: 14),
        ),
      ),
      title: Text(name, style: const TextStyle(fontWeight: FontWeight.w600)),
      subtitle: code == null
          ? null
          : Text(
              [code, designation].whereType<String>().join(' · '),
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
            ),
    );
  }
}
