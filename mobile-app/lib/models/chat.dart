/// A conversation: a channel, or a one-to-one.
///
/// Mirrors `CommunityDTOs.GroupResponse`. Only the fields a phone shows are
/// read — the payload carries more, and reading fields nothing renders would be
/// work that has to be kept correct for no visible reason.
class ChatChannel {
  const ChatChannel({
    required this.id,
    required this.name,
    this.description,
    this.isAnnouncement = false,
    this.direct = false,
    this.partnerId,
    this.partnerPhotoPath,
  });

  final int id;
  final String name;
  final String? description;

  /// An announcement channel: HR posts, everybody reads.
  final bool isAnnouncement;

  /// A private one-to-one rather than a group.
  final bool direct;
  final int? partnerId;
  final String? partnerPhotoPath;

  static ChatChannel fromJson(Map<String, dynamic> json) => ChatChannel(
        id: (json['id'] as num?)?.toInt() ?? 0,
        // A conversation with no name still has to be openable — a direct chat
        // whose partner was removed is the case that produces one.
        name: json['name']?.toString().trim().isNotEmpty == true
            ? json['name'].toString()
            : 'Conversation',
        description: _blank(json['description']),
        // The payload names it `announcement` (group responses) and
        // `isAnnouncement` (create responses); either spelling is read.
        isAnnouncement: json['announcement'] == true ||
            json['isAnnouncement'] == true,
        direct: json['direct'] == true,
        partnerId: (json['partnerId'] as num?)?.toInt(),
        partnerPhotoPath: _blank(json['partnerPhotoPath']),
      );

  static String? _blank(dynamic v) {
    final t = v?.toString().trim();
    return (t == null || t.isEmpty) ? null : t;
  }
}

/// One message in a conversation.
class ChatMessage {
  const ChatMessage({
    required this.id,
    required this.senderId,
    required this.content,
    this.senderName,
    this.sentAt,
    this.deleted = false,
    this.pinned = false,
    this.replyCount = 0,
    this.readCount = 0,
    this.attachments,
    this.audioPath,
    this.reactions = const {},
    this.myReactions = const [],
    this.parentId,
    this.requiresAck = false,
    this.ackCount = 0,
    this.acknowledgedByMe = false,
    this.pollOptions = const [],
    this.pollVotes = const [],
    this.myVote,
    this.senderPhotoPath,
  });

  final int id;
  final int senderId;
  final String content;
  final String? senderName;
  final DateTime? sentAt;
  final bool deleted;
  final bool pinned;
  final int replyCount;
  final int readCount;
  final String? attachments;
  final String? audioPath;

  /// The message this one answers, when it is a reply.
  final int? parentId;

  /// An announcement that asks readers to confirm they have seen it.
  final bool requiresAck;
  final int ackCount;
  final bool acknowledgedByMe;

  /// A poll: the labels, the tally per label, and this reader's choice.
  final List<String> pollOptions;
  final List<int> pollVotes;
  final int? myVote;

  final String? senderPhotoPath;

  /// Emoji to how many people used it.
  final Map<String, int> reactions;

  /// The emoji the person reading this has used, so their own can be shown as
  /// already pressed rather than as one more they could add.
  final List<String> myReactions;

  bool get hasAttachment =>
      (attachments != null && attachments!.trim().isNotEmpty) || audioPath != null;

  bool get isPoll => pollOptions.isNotEmpty;

  int get totalVotes => pollVotes.fold(0, (a, b) => a + b);

  /// The stored paths, one per attachment. The payload carries them as one
  /// comma-separated string; each is served from `/api/files/<path>`.
  List<String> get attachmentPaths {
    if (attachments == null || attachments!.trim().isEmpty) return const [];
    return attachments!
        .split(',')
        .map((p) => p.trim())
        .where((p) => p.isNotEmpty)
        .toList();
  }

  double pollShare(int index) {
    if (totalVotes == 0) return 0;
    final v = index >= 0 && index < pollVotes.length ? pollVotes[index] : 0;
    return v / totalVotes;
  }

  static ChatMessage fromJson(Map<String, dynamic> json) => ChatMessage(
        // messageId, not id — the payload names it differently from every other
        // DTO on this backend, and reading `id` would silently give every
        // message the same key and break the list.
        id: (json['messageId'] as num?)?.toInt() ?? 0,
        senderId: (json['senderId'] as num?)?.toInt() ?? 0,
        senderName: ChatChannel._blank(json['senderName']),
        content: json['content']?.toString() ?? '',
        sentAt: DateTime.tryParse(json['sentAt']?.toString() ?? ''),
        deleted: json['deleted'] == true,
        pinned: json['pinned'] == true,
        replyCount: (json['replyCount'] as num?)?.toInt() ?? 0,
        readCount: (json['readCount'] as num?)?.toInt() ?? 0,
        attachments: ChatChannel._blank(json['attachments']),
        audioPath: ChatChannel._blank(json['audioPath']),
        reactions: _counts(json['reactions']),
        myReactions: _strings(json['myReactions']),
        parentId: (json['parentId'] as num?)?.toInt(),
        requiresAck: json['requiresAck'] == true,
        ackCount: (json['ackCount'] as num?)?.toInt() ?? 0,
        acknowledgedByMe: json['acknowledgedByMe'] == true,
        pollOptions: _strings(json['pollOptions']),
        pollVotes: _ints(json['pollVotes']),
        myVote: (json['myVote'] as num?)?.toInt(),
        senderPhotoPath: ChatChannel._blank(json['senderPhotoPath']),
      );

  static Map<String, int> _counts(dynamic raw) {
    if (raw is! Map) return const {};
    final out = <String, int>{};
    raw.forEach((k, v) {
      final n = (v is num) ? v.toInt() : int.tryParse(v?.toString() ?? '');
      // A reaction nobody holds is not a reaction. Keeping zeroes would render
      // an empty chip that cannot be explained.
      if (n != null && n > 0) out[k.toString()] = n;
    });
    return out;
  }

  static List<String> _strings(dynamic raw) {
    if (raw is! List) return const [];
    return raw.map((e) => e.toString()).where((e) => e.isNotEmpty).toList();
  }

  static List<int> _ints(dynamic raw) {
    if (raw is! List) return const [];
    return raw
        .map((e) => e is num ? e.toInt() : int.tryParse(e?.toString() ?? ''))
        .whereType<int>()
        .toList();
  }
}
