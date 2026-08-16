import 'dart:async';
import 'dart:io';

import 'package:cached_network_image/cached_network_image.dart';
import 'package:dio/dio.dart';
import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:image_picker/image_picker.dart';
import 'package:intl/intl.dart';
import 'package:just_audio/just_audio.dart';
import 'package:open_filex/open_filex.dart';
import 'package:path_provider/path_provider.dart';
import 'package:record/record.dart';

import '../../core/config/app_config.dart';
import '../../core/realtime/realtime_service.dart';
import '../../core/calls/call_service.dart';
import '../../models/chat.dart';
import '../../providers/app_providers.dart';
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

final pinnedMessagesProvider =
    FutureProvider.autoDispose.family<List<ChatMessage>, int>(
  (ref, channelId) => ref.watch(workRepositoryProvider).pinnedMessages(channelId),
);

/// A stored file served from the backend, as a URL the phone can fetch.
String fileUrl(String path) {
  final origin = AppConfig.apiBaseUrl.endsWith('/api')
      ? AppConfig.apiBaseUrl.substring(0, AppConfig.apiBaseUrl.length - 4)
      : AppConfig.apiBaseUrl;
  return '$origin/api/files/$path';
}

bool _isImagePath(String path) {
  final ext = path.split('.').last.toLowerCase();
  return const {'jpg', 'jpeg', 'png', 'gif', 'webp', 'bmp', 'heic'}.contains(ext);
}

