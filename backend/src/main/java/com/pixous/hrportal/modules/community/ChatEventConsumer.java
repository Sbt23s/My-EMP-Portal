package com.pixous.hrportal.modules.community;

import com.pixous.hrportal.config.KafkaConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Slf4j
// @Component
@RequiredArgsConstructor
public class ChatEventConsumer {

    private final SimpMessagingTemplate messagingTemplate;

    // @KafkaListener(topics = KafkaConfig.CHAT_TOPIC, groupId = "hr-portal-group")
    public void consumeChatMessage(CommunityDTOs.ChatMessagePayload payload) {
        log.info("Received Kafka chat message (disabled) for community {}: {}", payload.getCommunityId(), payload.getContent());
        // Broadcast to WebSocket clients subscribed to this community
        String destination = "/topic/community/" + payload.getCommunityId();
        messagingTemplate.convertAndSend(destination, payload);
    }
}
