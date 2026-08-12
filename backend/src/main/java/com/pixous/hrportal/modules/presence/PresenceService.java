package com.pixous.hrportal.modules.presence;

import com.pixous.hrportal.modules.user.User;
import com.pixous.hrportal.modules.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Who is online, and when everybody else was last seen.
 *
 * <p>Being online means holding a live socket, so that half is kept in memory —
 * a table could only ever record a guess, and a stale row saying somebody is
 * online is worse than no answer. One person may have several sockets open (two
 * tabs, a phone), so sessions are counted: they go offline when the last one
 * closes, not the first.
 *
 * <p>The other half, "last seen", is written to the user row on every connect
 * and disconnect, because that answer has to survive a restart.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PresenceService {

    /** Socket session id to the person holding it. */
    private final Map<String, Long> sessionOwner = new ConcurrentHashMap<>();
    /** How many sockets each person currently holds. */
    private final Map<Long, Integer> openSockets = new ConcurrentHashMap<>();

    private final SimpMessagingTemplate messagingTemplate;
    private final UserRepository userRepository;

    /** A socket has connected. Announces the arrival only on the first one. */
    public void connected(String sessionId, Long userId) {
        if (sessionId == null || userId == null) return;
        if (sessionOwner.put(sessionId, userId) != null) return;
        boolean first = openSockets.merge(userId, 1, Integer::sum) == 1;
        touch(userId);
        if (first) announce(userId, true);
    }

    /** A socket has closed. Announces the departure only on the last one. */
    public void disconnected(String sessionId) {
        if (sessionId == null) return;
        Long userId = sessionOwner.remove(sessionId);
        if (userId == null) return;
        Integer left = openSockets.compute(userId, (k, v) -> (v == null || v <= 1) ? null : v - 1);
        touch(userId);
        if (left == null) announce(userId, false);
    }

    public boolean isOnline(Long userId) {
        return userId != null && openSockets.containsKey(userId);
    }

    /** Everybody currently online, and when everybody was last seen. */
    @Transactional(readOnly = true)
    public Map<String, Object> snapshot() {
        List<Long> online = new ArrayList<>(openSockets.keySet());
        Map<String, Object> lastSeen = new LinkedHashMap<>();
        userRepository.findAll().forEach(u -> {
            if (u.getLastSeenAt() != null) {
                lastSeen.put(String.valueOf(u.getId()), u.getLastSeenAt().toString());
            }
        });
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("online", online);
        out.put("lastSeen", lastSeen);
        return out;
    }

    /** Tell every listening client that somebody came or went. */
    private void announce(Long userId, boolean online) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", userId);
        payload.put("online", online);
        payload.put("lastSeenAt", LocalDateTime.now().toString());
        try {
            messagingTemplate.convertAndSend("/topic/presence", payload);
        } catch (Exception e) {
            log.debug("Could not broadcast presence for {}", userId, e);
        }
    }

    /**
     * Records the moment. Presence must never be the reason a socket fails, so a
     * write that does not land is logged and forgotten.
     */
    @Transactional
    public void touch(Long userId) {
        try {
            User user = userRepository.findById(userId).orElse(null);
            if (user == null) return;
            user.setLastSeenAt(LocalDateTime.now());
            userRepository.save(user);
        } catch (Exception e) {
            log.debug("Could not record last seen for {}", userId, e);
        }
    }
}