/// Conversations.
///
/// The portal's Chat, on a phone. Reading and replying is what a phone is
/// actually used for between desks; the composer also carries attachments,
/// voice notes and polls, exactly as the web client does.
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
                      : Text(
                          c.description!,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
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
              leading: CircleAvatar(
                child: Text(name.characters.first.toUpperCase()),
              ),
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
  bool _pickBusy = false;

  /// The message this room's next send answers, when the person tapped Reply.
  ChatMessage? _replyingTo;

  final _voice = AudioRecorder();
  bool _recording = false;
  int _recordSecs = 0;
  Timer? _recordTimer;

  /// The newest message already reported as read, so a reload never re-reports.
  int _markedReadUpTo = 0;

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
    _recordTimer?.cancel();
    _composer.dispose();
    _scroll.dispose();
    unawaited(_voice.dispose());
    super.dispose();
  }

  void _snack(String message, {bool error = false}) {
    if (!mounted) return;
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(
        SnackBar(
          content: Text(message),
          backgroundColor: error ? Theme.of(context).colorScheme.error : null,
        ),
      );
  }

  /// Reading is a side effect of looking, so it is reported quietly — the
  /// newest message from somebody else is marked seen once, and a failure is
  /// never allowed to interrupt the conversation.
  void _scheduleMarkRead(List<ChatMessage> messages) {
    final me = ref.read(currentUserProvider)?.id;
    if (me == null) return;
    ChatMessage? newestForeign;
    for (final m in messages) {
      if (m.senderId != me && !m.deleted && m.id > _markedReadUpTo) {
        if (newestForeign == null || m.id > newestForeign.id) {
          newestForeign = m;
        }
      }
    }
    if (newestForeign == null) return;
    _markedReadUpTo = newestForeign.id;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      unawaited(
        ref
            .read(workRepositoryProvider)
            .markMessageRead(newestForeign!.id)
            .then((_) {}, onError: (_) {}),
      );
    });
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
      await ref.read(workRepositoryProvider).sendMessage(
            widget.channel.id,
            text,
            parentId: _replyingTo?.id,
          );
      _composer.clear();
      setState(() => _replyingTo = null);
      ref.invalidate(chatMessagesProvider(widget.channel.id));
    } catch (e) {
      _snack('$e', error: true);
    } finally {
      if (mounted) setState(() => _sending = false);
    }
  }

  // ---- attachments --------------------------------------------------------

  Future<void> _attach() async {
    if (_pickBusy) return;
    final choice = await showModalBottomSheet<int>(
      context: context,
      builder: (sheet) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            ListTile(
              leading: const Icon(Icons.photo_library_outlined),
              title: const Text('Photos'),
              subtitle: const Text('From your gallery'),
              onTap: () => Navigator.of(sheet).pop(0),
            ),
            ListTile(
              leading: const Icon(Icons.photo_camera_outlined),
              title: const Text('Camera'),
              subtitle: const Text('Take a photo now'),
              onTap: () => Navigator.of(sheet).pop(1),
            ),
            ListTile(
              leading: const Icon(Icons.insert_drive_file_outlined),
              title: const Text('Document'),
              subtitle: const Text('PDF, sheets, anything stored'),
              onTap: () => Navigator.of(sheet).pop(2),
            ),
          ],
        ),
      ),
    );
    if (choice == null || !mounted) return;

    setState(() => _pickBusy = true);
    try {
      List<File> files = const [];
      final picker = ImagePicker();
      switch (choice) {
        case 0:
          final imgs = await picker.pickMultiImage();
          files = imgs.map((x) => File(x.path)).toList();
        case 1:
          final img = await picker.pickImage(source: ImageSource.camera);
          if (img != null) files = [File(img.path)];
        case 2:
          final res = await FilePicker.platform.pickFiles(allowMultiple: true);
          if (res != null) {
            files = res.paths.whereType<String>().map(File.new).toList();
          }
      }
      if (files.isEmpty) return;

      final caption = await _askCaption();
      if (!mounted) return;
      await ref
          .read(workRepositoryProvider)
          .sendChatAttachments(widget.channel.id, files, caption: caption);
      ref.invalidate(chatMessagesProvider(widget.channel.id));
    } catch (e) {
      _snack('$e', error: true);
    } finally {
      if (mounted) setState(() => _pickBusy = false);
    }
  }

  Future<String?> _askCaption() async {
    final ctrl = TextEditingController();
    final caption = await showModalBottomSheet<String>(
      context: context,
      isScrollControlled: true,
      useSafeArea: true,
      builder: (sheet) => Padding(
        padding: EdgeInsets.only(
          left: 20,
          right: 20,
          top: 20,
          bottom: 20 + MediaQuery.of(sheet).viewInsets.bottom,
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(
              'Add a caption',
              style: Theme.of(
                sheet,
              ).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w700),
            ),
            const SizedBox(height: 14),
            TextField(
              controller: ctrl,
              autofocus: true,
              maxLines: 2,
              decoration: const InputDecoration(
                hintText: 'What is this about? (optional)',
              ),
            ),
            const SizedBox(height: 16),
            FilledButton(
              onPressed: () => Navigator.of(sheet).pop(ctrl.text.trim()),
              child: const Text('Send'),
            ),
          ],
        ),
      ),
    );
    ctrl.dispose();
    return (caption == null || caption.isEmpty) ? null : caption;
  }

  Future<void> _openAttachment(String path) async {
    if (_isImagePath(path)) {
      if (!mounted) return;
      await Navigator.of(context).push(
        MaterialPageRoute<void>(builder: (_) => _ImageViewer(url: fileUrl(path))),
      );
      return;
    }
    try {
      final dio = ref.read(apiClientProvider).raw;
      final res = await dio.get<List<int>>(
        fileUrl(path),
        options: Options(responseType: ResponseType.bytes),
      );
      final dir = await getTemporaryDirectory();
      final name = path.split('/').last;
      final file = File('${dir.path}/$name');
      await file.writeAsBytes(res.data ?? const []);
      if (!mounted) return;
      final opened = await OpenFilex.open(file.path);
      if (opened.type != ResultType.done) {
        _snack('File saved, but no app on this phone opens it.');
      }
    } catch (e) {
      _snack('$e', error: true);
    }
  }

  // ---- voice notes --------------------------------------------------------

  Future<void> _toggleVoice() async {
    if (_recording) {
      await _stopAndSendVoice();
      return;
    }
    if (_pickBusy) return;
    setState(() => _pickBusy = true);
    try {
      final ok = await _voice.hasPermission();
      if (!ok) {
        _snack('Microphone permission is needed for voice notes.');
        return;
      }
      final dir = await getTemporaryDirectory();
      final path =
          '${dir.path}/voice_${DateTime.now().millisecondsSinceEpoch}.m4a';
      await _voice.start(const RecordConfig(), path: path);
      if (!mounted) return;
      setState(() {
        _recording = true;
        _recordSecs = 0;
      });
      _recordTimer = Timer.periodic(const Duration(seconds: 1), (_) {
        if (mounted) setState(() => _recordSecs++);
      });
    } catch (e) {
      _snack('$e', error: true);
    } finally {
      if (mounted) setState(() => _pickBusy = false);
    }
  }

  Future<void> _stopAndSendVoice() async {
    _recordTimer?.cancel();
    String? path;
    try {
      path = await _voice.stop();
    } catch (_) {}
    if (!mounted) return;
    setState(() {
      _recording = false;
      _recordSecs = 0;
    });
    if (path == null) {
      _snack('Recording was too short.');
      return;
    }
    setState(() => _pickBusy = true);
    try {
      await ref
          .read(workRepositoryProvider)
          .sendChatVoice(widget.channel.id, File(path));
      ref.invalidate(chatMessagesProvider(widget.channel.id));
    } catch (e) {
      _snack('$e', error: true);
    } finally {
      if (mounted) setState(() => _pickBusy = false);
    }
  }

  // ---- polls --------------------------------------------------------------

  Future<void> _openPollBuilder() async {
    if (_pickBusy) return;
    final options = await showModalBottomSheet<List<String>>(
      context: context,
      isScrollControlled: true,
      useSafeArea: true,
      builder: (_) => const _PollBuilderSheet(),
    );
    if (options == null || options.length < 2 || !mounted) return;
    setState(() => _pickBusy = true);
    try {
      await ref
          .read(workRepositoryProvider)
          .sendMessage(widget.channel.id, '', pollOptions: options);
      ref.invalidate(chatMessagesProvider(widget.channel.id));
    } catch (e) {
      _snack('$e', error: true);
    } finally {
      if (mounted) setState(() => _pickBusy = false);
    }
  }

  // ---- message actions ----------------------------------------------------

  Future<void> _react(int messageId, String emoji) async {
    try {
      await ref.read(workRepositoryProvider).reactToMessage(messageId, emoji);
      ref.invalidate(chatMessagesProvider(widget.channel.id));
    } catch (e) {
      _snack('$e', error: true);
    }
  }

  Future<void> _pin(ChatMessage m) async {
    try {
      // Toggled from what it is now, so the same gesture pins and unpins.
      await ref
          .read(workRepositoryProvider)
          .pinMessage(m.id, pinned: !m.pinned);
      ref.invalidate(chatMessagesProvider(widget.channel.id));
    } catch (e) {
      _snack('$e', error: true);
    }
  }

  Future<void> _delete(ChatMessage m) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialog) => AlertDialog(
        icon: const Icon(Icons.delete_outline_rounded),
        title: const Text('Delete this message?'),
        content: const Text(
          'It disappears for everyone in this conversation.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(dialog).pop(false),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () => Navigator.of(dialog).pop(true),
            child: const Text('Delete'),
          ),
        ],
      ),
    );
    if (confirmed != true || !mounted) return;
    try {
      await ref.read(workRepositoryProvider).deleteMessage(m.id);
      ref.invalidate(chatMessagesProvider(widget.channel.id));
    } catch (e) {
      _snack('$e', error: true);
    }
  }

  Future<void> _vote(ChatMessage m, int index) async {
    if (m.myVote == index) return;
    try {
      await ref.read(workRepositoryProvider).votePoll(m.id, index);
      ref.invalidate(chatMessagesProvider(widget.channel.id));
    } catch (e) {
      _snack('$e', error: true);
    }
  }

  Future<void> _acknowledge(ChatMessage m) async {
    try {
      await ref.read(workRepositoryProvider).acknowledgeMessage(m.id);
      ref.invalidate(chatMessagesProvider(widget.channel.id));
    } catch (e) {
      _snack('$e', error: true);
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
          IconButton(
            tooltip: 'Search messages',
            icon: const Icon(Icons.search_rounded),
            onPressed: () => Navigator.of(context).push(
              MaterialPageRoute<void>(
                builder: (_) => _SearchScreen(channelId: widget.channel.id),
              ),
            ),
          ),
          IconButton(
            tooltip: 'Pinned messages',
            icon: const Icon(Icons.push_pin_outlined),
            onPressed: () => Navigator.of(context).push(
              MaterialPageRoute<void>(
                builder: (_) => _PinnedScreen(channelId: widget.channel.id),
              ),
            ),
          ),
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

                _scheduleMarkRead(messages);

                // The parent of a reply, looked up once so each bubble can show
                // what it answers without each one doing its own search.
                final byId = {for (final m in messages) m.id: m};

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
                    parent: ordered[i].parentId == null
                        ? null
                        : byId[ordered[i].parentId],
                    mine: ordered[i].senderId == me,
                    canDelete: ordered[i].senderId == me,
                    showSender: !widget.channel.direct,
                    onReact: (emoji) => _react(ordered[i].id, emoji),
                    onPin: () => _pin(ordered[i]),
                    onReply: () => setState(() => _replyingTo = ordered[i]),
                    onDelete: () => _delete(ordered[i]),
                    onVote: (index) => _vote(ordered[i], index),
                    onAck: () => _acknowledge(ordered[i]),
                    onOpenAttachment: _openAttachment,
                  ),
                );
              },
            ),
          ),
          _Composer(
            controller: _composer,
            sending: _sending || _pickBusy,
            onSend: _send,
            onAttach: _attach,
            onVoice: _toggleVoice,
            onPoll: _openPollBuilder,
            recording: _recording,
            recordSecs: _recordSecs,
            replyingTo: _replyingTo,
            onCancelReply: () => setState(() => _replyingTo = null),
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
    required this.parent,
    required this.mine,
    required this.canDelete,
    required this.showSender,
    required this.onReact,
    required this.onPin,
    required this.onReply,
    required this.onDelete,
    required this.onVote,
    required this.onAck,
    required this.onOpenAttachment,
  });

  final ChatMessage message;
  final ChatMessage? parent;
  final bool mine;
  final bool canDelete;
  final bool showSender;
  final void Function(String emoji) onReact;
  final VoidCallback onPin;
  final VoidCallback onReply;
  final VoidCallback onDelete;
  final void Function(int optionIndex) onVote;
  final VoidCallback onAck;
  final void Function(String path) onOpenAttachment;

  Future<void> _menu(BuildContext context) async {
    final deleted = message.deleted;
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
            if (!deleted) ...[
              ListTile(
                leading: const Icon(Icons.reply_rounded),
                title: const Text('Reply'),
                onTap: () {
                  Navigator.of(sheet).pop();
                  onReply();
                },
              ),
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
              if (canDelete)
                ListTile(
                  leading: Icon(
                    Icons.delete_outline_rounded,
                    color: Theme.of(context).colorScheme.error,
                  ),
                  title: Text(
                    'Delete',
                    style: TextStyle(
                      color: Theme.of(context).colorScheme.error,
                    ),
                  ),
                  onTap: () {
                    Navigator.of(sheet).pop();
                    onDelete();
                  },
                ),
            ],
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
                  maxWidth: MediaQuery.of(context).size.width * 0.82,
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
                    // What this message answers.
                    if (parent != null && !parent!.deleted)
                      Padding(
                        padding: const EdgeInsets.only(bottom: 6),
                        child: Container(
                          padding: const EdgeInsets.symmetric(
                            horizontal: 9,
                            vertical: 5,
                          ),
                          decoration: BoxDecoration(
                            color: (mine ? scheme.onPrimary : scheme.primary)
                                .withValues(alpha: 0.12),
                            borderRadius: BorderRadius.circular(8),
                          ),
                          child: Row(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              Icon(
                                Icons.reply_rounded,
                                size: 13,
                                color: mine
                                    ? scheme.onPrimary
                                    : scheme.onSurfaceVariant,
                              ),
                              const SizedBox(width: 5),
                              Flexible(
                                child: Text(
                                  '${parent!.senderName ?? 'Reply'}'
                                  '${parent!.content.trim().isEmpty ? '' : ': ${parent!.content.trim()}'}',
                                  maxLines: 1,
                                  overflow: TextOverflow.ellipsis,
                                  style: Theme.of(context)
                                      .textTheme
                                      .labelSmall
                                      ?.copyWith(
                                        color: mine
                                            ? scheme.onPrimary
                                            : scheme.onSurfaceVariant,
                                      ),
                                ),
                              ),
                            ],
                          ),
                        ),
                      ),
                    if (message.content.trim().isNotEmpty)
                      Text(
                        message.content,
                        style: TextStyle(
                          color: mine ? scheme.onPrimary : scheme.onSurface,
                          height: 1.35,
                        ),
                      ),
                    if (message.isPoll)
                      Padding(
                        padding: const EdgeInsets.only(top: 6),
                        child: _PollCard(
                          message: message,
                          mine: mine,
                          onVote: onVote,
                        ),
                      ),
                    if (message.attachmentPaths.isNotEmpty)
                      Padding(
                        padding: const EdgeInsets.only(top: 6),
                        child: _AttachmentBlock(
                          paths: message.attachmentPaths,
                          mine: mine,
                          onOpen: onOpenAttachment,
                        ),
                      ),
                    if (message.audioPath != null)
                      Padding(
                        padding: const EdgeInsets.only(top: 4),
                        child: _VoiceNote(url: fileUrl(message.audioPath!)),
                      ),
                    if (message.requiresAck)
                      Padding(
                        padding: const EdgeInsets.only(top: 6),
                        child: Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Icon(
                              message.acknowledgedByMe
                                  ? Icons.task_alt_rounded
                                  : Icons.assignment_turned_in_outlined,
                              size: 15,
                              color: mine ? scheme.onPrimary : scheme.primary,
                            ),
                            const SizedBox(width: 5),
                            Text(
                              message.acknowledgedByMe
                                  ? 'Confirmed · ${message.ackCount}'
                                  : '${message.ackCount} confirmed',
                              style: Theme.of(context).textTheme.labelSmall
                                  ?.copyWith(
                                    color: mine
                                        ? scheme.onPrimary
                                        : scheme.onSurfaceVariant,
                                  ),
                            ),
                            if (!message.acknowledgedByMe)
                              TextButton(
                                onPressed: onAck,
                                style: TextButton.styleFrom(
                                  minimumSize: const Size(0, 32),
                                  padding: const EdgeInsets.symmetric(
                                    horizontal: 8,
                                  ),
                                  foregroundColor: mine
                                      ? scheme.onPrimary
                                      : scheme.primary,
                                ),
                                child: const Text('Confirm read'),
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
                                    color: (mine
                                            ? scheme.onPrimary
                                            : scheme.primary)
                                        .withValues(
                                      // The reader's own sits brighter, so a
                                      // glance says whether they have answered it.
                                      alpha: message.myReactions
                                              .contains(entry.key)
                                          ? 0.30
                                          : 0.12,
                                    ),
                                    borderRadius: BorderRadius.circular(12),
                                  ),
                                  child: Text(
                                    '${entry.key} ${entry.value}',
                                    style: TextStyle(
                                      fontSize: 11,
                                      color: mine
                                          ? scheme.onPrimary
                                          : scheme.onSurface,
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
                                color: (mine
                                        ? scheme.onPrimary
                                        : scheme.onSurfaceVariant)
                                    .withValues(alpha: 0.8),
                              ),
                            ),
                          if (message.sentAt != null)
                            Text(
                              DateFormat('h:mm a').format(message.sentAt!),
                              style: Theme.of(context).textTheme.labelSmall
                                  ?.copyWith(
                                    fontSize: 10,
                                    color: (mine
                                            ? scheme.onPrimary
                                            : scheme.onSurfaceVariant)
                                        .withValues(alpha: 0.7),
                                  ),
                            ),
                          // Delivery mark on the sender's own message: one
                          // tick once it is out, two once somebody has seen it.
                          if (mine)
                            Padding(
                              padding: const EdgeInsets.only(left: 4),
                              child: Icon(
                                message.readCount > 0
                                    ? Icons.done_all_rounded
                                    : Icons.done_rounded,
                                size: 13,
                                color: (scheme.onPrimary).withValues(alpha: 0.8),
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

/// Files sent with a message: images as thumbnails, everything else as a chip.
class _AttachmentBlock extends StatelessWidget {
  const _AttachmentBlock({
    required this.paths,
    required this.mine,
    required this.onOpen,
  });

  final List<String> paths;
  final bool mine;
  final void Function(String path) onOpen;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final images = paths.where(_isImagePath).toList();
    final others = paths.where((p) => !_isImagePath(p)).toList();

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        if (images.isNotEmpty)
          Wrap(
            spacing: 6,
            runSpacing: 6,
            children: [
              for (final p in images)
                GestureDetector(
                  onTap: () => onOpen(p),
                  child: ClipRRect(
                    borderRadius: BorderRadius.circular(10),
                    child: CachedNetworkImage(
                      imageUrl: fileUrl(p),
                      width: 130,
                      height: 130,
                      fit: BoxFit.cover,
                      placeholder: (_, __) => Container(
                        width: 130,
                        height: 130,
                        color: scheme.surfaceContainerHighest,
                        child: const Icon(Icons.image_outlined),
                      ),
                      errorWidget: (_, __, ___) => Container(
                        width: 130,
                        height: 130,
                        color: scheme.surfaceContainerHighest,
                        child: const Icon(Icons.broken_image_outlined),
                      ),
                    ),
                  ),
                ),
            ],
          ),
        for (final p in others)
          Padding(
            padding: EdgeInsets.only(top: images.isEmpty ? 0 : 6),
            child: InkWell(
              borderRadius: BorderRadius.circular(8),
              onTap: () => onOpen(p),
              child: Container(
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 7),
                decoration: BoxDecoration(
                  color: (mine ? scheme.onPrimary : scheme.primary)
                      .withValues(alpha: 0.12),
                  borderRadius: BorderRadius.circular(8),
                ),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Icon(
                      Icons.insert_drive_file_outlined,
                      size: 16,
                      color: mine ? scheme.onPrimary : scheme.onSurfaceVariant,
                    ),
                    const SizedBox(width: 7),
                    Flexible(
                      child: Text(
                        p.split('/').last,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: Theme.of(context).textTheme.labelSmall?.copyWith(
                              color: mine
                                  ? scheme.onPrimary
                                  : scheme.onSurfaceVariant,
                            ),
                      ),
                    ),
                    const SizedBox(width: 5),
                    Icon(
                      Icons.open_in_new_rounded,
                      size: 13,
                      color: mine ? scheme.onPrimary : scheme.onSurfaceVariant,
                    ),
                  ],
                ),
              ),
            ),
          ),
      ],
    );
  }
}

