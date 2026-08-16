import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import '../../models/chat.dart';
import '../../providers/app_providers.dart';
import '../../core/realtime/realtime_service.dart';
import '../../core/calls/call_service.dart';
import '../../providers/call_provider.dart';
import '../../providers/realtime_provider.dart';
import '../../widgets/states.dart';

final chatChannelsProvider = FutureProvider.autoDispose<List<ChatChannel>>(
  (ref) => ref.watch(workRepositoryProvider).myChannels(),
);

final chatMessagesProvider =
    FutureProvider.autoDispose.family<List<ChatMessage>, int>(
  (ref, channelId) => ref.watch(workRepositoryProvider).messages(channelId),
);

/// Conversations.
///
/// The portal's Chat, on a phone. Text only: voice notes, polls, reactions and
/// acknowledgements are things the browser does, and each is a feature of its
/// own rather than a detail of this one. Reading and replying is what a phone is
/// actually used for between desks.
class ChatScreen extends ConsumerWidget {
  const ChatScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(chatChannelsProvider);

    return Scaffold(
      appBar: AppBar(title: const Text('Chat')),
      // Channels arrive already joined; a one-to-one has to be started. Without
      // this the app could only read conversations somebody else had opened.
      floatingActionButton: FloatingActionButton(
        onPressed: () => _startDirect(context, ref),
        child: const Icon(Icons.person_add_alt_1_rounded),
      ),
      body: RefreshIndicator(
        onRefresh: () async => ref.invalidate(chatChannelsProvider),
        child: async.when(
          loading: () => const LoadingList(),
          error: (e, _) => ListView(
            physics: const AlwaysScrollableScrollPhysics(),
            children: [
              const SizedBox(height: 80),
              ErrorState(
                message: '$e',
                onRetry: () => ref.invalidate(chatChannelsProvider),
              ),
            ],
          ),
          data: (channels) {
            if (channels.isEmpty) {
              return ListView(
                physics: const AlwaysScrollableScrollPhysics(),
                children: const [
                  SizedBox(height: 80),
                  EmptyState(
                    icon: Icons.forum_outlined,
                    title: 'No conversations yet',
                    description:
                        'Channels you are added to will appear here.',
                  ),
                ],
              );
            }

            return ListView.separated(
              physics: const AlwaysScrollableScrollPhysics(),
              padding: const EdgeInsets.symmetric(vertical: 8),
              itemCount: channels.length,
              separatorBuilder: (_, __) => const Divider(height: 1, indent: 72),
              itemBuilder: (context, i) {
                final c = channels[i];
                return ListTile(
                  leading: CircleAvatar(
                    backgroundColor: Theme.of(context)
                        .colorScheme
                        .primary
                        .withValues(alpha: 0.14),
                    child: Icon(
                      c.direct
                          ? Icons.person_rounded
                          : c.isAnnouncement
                              ? Icons.campaign_rounded
                              : Icons.groups_rounded,
                      color: Theme.of(context).colorScheme.primary,
                      size: 20,
                    ),
                  ),
                  title: Text(
                    c.name,
                    style: const TextStyle(fontWeight: FontWeight.w600),
                  ),
                  subtitle: c.description == null
                      ? (c.isAnnouncement ? const Text('Announcements') : null)
                      : Text(c.description!, maxLines: 1, overflow: TextOverflow.ellipsis),
                  trailing: const Icon(Icons.chevron_right_rounded),
                  onTap: () => Navigator.of(context).push(
                    MaterialPageRoute<void>(
                      builder: (_) => ChatRoomScreen(channel: c),
                    ),
                  ),
                ).animate().fadeIn(
                      delay: (i.clamp(0, 10) * 30).ms,
                      duration: 200.ms,
                    );
              },
            );
          },
        ),
      ),
    );
  }
}

