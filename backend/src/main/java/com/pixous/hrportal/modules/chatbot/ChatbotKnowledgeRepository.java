package com.pixous.hrportal.modules.chatbot;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatbotKnowledgeRepository extends JpaRepository<ChatbotKnowledge, Long> {

    List<ChatbotKnowledge> findByEnabledTrueOrderByIdAsc();

    List<ChatbotKnowledge> findBySource(String source);

    void deleteBySource(String source);
}