/// A voice note: tap to play, tap again to stop.
class _VoiceNote extends StatefulWidget {
  const _VoiceNote({required this.url});

  final String url;

  @override
  State<_VoiceNote> createState() => _VoiceNoteState();
}

class _VoiceNoteState extends State<_VoiceNote> {
  final _player = AudioPlayer();
  bool _playing = false;
  bool _loading = false;
  Duration? _duration;

  @override
  void initState() {
    super.initState();
    _player.processingStateStream.listen((s) {
      if (s == ProcessingState.completed && mounted) {
        setState(() => _playing = false);
      }
    });
  }

  @override
  void dispose() {
    unawaited(_player.dispose());
    super.dispose();
  }

  Future<void> _toggle() async {
    if (_playing) {
      await _player.pause();
      if (mounted) setState(() => _playing = false);
      return;
    }
    setState(() => _loading = true);
    try {
      if (_duration == null) {
        await _player.setUrl(widget.url);
        _duration = _player.duration;
      }
      await _player.play();
      if (mounted) setState(() => _playing = true);
    } catch (_) {
      // A failed note is shown, not thrown: the conversation must not die
      // because one recording would not load.
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final d = _duration;
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        InkWell(
          borderRadius: BorderRadius.circular(18),
          onTap: _toggle,
          child: Container(
            padding: const EdgeInsets.all(6),
            decoration: BoxDecoration(
              color: scheme.primary.withValues(alpha: 0.14),
              shape: BoxShape.circle,
            ),
            child: Icon(
              _loading
                  ? Icons.hourglass_top_rounded
                  : _playing
                      ? Icons.stop_rounded
                      : Icons.play_arrow_rounded,
              size: 18,
              color: scheme.primary,
            ),
          ),
        ),
        if (d != null) ...[
          const SizedBox(width: 6),
          Text(
            _durationLabel(d),
            style: Theme.of(context).textTheme.labelSmall?.copyWith(
                  color: scheme.onSurfaceVariant,
                ),
          ),
        ],
        const SizedBox(width: 4),
        Icon(Icons.graphic_eq_rounded, size: 14, color: scheme.primary),
      ],
    );
  }

  static String _durationLabel(Duration d) {
    final s = d.inSeconds;
    if (s < 60) return '$s s';
    return '${s ~/ 60}m ${s % 60}s';
  }
}

