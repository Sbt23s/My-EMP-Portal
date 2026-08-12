package com.pixous.hrportal.config;

import com.pixous.hrportal.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.security.Principal;
import java.util.List;

/**
 * STOMP over WebSocket. Clients subscribe to {@code /topic/notifications/{userId}}
 * to receive real-time in-app notifications pushed by {@code NotificationService}.
 */
@Slf4j
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtService jwtService;
    private final AppProperties props;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // The same origins the HTTP side allows, rather than "*".
        //
        // Every REST call is restricted to app.cors.allowed-origins while this
        // endpoint accepted a socket from any page on the internet -- so the one
        // channel that pushes notifications, presence and chat was the one with no
        // origin check on it. Falls back to "*" only if the list is empty, which
        // keeps a misconfigured local setup working rather than silently mute.
        List<String> origins = props.cors() == null ? null : props.cors().allowedOrigins();
        String[] patterns = (origins == null || origins.isEmpty())
                ? new String[]{"*"}
                : origins.toArray(new String[0]);
        if (origins == null || origins.isEmpty()) {
            log.warn("app.cors.allowed-origins is empty -- the /ws endpoint is accepting any origin");
        }
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(patterns)
                .withSockJS();
    }

    /**
     * Names the person behind each socket, from the token the client already
     * sends on CONNECT. Presence needs to know who arrived and who left.
     *
     * <p>This never refuses a connection. Chat and notifications worked before
     * anyone was named and must keep working: a missing or unreadable token
     * simply leaves the session anonymous, which costs it presence and nothing
     * else.
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor =
                        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand())) {
                    return message;
                }
                try {
                    String header = accessor.getFirstNativeHeader("Authorization");
                    if (header != null && header.startsWith("Bearer ")) {
                        String token = header.substring(7);
                        if (jwtService.isValid(token)) {
                            Long userId = jwtService.extractUserId(token);
                            String name = String.valueOf(userId);
                            accessor.setUser((Principal) () -> name);
                        }
                    }
                } catch (Exception e) {
                    log.debug("Could not name a websocket session; leaving it anonymous", e);
                }
                return message;
            }
        });
    }
}
