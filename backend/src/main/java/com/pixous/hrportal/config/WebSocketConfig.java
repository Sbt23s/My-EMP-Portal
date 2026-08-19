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
                if (accessor == null) {
                    return message;
                }

                if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                    return authorizeSubscription(accessor, message);
                }

                if (!StompCommand.CONNECT.equals(accessor.getCommand())) {
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

    /**
     * Topics anyone may listen to, signed in or not.
     *
     * <p>The login announcement is the one genuinely public broadcast: the modal
     * that shows it can be mid token-refresh when it subscribes, and the payload
     * is a notice deliberately shown to everybody. Everything else carries
     * somebody's data.
     */
    private static final List<String> PUBLIC_TOPICS = List.of(
            "/topic/global-announcement");

    /**
     * Decides whether this session may listen to the destination it asked for.
     *
     * <p>Every private topic used to be readable by anyone who could open a
     * socket. The endpoint is public so that the page can connect before its
     * token is ready, connections were never refused, and no check ran at
     * subscribe time -- so an unauthenticated client that guessed
     * {@code /topic/community/3} received that group's chat as it was typed, and
     * {@code /topic/notifications/12} delivered another person's notifications.
     * Both ids are small integers.
     *
     * <p>The check is made here, at SUBSCRIBE, rather than by refusing anonymous
     * CONNECTs. Refusing the connection would also refuse the announcement modal
     * during a token refresh, and would change how every client reconnects;
     * withholding the subscription protects the same data and leaves the
     * connection lifecycle exactly as it was.
     *
     * <p>Returning {@code null} drops the frame: the subscription is simply never
     * created. The client stays connected and keeps whatever it is entitled to.
     */
    private Message<?> authorizeSubscription(StompHeaderAccessor accessor, Message<?> message) {
        String destination = accessor.getDestination();
        if (destination == null) {
            return message;
        }
        if (PUBLIC_TOPICS.contains(destination)) {
            return message;
        }

        Principal user = accessor.getUser();
        if (user == null) {
            log.debug("Refused an unauthenticated subscription to {}", destination);
            return null;
        }

        // A signed-in user may only listen to their own notification stream.
        // Clients only ever ask for their own; anything else is someone reading
        // another person's alerts.
        if (destination.startsWith("/topic/notifications/")) {
            String requested = destination.substring("/topic/notifications/".length());
            if (!requested.equals(user.getName())) {
                log.warn("User {} tried to subscribe to notifications for {}", user.getName(), requested);
                return null;
            }
        }

        return message;
    }
}
