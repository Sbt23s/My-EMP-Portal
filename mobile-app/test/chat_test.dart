import 'package:flutter_test/flutter_test.dart';
import 'package:hr_portal_mobile/models/chat.dart';

void main() {
  group('ChatMessage.fromJson', () {
    test('reads the id from messageId, not id', () {
      // The payload names it differently from every other DTO on this backend.
      // Reading `id` would give every message the same key, and a list keyed on
      // duplicates renders the wrong bubbles in the wrong places.
      final m = ChatMessage.fromJson({
        'messageId': 42,
        'senderId': 7,
        'content': 'Morning',
        'sentAt': '2026-08-15T09:15:00',
      });
      expect(m.id, 42);
      expect(m.senderId, 7);
      expect(m.content, 'Morning');
      expect(m.sentAt?.hour, 9);
    });

    test('a deleted message keeps its place instead of vanishing', () {
      final m = ChatMessage.fromJson({'messageId': 1, 'deleted': true});
      expect(m.deleted, isTrue);
    });

    test('an empty payload renders rather than throwing', () {
      // One bad row must not take the conversation down.
      final m = ChatMessage.fromJson({});
      expect(m.id, 0);
      expect(m.content, '');
      expect(m.sentAt, isNull);
      expect(m.hasAttachment, isFalse);
    });

    test('an attachment or a voice note both count as attached', () {
      expect(
        ChatMessage.fromJson({'messageId': 1, 'attachments': 'a.png'}).hasAttachment,
        isTrue,
      );
      expect(
        ChatMessage.fromJson({'messageId': 2, 'audioPath': 'v.m4a'}).hasAttachment,
        isTrue,
      );
      // Blank strings are absent, not content.
      expect(
        ChatMessage.fromJson({'messageId': 3, 'attachments': '   '}).hasAttachment,
        isFalse,
      );
    });
  });

  group('reactions', () {
    test('counts and the reader own set are read', () {
      final m = ChatMessage.fromJson({
        'messageId': 1,
        'reactions': {'A': 3, 'B': 1},
        'myReactions': ['A'],
      });
      expect(m.reactions['A'], 3);
      expect(m.myReactions, contains('A'));
    });

    test('a reaction nobody holds is dropped', () {
      // A zero would render an empty chip that cannot be explained.
      final m = ChatMessage.fromJson({
        'messageId': 1,
        'reactions': {'A': 0, 'B': 2},
      });
      expect(m.reactions.containsKey('A'), isFalse);
      expect(m.reactions['B'], 2);
    });

    test('counts sent as strings still parse', () {
      final m = ChatMessage.fromJson({'messageId': 1, 'reactions': {'A': '4'}});
      expect(m.reactions['A'], 4);
    });

    test('no reactions is an empty map, never null', () {
      final m = ChatMessage.fromJson({'messageId': 1});
      expect(m.reactions, isEmpty);
      expect(m.myReactions, isEmpty);
    });

    test('a pinned message is marked', () {
      expect(ChatMessage.fromJson({'messageId': 1, 'pinned': true}).pinned, isTrue);
    });
  });

  group('chat parity features', () {
    test('read count drives the delivery ticks', () {
      final unread = ChatMessage.fromJson({'messageId': 1, 'readCount': 0});
      final seen = ChatMessage.fromJson({'messageId': 2, 'readCount': 3});
      expect(unread.readCount, 0);
      expect(seen.readCount, 3);
    });

    test('a reply records its parent', () {
      final m = ChatMessage.fromJson({'messageId': 5, 'parentId': 2});
      expect(m.parentId, 2);
      expect(ChatMessage.fromJson({'messageId': 6}).parentId, isNull);
    });

    test('an announcement that asks for confirmation is read', () {
      final m = ChatMessage.fromJson({
        'messageId': 1,
        'requiresAck': true,
        'ackCount': 4,
        'acknowledgedByMe': true,
      });
      expect(m.requiresAck, isTrue);
      expect(m.ackCount, 4);
      expect(m.acknowledgedByMe, isTrue);
    });

    test('attachments split on commas and drop blanks', () {
      final m = ChatMessage.fromJson({
        'messageId': 1,
        'attachments': 'chat/1a.png, chat/2b.pdf,  ',
      });
      expect(m.attachmentPaths, ['chat/1a.png', 'chat/2b.pdf']);
      expect(ChatMessage.fromJson({'messageId': 2}).attachmentPaths, isEmpty);
    });

    test('a poll parses options, votes and the reader answer', () {
      final m = ChatMessage.fromJson({
        'messageId': 1,
        'pollOptions': ['Tea', 'Coffee', 'Neither'],
        'pollVotes': [3, 2, 1],
        'myVote': 1,
      });
      expect(m.isPoll, isTrue);
      expect(m.totalVotes, 6);
      expect(m.myVote, 1);
      expect(m.pollShare(0), closeTo(0.5, 0.001));
      expect(m.pollShare(2), closeTo(1 / 6, 0.001));
    });

    test('a poll with no votes has no share, not a division by zero', () {
      final m = ChatMessage.fromJson({
        'messageId': 1,
        'pollOptions': ['A', 'B'],
        'pollVotes': [0, 0],
      });
      expect(m.isPoll, isTrue);
      expect(m.totalVotes, 0);
      expect(m.pollShare(0), 0);
    });

    test('an attachment message without content is still a poll-safe empty', () {
      final m = ChatMessage.fromJson({'messageId': 1, 'content': ''});
      expect(m.isPoll, isFalse);
      expect(m.content, '');
    });
  });

  group('ChatChannel.fromJson', () {
    test('reads a direct conversation', () {
      final c = ChatChannel.fromJson({
        'id': 3,
        'name': 'Arun Kumar',
        'direct': true,
        'partnerId': 9,
      });
      expect(c.direct, isTrue);
      expect(c.partnerId, 9);
      expect(c.isAnnouncement, isFalse);
    });

    test('a nameless conversation is still openable', () {
      // A direct chat whose partner was removed produces one, and a blank tile
      // that cannot be tapped is worse than a generic label.
      expect(ChatChannel.fromJson({'id': 1}).name, 'Conversation');
      expect(ChatChannel.fromJson({'id': 1, 'name': '  '}).name, 'Conversation');
    });

    test('an announcement channel is marked as one', () {
      final c = ChatChannel.fromJson({'id': 2, 'name': 'Notices', 'isAnnouncement': true});
      expect(c.isAnnouncement, isTrue);
      // Which is what hides the composer — the server refuses replies there, and
      // finding that out after typing a paragraph is the failure being avoided.
    });
  });
}
