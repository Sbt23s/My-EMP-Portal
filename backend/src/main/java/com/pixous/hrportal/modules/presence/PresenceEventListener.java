package com.pixous.hrportal.modules.presence;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/**
 * Turns socket lifecycle events into presence. The principal is put on the
 * session by the CONNECT interceptor; a socket without one is simply ignored,
 * which is what an unauthenticated connection deserves.
 */
@Component
@RequiredArgsConstructor
public class PresenceEventListener {

    private final PresenceService presenceService;

    @EventListener
    public void onConnected(SessionConnectedEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        presenceService.connected(accessor.getSessionId(), userId(accessor));
    }

    @EventListener
    public void onDisconnected(SessionDisconnectEvent event) {
        presenceService.disconnected(event.getSessionId());
    }

    private static Long userId(StompHeaderAccessor accessor) {
        if (accessor.getUser() == null) return null;
        try {
            return Long.valueOf(accessor.getUser().getName());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
