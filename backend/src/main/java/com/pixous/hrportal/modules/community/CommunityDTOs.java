package com.pixous.hrportal.modules.community;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

public class CommunityDTOs {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChatMessagePayload {
        private Long messageId;
        private Long communityId;
        private Long senderId;
        private String senderName;
        private String content;
        private LocalDateTime sentAt;
        /** Voice message audio path (null for plain text). */
        private String audioPath;
        /** Comma-separated attachment paths (null when there are none). */
        private String attachments;
        /** True when this payload signals that the message was deleted. */
        private boolean deleted;

        // ---- the newer chat features ----
        /** The message this one answers; null for a top-level post. */
        private Long parentId;
        /** How many replies hang off this message. */
        private int replyCount;
        private boolean pinned;
        private LocalDateTime pinnedAt;
        /** Emoji to the number of people who reacted with it. */
        private java.util.Map<String, Integer> reactions;
        /** The emoji the person reading this has used on it. */
        private java.util.List<String> myReactions;
        /** How many have seen it, and whether the reader is one of them. */
        private int readCount;
        /** An announcement that asks to be confirmed. */
        private boolean requiresAck;
        private int ackCount;
        private boolean acknowledgedByMe;
        /** Set while a message is still waiting for its time to come. */
        private LocalDateTime scheduledAt;
        /** A poll's choices, the vote count for each, and the reader's own vote. */
        private java.util.List<String> pollOptions;
        private java.util.List<Integer> pollVotes;
        private Integer myVote;
    }

    @Data
    public static class SendMessageRequest {
        private String content;
        /** Reply to this message, when it is a reply. */
        private Long parentId;
        /** Post at this time instead of now (ISO date-time). */
        private String scheduledAt;
        /** Ask readers to confirm — announcement channels only. */
        private boolean requiresAck;
        /** Two or more labels turn the message into a poll. */
        private java.util.List<String> pollOptions;
    }

    @Data
    public static class CreateGroupRequest {
        private String name;
        private String description;
        private boolean isAnnouncement;
    }

    @Data
    public static class AddMemberRequest {
        private Long userId;
    }
    
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class GroupResponse {
        private Long id;
        private String name;
        private String description;
        private Long createdBy;
        private LocalDateTime createdAt;
        private boolean isAnnouncement;
        // Direct (1:1) chat metadata — null / false for normal community groups.
        private boolean direct;
        private Long partnerId;
        private String partnerPhotoPath;

        // Backward-compatible constructor for normal (non-direct) community groups.
        public GroupResponse(Long id, String name, String description, Long createdBy,
                             LocalDateTime createdAt, boolean isAnnouncement) {
            this(id, name, description, createdBy, createdAt, isAnnouncement, false, null, null);
        }
    }
}