/// Pick somebody to message.
///
/// The server finds an existing conversation or makes one, so tapping the same
/// person twice opens the same thread rather than a second empty one.
Future<void> _startDirect(BuildContext context, WidgetRef ref) async {
  final repo = ref.read(workRepositoryProvider);

  final chosen = await showModalBottomSheet<Map<String, dynamic>>(
    context: context,
    isScrollControlled: true,
    useSafeArea: true,
    builder: (sheetContext) => FutureBuilder<List<Map<String, dynamic>>>(
      future: repo.chatContacts(),
      builder: (c, snap) {
        if (snap.connectionState != ConnectionState.done) {
          return const SizedBox(
            height: 220,
            child: Center(child: CircularProgressIndicator()),
          );
        }
        if (snap.hasError) {
          return SizedBox(
            height: 220,
            child: Center(child: Text('${snap.error}')),
          );
        }
        final people = snap.data ?? const [];
        if (people.isEmpty) {
          return const SizedBox(
            height: 220,
            child: Center(child: Text('Nobody to message yet.')),
          );
        }
        return ListView.builder(
          shrinkWrap: true,
          padding: const EdgeInsets.symmetric(vertical: 12),
          itemCount: people.length,
          itemBuilder: (_, i) {
            final p = people[i];
            final name = p['name']?.toString() ?? 'Colleague';
            return ListTile(
              leading: CircleAvatar(child: Text(name.characters.first.toUpperCase())),
              title: Text(name),
              subtitle: p['designationTitle'] == null
                  ? null
                  : Text(p['designationTitle'].toString()),
              onTap: () => Navigator.of(sheetContext).pop(p),
            );
          },
        );
      },
    ),
  );

  if (chosen == null || !context.mounted) return;

  final id = (chosen['id'] as num?)?.toInt();
  if (id == null) return;

  try {
    final channel = await repo.openDirect(id);
    ref.invalidate(chatChannelsProvider);
    if (!context.mounted) return;
    await Navigator.of(context).push(
      MaterialPageRoute<void>(builder: (_) => ChatRoomScreen(channel: channel)),
    );
  } catch (e) {
    if (!context.mounted) return;
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text('$e')));
  }
}

/// One conversation.
///
/// Refreshed on a timer while it is open. There is no websocket in this app —
/// the portal's real-time channel is STOMP over SockJS, which is a piece of
/// infrastructure rather than a detail — so this polls, and says nothing about
/// being live that it cannot keep. Five seconds is close enough to feel
/// immediate in a conversation and cheap enough not to trouble a database with
/// twenty connections.
///
/// Polling stops the moment the screen is left, so a forgotten conversation
/// costs nothing.
class ChatRoomScreen extends ConsumerStatefulWidget {
  const ChatRoomScreen({required this.channel, super.key});

  final ChatChannel channel;

  @override
  ConsumerState<ChatRoomScreen> createState() => _ChatRoomScreenState();
}

class _ChatRoomScreenState extends ConsumerState<ChatRoomScreen> {
  /// A safety net behind the socket, not the delivery mechanism.
  ///
  /// Thirty seconds rather than five: the socket does the work, and this only
  /// catches the case where it dropped silently. Leaving it at five would spend
  /// the request budget on a job something else is already doing.
  static const _pollEvery = Duration(seconds: 30);

  final _composer = TextEditingController();
  final _scroll = ScrollController();
  Timer? _timer;
  bool _sending = false;

  StreamSubscription<RealtimeEvent>? _live;

  @override
  void initState() {
    super.initState();

    final realtime = ref.read(realtimeServiceProvider);
    final topic = '/topic/community/${widget.channel.id}';
    realtime.subscribe(topic);

    // Messages arrive as they are sent. The poll below stays as a safety net —
    // a socket that dropped without saying so would otherwise leave the
    // conversation frozen and looking fine.
    _live = realtime.events.listen((event) {
      if (event.topic != topic || !mounted) return;
      ref.invalidate(chatMessagesProvider(widget.channel.id));
    });

    _timer = Timer.periodic(_pollEvery, (_) {
      if (mounted) ref.invalidate(chatMessagesProvider(widget.channel.id));
    });
  }

  @override
  void dispose() {
    _live?.cancel();
    ref.read(realtimeServiceProvider)
        .unsubscribe('/topic/community/${widget.channel.id}');
    _timer?.cancel();
    _composer.dispose();
    _scroll.dispose();
    super.dispose();
  }

