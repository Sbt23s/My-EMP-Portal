package com.pixous.hrportal.modules.community;

import com.pixous.hrportal.modules.user.User;
import com.pixous.hrportal.modules.user.UserRepository;
import com.pixous.hrportal.security.SecurityUtils;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/calls")
@RequiredArgsConstructor
public class CallController {

    private final SimpMessagingTemplate messagingTemplate;
    private final UserRepository userRepository;
    private final com.pixous.hrportal.modules.notification.NotificationService notificationService;
    private final CommunityService communityService;

    @PostMapping("/signal")
    public ResponseEntity<Void> sendSignal(@RequestBody CallSignalRequest request) {
        Long senderId = SecurityUtils.currentUserId();
        User sender = userRepository.findById(senderId).orElseThrow();
        
        CallSignalPayload payload = new CallSignalPayload();
        payload.setSenderId(senderId);
        payload.setSenderName(sender.getName());
        payload.setType(request.getType());
        payload.setData(request.getData());
        
        String destination = "/topic/calls/" + request.getRecipientId();
        log.info("Routing call signal of type {} from {} (ID {}) to destination: {}", 
                request.getType(), sender.getName(), senderId, destination);
        
        messagingTemplate.convertAndSend(destination, payload);

        // Send an in-app push notification when the call is initiated
        if ("calling".equals(request.getType())) {
            try {
                notificationService.createAndPush(
                        request.getRecipientId(),
                        "Incoming Call",
                        sender.getName() + " is calling you...",
                        "CALL",
                        "/chat"
                );
            } catch (Exception e) {
                log.error("Failed to send call notification", e);
            }
        }

        return ResponseEntity.ok().build();
    }

    /**
     * Records a finished call in the two people's own conversation, so the chat
     * shows that it happened — a missed call is otherwise invisible once the
     * ringing stops. Written by whichever side ended it; the direct room is
     * found or created exactly as opening the chat would.
     */
    @PostMapping("/log")
    public ResponseEntity<Void> logCall(@RequestBody CallLogRequest request) {
        Long senderId = SecurityUtils.currentUserId();
        if (request.getRecipientId() == null) return ResponseEntity.badRequest().build();

        String kind = Boolean.TRUE.equals(request.getVideo()) ? "Video call" : "Voice call";
        String outcome = switch (String.valueOf(request.getOutcome())) {
            case "MISSED" -> "Missed";
            case "DECLINED" -> "Declined";
            default -> duration(request.getSeconds());
        };

        try {
            Long roomId = communityService.openDirect(senderId, request.getRecipientId()).getId();
            communityService.sendMessage(roomId, senderId, "📞 " + kind + " · " + outcome);
        } catch (Exception e) {
            // A call log is a courtesy; failing to write it must not surface as a
            // failed call to somebody who has just hung up.
            log.warn("Could not record a call between {} and {}", senderId, request.getRecipientId(), e);
        }
        return ResponseEntity.ok().build();
    }

    private static String duration(Integer seconds) {
        int s = seconds == null ? 0 : Math.max(0, seconds);
        if (s < 60) return s + "s";
        return (s / 60) + "m " + (s % 60) + "s";
    }

    @Data
    public static class CallSignalRequest {
        private Long recipientId;
        private String type;
        private Object data;
    }

    @Data
    public static class CallLogRequest {
        private Long recipientId;
        /** MISSED, DECLINED or ENDED. */
        private String outcome;
        private Boolean video;
        private Integer seconds;
    }

    @Data
    public static class CallSignalPayload {
        private Long senderId;
        private String senderName;
        private String type;
        private Object data;
    }
}
