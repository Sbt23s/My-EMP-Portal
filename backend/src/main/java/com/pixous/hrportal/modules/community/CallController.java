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

    /**
     * The ICE servers a call may use: STUN always, plus TURN when the shared
     * secret is configured (see setup-turn.sh on the server).
     *
     * The mobile client reads this endpoint and falls back to public STUN when
     * it is absent, so this is purely additive. TURN credentials are minted
     * time-limited from the shared secret rather than stored: a static password
     * shipped in an APK would be a password published to everybody who installs
     * it.
     */
    @GetMapping("/ice-servers")
    public Map<String, Object> iceServers() {
        String turnSecret = System.getenv("TURN_SECRET");

        List<Map<String, Object>> servers = new java.util.ArrayList<>();
        servers.add(Map.of("urls", "stun:stun.l.google.com:19302"));
        servers.add(Map.of("urls", "stun:stun1.l.google.com:19302"));

        if (turnSecret != null && !turnSecret.isBlank()) {
            String domain = System.getenv().getOrDefault("TURN_DOMAIN", "pixoushrportal.pixous.info");
            // coturn REST API: username "<expiry-epoch>:<user>", credential is
            // base64(HMAC-SHA1(secret, username)). Expiry ten minutes out, so a
            // call that takes a while to connect is never cut off by its own ICE
            // configuration.
            long expires = System.currentTimeMillis() / 1000 + 600;
            String username = expires + ":mobile";
            try {
                javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA1");
                mac.init(new javax.crypto.spec.SecretKeySpec(
                        turnSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA1"));
                String credential = java.util.Base64.getEncoder().encodeToString(
                        mac.doFinal(username.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                servers.add(Map.of(
                        "urls", java.util.List.of(
                                "turn:" + domain + ":3478?transport=udp",
                                "turns:" + domain + ":5349?transport=tcp"),
                        "username", username,
                        "credential", credential));
            } catch (Exception e) {
                log.warn("Could not mint TURN credentials", e);
            }
        }

        return Map.of("iceServers", servers);
    }

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
