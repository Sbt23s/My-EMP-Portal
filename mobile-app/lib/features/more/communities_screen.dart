import 'package:flutter/material.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../models/chat.dart';
import '../../models/directory_person.dart';
import '../../providers/app_providers.dart';
import '../../widgets/states.dart';
import '../chat/chat_screen.dart';

final communitiesProvider = FutureProvider.autoDispose<List<ChatChannel>>(
  (ref) => ref.watch(workRepositoryProvider).communityGroups(),
);

final directoryPeopleProvider = FutureProvider.autoDispose<List<DirectoryPerson>>(
  (ref) => ref.watch(workRepositoryProvider).directory(size: 1000),
);

/// Community groups: who is in each, who can be added, and creating new ones.
///
/// The portal's Communities page on a phone — for people holding ORG_MANAGE or
/// COMMUNITY_MANAGE, the same people the web route lets in. Each group expands
/// to its members, and members can be added from the directory or removed.
class CommunitiesScreen extends ConsumerWidget {
  const CommunitiesScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(communitiesProvider);

    return Scaffold(
      appBar: AppBar(title: const Text('Communities')),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => _create(context, ref),
        icon: const Icon(Icons.add_rounded),
        label: const Text('New group'),
      ),
      body: RefreshIndicator(
        onRefresh: () async => ref.invalidate(communitiesProvider),
        child: async.when(
          loading: () => const LoadingList(),
          error: (e, _) => ListView(
            physics: const AlwaysScrollableScrollPhysics(),
            children: [
              const SizedBox(height: 80),
              ErrorState(
                message: '$e',
                onRetry: () => ref.invalidate(communitiesProvider),
              ),
            ],
          ),
          data: (groups) {
            final visible =
                groups.where((g) => !g.direct).toList();
            if (visible.isEmpty) {
              return ListView(
                physics: const AlwaysScrollableScrollPhysics(),
                children: const [
                  SizedBox(height: 80),
                  EmptyState(
                    icon: Icons.groups_outlined,
                    title: 'No community groups',
                    description:
                        'Create one and add people — they will find it in Chat.',
                  ),
                ],
              );
            }
            return ListView.builder(
              physics: const AlwaysScrollableScrollPhysics(),
              padding: const EdgeInsets.fromLTRB(16, 16, 16, 96),
              itemCount: visible.length,
              itemBuilder: (context, i) {
                final group = visible[i];
                return _GroupCard(group: group)
                    .animate()
                    .fadeIn(delay: (i.clamp(0, 8) * 40).ms, duration: 220.ms)
                    .slideY(begin: 0.06, end: 0, curve: Curves.easeOutCubic);
              },
            );
          },
        ),
      ),
    );
  }

  Future<void> _create(BuildContext context, WidgetRef ref) async {
    final created = await showModalBottomSheet<bool>(
      context: context,
      isScrollControlled: true,
      showDragHandle: true,
      builder: (_) => const _CreateGroupSheet(),
    );
    if (created == true && context.mounted) {
      ref.invalidate(communitiesProvider);
    }
  }
}

class _GroupCard extends ConsumerStatefulWidget {
  const _GroupCard({required this.group});

  final ChatChannel group;

  @override
  ConsumerState<_GroupCard> createState() => _GroupCardState();
}

class _GroupCardState extends ConsumerState<_GroupCard> {
  bool _expanded = false;
  late Future<List<DirectoryPerson>> _membersFuture;