/// A poll: every option, its share, and a tap to answer.
class _PollCard extends StatelessWidget {
  const _PollCard({
    required this.message,
    required this.mine,
    required this.onVote,
  });

  final ChatMessage message;
  final bool mine;
  final void Function(int optionIndex) onVote;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final fg = mine ? scheme.onPrimary : scheme.onSurface;
    final dim = (mine ? scheme.onPrimary : scheme.onSurfaceVariant)
        .withValues(alpha: 0.75);

    return Container(
      width: 230,
      padding: const EdgeInsets.all(10),
      decoration: BoxDecoration(
        color: (mine ? scheme.onPrimary : scheme.primary).withValues(alpha: 0.10),
        borderRadius: BorderRadius.circular(10),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          for (var i = 0; i < message.pollOptions.length; i++) ...[
            if (i > 0) const SizedBox(height: 7),
            InkWell(
              borderRadius: BorderRadius.circular(7),
              onTap: () => onVote(i),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Expanded(
                        child: Text(
                          message.pollOptions[i],
                          style: TextStyle(
                            fontSize: 13,
                            fontWeight:
                                message.myVote == i ? FontWeight.w700 : FontWeight.w500,
                            color: fg,
                          ),
                        ),
                      ),
                      Text(
                        message.myVote == i ? '· your vote' : '',
                        style: TextStyle(fontSize: 11, color: scheme.primary),
                      ),
                    ],
                  ),
                  const SizedBox(height: 3),
                  Row(
                    children: [
                      Expanded(
                        child: ClipRRect(
                          borderRadius: BorderRadius.circular(3),
                          child: LinearProgressIndicator(
                            value: message.pollShare(i),
                            minHeight: 5,
                            backgroundColor: (mine
                                    ? scheme.onPrimary
                                    : scheme.primary)
                                .withValues(alpha: 0.16),
                          ),
                        ),
                      ),
                      const SizedBox(width: 7),
                      Text(
                        '${message.pollVotes[i]} · '
                        '${(message.pollShare(i) * 100).round()}%',
                        style: TextStyle(fontSize: 11, color: dim),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ],
          const SizedBox(height: 8),
          Text(
            message.totalVotes == 0
                ? 'No votes yet'
                : '${message.totalVotes} '
                    '${message.totalVotes == 1 ? 'vote' : 'votes'}',
            style: TextStyle(fontSize: 11, color: dim),
          ),
        ],
      ),
    );
  }
}

