package com.pixous.hrportal.modules.community;

import com.pixous.hrportal.config.KafkaConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
// @Component
@RequiredArgsConstructor
public class ChatEventProducer {

    // private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishMessage(CommunityDTOs.ChatMessagePayload payload) {
        log.info("Publishing message to Kafka (disabled) for community: {}", payload.getCommunityId());
        // kafkaTemplate.send(KafkaConfig.CHAT_TOPIC, String.valueOf(payload.getCommunityId()), payload);
    }
}