  @override
  void initState() {
    super.initState();
    _membersFuture = ref.read(workRepositoryProvider).communityMembers(widget.group.id);
  }

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Card(
      margin: const EdgeInsets.only(bottom: 12),
      child: Column(
        children: [
          ListTile(
            leading: Container(
              padding: const EdgeInsets.all(10),
              decoration: BoxDecoration(
                color: scheme.primary.withValues(alpha: 0.12),
                borderRadius: BorderRadius.circular(12),
              ),
              child: Icon(
                widget.group.isAnnouncement ? Icons.campaign_rounded : Icons.groups_rounded,
                color: scheme.primary,
              ),
            ),
            title: Text(widget.group.name, style: const TextStyle(fontWeight: FontWeight.w700)),
            subtitle: widget.group.description == null
                ? null
                : Text(widget.group.description!, maxLines: 1, overflow: TextOverflow.ellipsis),
            trailing: IconButton(
              tooltip: 'Delete group',
              icon: const Icon(Icons.delete_outline_rounded),
              onPressed: () => _delete(context),
            ),
            onTap: () => setState(() => _expanded = !_expanded),
          ),
          if (_expanded) ...[
            const Divider(height: 1, indent: 16, endIndent: 16),
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 8, 16, 16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Text(
                        'Members',
                        style: TextStyle(
                          fontWeight: FontWeight.w700,
                          color: scheme.onSurfaceVariant,
                        ),
                      ),
                      const Spacer(),
                      TextButton.icon(
                        onPressed: () => _openChat(context),
                        icon: const Icon(Icons.forum_outlined, size: 18),
                        label: const Text('Open chat'),
                      ),
                    ],
                  ),
                  FutureBuilder<List<DirectoryPerson>>(
                    future: _membersFuture,
                    builder: (context, snapshot) {
                      if (snapshot.connectionState != ConnectionState.done) {
                        return const Padding(
                          padding: EdgeInsets.symmetric(vertical: 16),
                          child: Center(
                            child: SizedBox(
                              width: 22,
                              height: 22,
                              child: CircularProgressIndicator(strokeWidth: 2),
                            ),
                          ),
                        );
                      }
                      if (snapshot.hasError) {
                        return Padding(
                          padding: const EdgeInsets.symmetric(vertical: 8),
                          child: Text(
                            'Could not load members',
                            style: TextStyle(color: scheme.error, fontSize: 13),
                          ),
                        );
                      }
                      final members = snapshot.data ?? const <DirectoryPerson>[];
                      if (members.isEmpty) {
                        return const Padding(
                          padding: EdgeInsets.symmetric(vertical: 8),
                          child: Text('No members yet — add some below.'),
                        );
                      }
                      return Column(
                        children: [
                          for (final m in members)
                            ListTile(
                              dense: true,
                              contentPadding: EdgeInsets.zero,
                              leading: CircleAvatar(
                                radius: 16,
                                backgroundColor: scheme.primary.withValues(alpha: 0.12),
                                child: Text(
                                  m.initials,
                                  style: TextStyle(
                                    color: scheme.primary,
                                    fontSize: 12,
                                    fontWeight: FontWeight.w700,
                                  ),
                                ),
                              ),
                              title: Text(m.name, style: const TextStyle(fontSize: 14)),
                              trailing: IconButton(
                                tooltip: 'Remove',
                                icon: const Icon(Icons.person_remove_outlined, size: 20),
                                onPressed: () => _removeMember(context, m.id),
                              ),
                            ),
                        ],
                      );
                    },
                  ),
                  const SizedBox(height: 8),
                  _AddMemberRow(groupId: widget.group.id, onChanged: () {
                    setState(() {
                      _membersFuture = ref
                          .read(workRepositoryProvider)
                          .communityMembers(widget.group.id);
                    });
                  }),
                ],
              ),
            ),
          ],
        ],
      ),
    );
  }

  Future<void> _openChat(BuildContext context) async {
    Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) => ChatRoomScreen(channel: widget.group),
      ),
    );
  }

  Future<void> _delete(BuildContext context) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Delete group?'),
        content: Text('"${widget.group.name}" will be removed for everyone.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Cancel')),
          FilledButton(
            style: FilledButton.styleFrom(
              backgroundColor: Theme.of(context).colorScheme.error,
            ),
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('Delete'),
          ),
        ],
      ),
    );
    if (confirmed != true || !context.mounted) return;
    try {
      await ref.read(workRepositoryProvider).deleteCommunity(widget.group.id);
      if (!context.mounted) return;
      ref.invalidate(communitiesProvider);
    } catch (e) {
      if (!context.mounted) return;
      ScaffoldMessenger.of(context)
        ..hideCurrentSnackBar()
        ..showSnackBar(SnackBar(content: Text('$e')));
    }
  }

  Future<void> _removeMember(BuildContext context, int userId) async {
    try {
      await ref.read(workRepositoryProvider).removeCommunityMember(widget.group.id, userId);
      if (!context.mounted) return;
      setState(() {
        _membersFuture = ref
            .read(workRepositoryProvider)
            .communityMembers(widget.group.id);
      });
    } catch (e) {
      if (!context.mounted) return;
      ScaffoldMessenger.of(context)
        ..hideCurrentSnackBar()
        ..showSnackBar(SnackBar(content: Text('$e')));
    }
  }
}