/// The composer: text, attachments, voice notes, polls, and a reply strip.
class _Composer extends StatelessWidget {
  const _Composer({
    required this.controller,
    required this.sending,
    required this.onSend,
    required this.onAttach,
    required this.onVoice,
    required this.onPoll,
    required this.recording,
    required this.recordSecs,
    required this.replyingTo,
    required this.onCancelReply,
    required this.readOnly,
  });

  final TextEditingController controller;
  final bool sending;
  final VoidCallback onSend;
  final VoidCallback onAttach;
  final VoidCallback onVoice;
  final VoidCallback onPoll;
  final bool recording;
  final int recordSecs;
  final ChatMessage? replyingTo;
  final VoidCallback onCancelReply;
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
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            if (replyingTo != null)
              Padding(
                padding: const EdgeInsets.only(bottom: 6),
                child: Row(
                  children: [
                    Icon(Icons.reply_rounded, size: 16, color: scheme.primary),
                    const SizedBox(width: 6),
                    Expanded(
                      child: Text(
                        'Replying to ${replyingTo!.senderName ?? 'a message'}'
                        '${replyingTo!.content.trim().isEmpty ? '' : ': ${replyingTo!.content.trim()}'}',
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: Theme.of(context).textTheme.bodySmall,
                      ),
                    ),
                    IconButton(
                      visualDensity: VisualDensity.compact,
                      icon: const Icon(Icons.close_rounded, size: 18),
                      onPressed: onCancelReply,
                      tooltip: 'Cancel reply',
                    ),
                  ],
                ),
              ),
            Row(
              crossAxisAlignment: CrossAxisAlignment.end,
              children: [
                IconButton(
                  onPressed: sending ? null : onAttach,
                  icon: const Icon(Icons.attach_file_rounded),
                  tooltip: 'Attach',
                ),
                IconButton(
                  onPressed: sending ? null : onPoll,
                  icon: const Icon(Icons.poll_outlined),
                  tooltip: 'Poll',
                ),
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
                if (recording)
                  Padding(
                    padding: const EdgeInsets.only(right: 8, bottom: 10),
                    child: Text(
                      '$recordSecs s',
                      style: TextStyle(
                        color: Theme.of(context).colorScheme.error,
                        fontWeight: FontWeight.w700,
                        fontFeatures: [FontFeature.tabularFigures()],
                      ),
                    ),
                  ),
                IconButton(
                  onPressed: sending ? null : onVoice,
                  icon: Icon(
                    recording ? Icons.stop_circle_outlined : Icons.mic_none_rounded,
                    color: recording ? scheme.error : null,
                  ),
                  tooltip: recording ? 'Stop and send' : 'Voice note',
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
          ],
        ),
      ),
    );
  }
}