  Future<void> _startCall({required bool video}) async {
    final partnerId = widget.channel.partnerId;
    if (partnerId == null) return;
    await ref.read(callServiceProvider).call(
          CallPeer(id: partnerId, name: widget.channel.name),
          video: video,
        );
  }

  Future<void> _send() async {
    final text = _composer.text.trim();
    if (text.isEmpty || _sending) return;

    setState(() => _sending = true);
    try {
      await ref.read(workRepositoryProvider).sendMessage(widget.channel.id, text);
      _composer.clear();
      ref.invalidate(chatMessagesProvider(widget.channel.id));
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context)
        ..hideCurrentSnackBar()
        ..showSnackBar(
          SnackBar(
            content: Text('$e'),
            backgroundColor: Theme.of(context).colorScheme.error,
          ),
        );
    } finally {
      if (mounted) setState(() => _sending = false);
    }
  }

  Future<void> _react(int messageId, String emoji) async {
    try {
      await ref.read(workRepositoryProvider).reactToMessage(messageId, emoji);
      ref.invalidate(chatMessagesProvider(widget.channel.id));
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context)
        ..hideCurrentSnackBar()
        ..showSnackBar(SnackBar(content: Text('$e')));
    }
  }

  Future<void> _pin(ChatMessage m) async {
    try {
      // Toggled from what it is now, so the same gesture pins and unpins.
      await ref.read(workRepositoryProvider).pinMessage(m.id, pinned: !m.pinned);
      ref.invalidate(chatMessagesProvider(widget.channel.id));
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context)
        ..hideCurrentSnackBar()
        ..showSnackBar(SnackBar(content: Text('$e')));
    }
  }

  @override
  Widget build(BuildContext context) {
    final me = ref.watch(currentUserProvider)?.id;
    final async = ref.watch(chatMessagesProvider(widget.channel.id));

    return Scaffold(
      appBar: AppBar(
        title: Text(widget.channel.name),
        actions: [
          // Only in a one-to-one. A group call is a different thing entirely —
          // several peer connections, a layout for many faces — and offering the
          // button in a channel would ring one arbitrary member.
          if (widget.channel.direct && widget.channel.partnerId != null) ...[
            IconButton(
              tooltip: 'Voice call',
              icon: const Icon(Icons.call_rounded),
              onPressed: () => _startCall(video: false),
            ),
            IconButton(
              tooltip: 'Video call',
              icon: const Icon(Icons.videocam_rounded),
              onPressed: () => _startCall(video: true),
            ),
          ],
        ],
      ),
      body: Column(
        children: [
          Expanded(
            child: async.when(
              loading: () => const LoadingList(),
              error: (e, _) => ErrorState(
                message: '$e',
                onRetry: () =>
                    ref.invalidate(chatMessagesProvider(widget.channel.id)),
              ),
              data: (messages) {
                if (messages.isEmpty) {
                  return const EmptyState(
                    icon: Icons.chat_bubble_outline_rounded,
                    title: 'No messages yet',
                    description: 'Say something to start the conversation.',
                  );
                }

                // Newest at the bottom, and the view starts there — which is
                // where a conversation is read from. `reverse` puts the list's
                // natural resting position at the latest message without having
                // to scroll to it after every load.
                final ordered = messages.reversed.toList();

                return ListView.builder(
                  controller: _scroll,
                  reverse: true,
                  padding: const EdgeInsets.fromLTRB(12, 12, 12, 8),
                  itemCount: ordered.length,
                  itemBuilder: (context, i) => _Bubble(
                    message: ordered[i],
                    mine: ordered[i].senderId == me,
                    showSender: !widget.channel.direct,
                    onReact: (emoji) => _react(ordered[i].id, emoji),
                    onPin: () => _pin(ordered[i]),
                  ),
                );
              },
            ),
          ),
          _Composer(
            controller: _composer,
            sending: _sending,
            onSend: _send,
            // An announcement channel is written to by HR, not replied to. The
            // server refuses either way; hiding the box is so nobody types a
            // paragraph before finding that out.
            readOnly: widget.channel.isAnnouncement,
          ),
        ],
      ),
    );
  }
}