class _AddMemberRow extends ConsumerWidget {
  const _AddMemberRow({required this.groupId, required this.onChanged});

  final int groupId;
  final VoidCallback onChanged;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final people = ref.watch(directoryPeopleProvider);
    return people.when(
      loading: () => const LinearProgressIndicator(minHeight: 2),
      error: (_, __) => const SizedBox.shrink(),
      data: (all) {
        return Row(
          children: [
            Expanded(
              child: DropdownButtonFormField<int>(
                decoration: const InputDecoration(
                  labelText: 'Add a person',
                  isDense: true,
                  contentPadding: EdgeInsets.symmetric(horizontal: 12, vertical: 10),
                ),
                items: all
                    .map((p) => DropdownMenuItem(
                          value: p.id,
                          child: Text(
                            p.name,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                          ),
                        ))
                    .toList(),
                onChanged: (id) {
                  if (id == null) return;
                  ref.read(workRepositoryProvider).addCommunityMember(groupId, id).then((_) {
                    onChanged();
                    if (context.mounted) {
                      ScaffoldMessenger.of(context)
                        ..hideCurrentSnackBar()
                        ..showSnackBar(const SnackBar(content: Text('Member added')));
                    }
                  }).catchError((e) {
                    if (context.mounted) {
                      ScaffoldMessenger.of(context)
                        ..hideCurrentSnackBar()
                        ..showSnackBar(SnackBar(content: Text('$e')));
                    }
                  });
                },
              ),
            ),
          ],
        );
      },
    );
  }
}

class _CreateGroupSheet extends ConsumerStatefulWidget {
  const _CreateGroupSheet();

  @override
  ConsumerState<_CreateGroupSheet> createState() => _CreateGroupSheetState();
}

class _CreateGroupSheetState extends ConsumerState<_CreateGroupSheet> {
  final _formKey = GlobalKey<FormState>();
  final _name = TextEditingController();
  final _description = TextEditingController();
  bool _announcement = false;
  bool _busy = false;

  @override
  void dispose() {
    _name.dispose();
    _description.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (_busy) return;
    if (!(_formKey.currentState?.validate() ?? false)) return;
    setState(() => _busy = true);
    try {
      await ref.read(workRepositoryProvider).createCommunityGroup(
            name: _name.text,
            description: _description.text,
            isAnnouncement: _announcement,
          );
      if (mounted) Navigator.of(context).pop(true);
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context)
        ..hideCurrentSnackBar()
        ..showSnackBar(SnackBar(content: Text('$e')));
      setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      child: Padding(
        padding: EdgeInsets.only(
          left: 20,
          right: 20,
          top: 4,
          bottom: MediaQuery.of(context).viewInsets.bottom + 24,
        ),
        child: Form(
          key: _formKey,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('New group', style: Theme.of(context).textTheme.titleLarge),
              const SizedBox(height: 16),
              TextFormField(
                controller: _name,
                decoration: const InputDecoration(labelText: 'Group name'),
                validator: (v) =>
                    (v == null || v.trim().isEmpty) ? 'Give the group a name' : null,
              ),
              const SizedBox(height: 12),
              TextFormField(
                controller: _description,
                maxLines: 2,
                decoration: const InputDecoration(
                  labelText: 'Description (optional)',
                  alignLabelWithHint: true,
                ),
              ),
              SwitchListTile(
                contentPadding: EdgeInsets.zero,
                title: const Text('Announcement channel'),
                subtitle: const Text('HR posts, everyone reads'),
                value: _announcement,
                onChanged: (v) => setState(() => _announcement = v),
              ),
              const SizedBox(height: 8),
              SizedBox(
                width: double.infinity,
                child: FilledButton.icon(
                  onPressed: _busy ? null : _submit,
                  icon: _busy
                      ? const SizedBox(
                          width: 18,
                          height: 18,
                          child: CircularProgressIndicator(strokeWidth: 2),
                        )
                      : const Icon(Icons.check_rounded),
                  label: Text(_busy ? 'Creating…' : 'Create group'),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