/// Two to six options; Send posts them as a poll.
class _PollBuilderSheet extends StatefulWidget {
  const _PollBuilderSheet();

  @override
  State<_PollBuilderSheet> createState() => _PollBuilderSheetState();
}

class _PollBuilderSheetState extends State<_PollBuilderSheet> {
  static const _minOptions = 2;
  static const _maxOptions = 6;

  final List<TextEditingController> _options = [
    TextEditingController(),
    TextEditingController(),
  ];

  @override
  void dispose() {
    for (final c in _options) {
      c.dispose();
    }
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final filled = _options
        .where((c) => c.text.trim().isNotEmpty)
        .length;

    return SafeArea(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(20, 20, 20, 16),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(
              'New poll',
              style: Theme.of(
                context,
              ).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w700),
            ),
            const SizedBox(height: 14),
            for (var i = 0; i < _options.length; i++) ...[
              if (i > 0) const SizedBox(height: 10),
              TextField(
                controller: _options[i],
                decoration: InputDecoration(
                  hintText: 'Option ${i + 1}',
                  suffixIcon: _options.length > _minOptions
                      ? IconButton(
                          icon: const Icon(Icons.close_rounded, size: 18),
                          onPressed: () =>
                              setState(() => _options.removeAt(i)),
                        )
                      : null,
                ),
                onChanged: (_) => setState(() {}),
              ),
            ],
            const SizedBox(height: 10),
            Row(
              children: [
                if (_options.length < _maxOptions)
                  TextButton.icon(
                    onPressed: () => setState(
                      () => _options.add(TextEditingController()),
                    ),
                    icon: const Icon(Icons.add_rounded, size: 18),
                    label: const Text('Add option'),
                  ),
                const Spacer(),
                Text(
                  '$filled/$_maxOptions',
                  style: Theme.of(context)
                      .textTheme
                      .labelSmall
                      ?.copyWith(color: scheme.onSurfaceVariant),
                ),
              ],
            ),
            const SizedBox(height: 8),
            FilledButton(
              onPressed: filled >= _minOptions
                  ? () => Navigator.of(context).pop(
                      _options
                          .map((c) => c.text.trim())
                          .where((t) => t.isNotEmpty)
                          .toList(),
                    )
                  : null,
              child: const Text('Post poll'),
            ),
          ],
        ),
      ),
    );
  }
}