/// The handful offered on a long press.
///
/// Six, not a picker. A phone keyboard's full emoji set turns a one-tap
/// acknowledgement into a search, and these are the ones people actually use to
/// say "seen", "yes" and "thanks".
const _quickReactions = ['👍', '❤️', '😂', '🎉', '👀', '🙏'];

class _Bubble extends StatelessWidget {
  const _Bubble({
    required this.message,
    required this.mine,
    required this.showSender,
    required this.onReact,
    required this.onPin,
  });

  final ChatMessage message;
  final bool mine;
  final bool showSender;
  final void Function(String emoji) onReact;
  final VoidCallback onPin;

  Future<void> _menu(BuildContext context) async {
    await showModalBottomSheet<void>(
      context: context,
      builder: (sheet) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Padding(
              padding: const EdgeInsets.symmetric(vertical: 14, horizontal: 8),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                children: [
                  for (final e in _quickReactions)
                    InkWell(
                      borderRadius: BorderRadius.circular(24),
                      onTap: () {
                        Navigator.of(sheet).pop();
                        onReact(e);
                      },
                      child: Padding(
                        padding: const EdgeInsets.all(8),
                        child: Text(
                          e,
                          style: TextStyle(
                            fontSize: 26,
                            // Already used by this person, so the menu says what
                            // they have done and not only what they could do.
                            fontWeight: message.myReactions.contains(e)
                                ? FontWeight.bold
                                : FontWeight.normal,
                          ),
                        ),
                      ),
                    ),
                ],
              ),
            ),
            const Divider(height: 1),
            ListTile(
              leading: Icon(
                message.pinned ? Icons.push_pin_rounded : Icons.push_pin_outlined,
              ),
              title: Text(message.pinned ? 'Unpin' : 'Pin to this channel'),
              onTap: () {
                Navigator.of(sheet).pop();
                onPin();
              },
            ),
          ],
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;