/// A full-screen photo: pinch to zoom, tap to leave.
class _ImageViewer extends StatelessWidget {
  const _ImageViewer({required this.url});

  final String url;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
      appBar: AppBar(backgroundColor: Colors.black, foregroundColor: Colors.white),
      body: Center(
        child: InteractiveViewer(
          maxScale: 5,
          child: CachedNetworkImage(
            imageUrl: url,
            fit: BoxFit.contain,
            placeholder: (_, __) => const Center(
              child: CircularProgressIndicator(),
            ),
            errorWidget: (_, __, ___) => const Center(
              child: Icon(Icons.broken_image_outlined, color: Colors.white54),
            ),
          ),
        ),
      ),
    );
  }
}

/// Search inside one conversation.
class _SearchScreen extends ConsumerStatefulWidget {
  const _SearchScreen({required this.channelId});

  final int channelId;

  @override
  ConsumerState<_SearchScreen> createState() => _SearchScreenState();
}

class _SearchScreenState extends ConsumerState<_SearchScreen> {
  final _query = TextEditingController();
  bool _searching = false;
  String? _error;
  List<ChatMessage> _results = const [];

  @override
  void dispose() {
    _query.dispose();
    super.dispose();
  }

  Future<void> _run(String raw) async {
    final q = raw.trim();
    if (q.isEmpty) {
      setState(() {
        _results = const [];
        _error = null;
      });
      return;
    }
    setState(() {
      _searching = true;
      _error = null;
    });
    try {
      final results = await ref
          .read(workRepositoryProvider)
          .searchMessages(widget.channelId, q);
      if (!mounted) return;
      setState(() => _results = results);
    } catch (e) {
      if (!mounted) return;
      setState(() => _error = '$e');
    } finally {
      if (mounted) setState(() => _searching = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;

    return Scaffold(
      appBar: AppBar(
        title: TextField(
          controller: _query,
          autofocus: true,
          textInputAction: TextInputAction.search,
          decoration: const InputDecoration(hintText: 'Search messages'),
          onSubmitted: _run,
        ),
        actions: [
          IconButton(
            onPressed: () => _run(_query.text),
            icon: const Icon(Icons.search_rounded),
          ),
        ],
      ),
      body: _searching
          ? const Center(child: CircularProgressIndicator())
          : _error != null
              ? ErrorState(message: _error!, onRetry: () => _run(_query.text))
              : _results.isEmpty
                  ? const EmptyState(
                      icon: Icons.search_off_rounded,
                      title: 'Nothing found',
                      description: 'Type a word to search this conversation.',
                    )
                  : ListView.separated(
                      padding: const EdgeInsets.symmetric(vertical: 8),
                      itemCount: _results.length,
                      separatorBuilder: (_, __) =>
                          const Divider(height: 1, indent: 16),
                      itemBuilder: (context, i) {
                        final m = _results[i];
                        return ListTile(
                          leading: CircleAvatar(
                            radius: 16,
                            backgroundColor: scheme.primary.withValues(alpha: 0.14),
                            child: Text(
                              (m.senderName ?? '?').characters.first.toUpperCase(),
                              style: TextStyle(
                                fontSize: 13,
                                fontWeight: FontWeight.w700,
                                color: scheme.primary,
                              ),
                            ),
                          ),
                          title: Text(
                            m.content.trim().isEmpty
                                ? (m.isPoll
                                    ? '📊 Poll'
                                    : m.audioPath != null
                                        ? '🎤 Voice note'
                                        : '📎 Attachment')
                                : m.content,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                          ),
                          subtitle: Text(
                            [
                              if (m.senderName != null) m.senderName!,
                              if (m.sentAt != null)
                                DateFormat('d MMM, h:mm a').format(m.sentAt!),
                            ].join(' · '),
                          ),
                        );
                      },
                    ),
    );
  }
}

/// Everything pinned in one conversation.
class _PinnedScreen extends ConsumerWidget {
  const _PinnedScreen({required this.channelId});

  final int channelId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(pinnedMessagesProvider(channelId));

    return Scaffold(
      appBar: AppBar(title: const Text('Pinned messages')),
      body: RefreshIndicator(
        onRefresh: () async => ref.invalidate(pinnedMessagesProvider(channelId)),
        child: async.when(
          loading: () => const LoadingList(),
          error: (e, _) => ListView(
            physics: const AlwaysScrollableScrollPhysics(),
            children: [
              const SizedBox(height: 80),
              ErrorState(
                message: '$e',
                onRetry: () => ref.invalidate(pinnedMessagesProvider(channelId)),
              ),
            ],
          ),
          data: (messages) => messages.isEmpty
              ? ListView(
                  physics: const AlwaysScrollableScrollPhysics(),
                  children: const [
                    SizedBox(height: 80),
                    EmptyState(
                      icon: Icons.push_pin_outlined,
                      title: 'Nothing pinned',
                      description:
                          'Pinned messages stay at the top of a conversation.',
                    ),
                  ],
                )
              : ListView.separated(
                  physics: const AlwaysScrollableScrollPhysics(),
                  padding: const EdgeInsets.symmetric(vertical: 8),
                  itemCount: messages.length,
                  separatorBuilder: (_, __) =>
                      const Divider(height: 1, indent: 16),
                  itemBuilder: (context, i) {
                    final m = messages[i];
                    return ListTile(
                      leading: const Icon(Icons.push_pin_rounded, size: 20),
                      title: Text(
                        m.content.trim().isEmpty
                            ? (m.isPoll
                                ? '📊 Poll'
                                : m.audioPath != null
                                    ? '🎤 Voice note'
                                    : '📎 Attachment')
                            : m.content,
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                      ),
                      subtitle: Text(
                        [
                          if (m.senderName != null) m.senderName!,
                          if (m.sentAt != null)
                            DateFormat('d MMM, h:mm a').format(m.sentAt!),
                        ].join(' · '),
                      ),
                    );
                  },
                ),
        ),
      ),
    );
  }
}