    if (message.deleted) {
      return Padding(
        padding: const EdgeInsets.symmetric(vertical: 6),
        child: Center(
          child: Text(
            'Message deleted',
            style: Theme.of(context)
                .textTheme
                .labelSmall
                ?.copyWith(color: scheme.onSurfaceVariant),
          ),
        ),
      );
    }

    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: Row(
        mainAxisAlignment: mine ? MainAxisAlignment.end : MainAxisAlignment.start,
        children: [
          Flexible(
            child: GestureDetector(
              onLongPress: () => _menu(context),
              child: Container(
              constraints: BoxConstraints(
                maxWidth: MediaQuery.of(context).size.width * 0.78,
              ),
              padding: const EdgeInsets.symmetric(horizontal: 13, vertical: 9),
              decoration: BoxDecoration(
                color: mine ? scheme.primary : scheme.surfaceContainerHighest,
                borderRadius: BorderRadius.only(
                  topLeft: const Radius.circular(14),
                  topRight: const Radius.circular(14),
                  bottomLeft: Radius.circular(mine ? 14 : 3),
                  bottomRight: Radius.circular(mine ? 3 : 14),
                ),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  if (showSender && !mine && message.senderName != null)
                    Padding(
                      padding: const EdgeInsets.only(bottom: 3),
                      child: Text(
                        message.senderName!,
                        style: Theme.of(context).textTheme.labelSmall?.copyWith(
                              fontWeight: FontWeight.w700,
                              color: scheme.primary,
                            ),
                      ),
                    ),
                  Text(
                    message.content,
                    style: TextStyle(
                      color: mine ? scheme.onPrimary : scheme.onSurface,
                      height: 1.35,
                    ),
                  ),
                  if (message.hasAttachment)
                    Padding(
                      padding: const EdgeInsets.only(top: 4),
                      child: Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Icon(
                            Icons.attach_file_rounded,
                            size: 13,
                            color: (mine ? scheme.onPrimary : scheme.onSurfaceVariant)
                                .withValues(alpha: 0.8),
                          ),
                          const SizedBox(width: 4),
                          Text(
                            // Named, not opened. Downloading an attachment is a
                            // separate piece of work, and a tap that does
                            // nothing is worse than a label that explains.
                            'Attachment — open on the web portal',
                            style: Theme.of(context).textTheme.labelSmall?.copyWith(
                                  color: (mine
                                          ? scheme.onPrimary
                                          : scheme.onSurfaceVariant)
                                      .withValues(alpha: 0.8),
                                ),
                          ),
                        ],
                      ),
                    ),
                  if (message.reactions.isNotEmpty)
                    Padding(
                      padding: const EdgeInsets.only(top: 6),
                      child: Wrap(
                        spacing: 4,
                        runSpacing: 4,
                        children: [
                          for (final entry in message.reactions.entries)
                            GestureDetector(
                              onTap: () => onReact(entry.key),
                              child: Container(
                                padding: const EdgeInsets.symmetric(
                                  horizontal: 7,
                                  vertical: 2,
                                ),
                                decoration: BoxDecoration(
                                  color: (mine ? scheme.onPrimary : scheme.primary)
                                      .withValues(
                                    // The reader's own sits brighter, so a
                                    // glance says whether they have answered it.
                                    alpha: message.myReactions.contains(entry.key)
                                        ? 0.30
                                        : 0.12,
                                  ),
                                  borderRadius: BorderRadius.circular(12),
                                ),
                                child: Text(
                                  '${entry.key} ${entry.value}',
                                  style: TextStyle(
                                    fontSize: 11,
                                    color: mine ? scheme.onPrimary : scheme.onSurface,
                                  ),
                                ),
                              ),
                            ),
                        ],
                      ),
                    ),
                  Padding(
                    padding: const EdgeInsets.only(top: 3),
                    child: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        if (message.pinned)
                          Padding(
                            padding: const EdgeInsets.only(right: 4),
                            child: Icon(
                              Icons.push_pin_rounded,
                              size: 11,
                              color: (mine ? scheme.onPrimary : scheme.onSurfaceVariant)
                                  .withValues(alpha: 0.8),
                            ),
                          ),
                        if (message.sentAt != null)
                          Text(
                            DateFormat('h:mm a').format(message.sentAt!),
                            style: Theme.of(context).textTheme.labelSmall?.copyWith(
                                  fontSize: 10,
                                  color: (mine ? scheme.onPrimary : scheme.onSurfaceVariant)
                                      .withValues(alpha: 0.7),
                                ),
                          ),
                      ],
                    ),
                  ),
                ],
              ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _Composer extends StatelessWidget {
  const _Composer({
    required this.controller,
    required this.sending,
    required this.onSend,
    required this.readOnly,
  });

  final TextEditingController controller;
  final bool sending;
  final VoidCallback onSend;
  final bool readOnly;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;

    if (readOnly) {
      return SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Text(
            'This is an announcement channel — only HR posts here.',
            textAlign: TextAlign.center,
            style: Theme.of(context)
                .textTheme
                .bodySmall
                ?.copyWith(color: scheme.onSurfaceVariant),
          ),
        ),
      );
    }

    return SafeArea(
      child: Container(
        padding: const EdgeInsets.fromLTRB(12, 8, 8, 8),
        decoration: BoxDecoration(
          color: scheme.surface,
          border: Border(top: BorderSide(color: scheme.outlineVariant)),
        ),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.end,
          children: [
            Expanded(
              child: TextField(
                controller: controller,
                enabled: !sending,
                minLines: 1,
                maxLines: 4,
                textCapitalization: TextCapitalization.sentences,
                decoration: const InputDecoration(
                  hintText: 'Message',
                  border: InputBorder.none,
                  filled: false,
                  contentPadding: EdgeInsets.symmetric(vertical: 10),
                ),
                onSubmitted: (_) => onSend(),
              ),
            ),
            IconButton.filled(
              onPressed: sending ? null : onSend,
              icon: sending
                  ? const SizedBox(
                      height: 18,
                      width: 18,
                      child: CircularProgressIndicator(strokeWidth: 2.2),
                    )
                  : const Icon(Icons.send_rounded, size: 20),
            ),
          ],
        ),
      ),
    );
  }
}
