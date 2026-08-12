package com.pixous.hrportal.modules.chatbot;

import java.util.List;

/** Request/response payloads for the AI assistant endpoints. */
public final class ChatbotDtos {

    private ChatbotDtos() {
    }

    /** A single prior turn of the conversation. role = "user" | "bot". */
    public record ChatTurn(String role, String content) {
    }

    public record ChatRequest(String message, String lang, List<ChatTurn> history) {
    }

    public record ChatResponse(String reply, String provider, String lang) {
    }

    public record TtsRequest(String text, String lang, String voiceId) {
    }

    public record SttResponse(String text, String lang) {
    }

    public record TranslateRequest(String text, String targetLang) {
    }

    public record TranslateResponse(String text, String targetLang) {
    }

    /** Public, secret-free config the widget reads on startup. */
    public record ChatbotConfig(
            boolean enabled,
            boolean ttsAvailable,
            boolean sttAvailable,
            boolean llmAvailable,
            String assistantName,
            List<String> languages
    ) {
    }

    /** Admin view — keys are masked, never returned in full. */
    public record AdminSettingsResponse(
            boolean enabled,
            String llmProvider,
            String groqModel,
            String groqSttModel,
            String geminiModel,
            String elevenLabsVoiceId,
            String elevenLabsModel,
            String websiteUrl,
            boolean groqKeySet,
            boolean geminiKeySet,
            boolean elevenLabsKeySet,
            boolean firecrawlKeySet,
            String groqKeyMasked,
            String geminiKeyMasked,
            String elevenLabsKeyMasked,
            String firecrawlKeyMasked,
            int knowledgeDocs
    ) {
    }

    /**
     * Admin update. Every field is optional (null = leave unchanged).
     * Send a non-blank key to replace it; keys are write-only.
     */
    public record AdminSettingsRequest(
            Boolean enabled,
            String llmProvider,
            String groqModel,
            String groqSttModel,
            String geminiModel,
            String elevenLabsVoiceId,
            String elevenLabsModel,
            String websiteUrl,
            String groqApiKey,
            String geminiApiKey,
            String elevenLabsApiKey,
            String firecrawlApiKey
    ) {
    }

    public record IngestRequest(String url) {
    }

    public record IngestResponse(boolean ok, String message, String title, int chars) {
    }

    public record KnowledgeDoc(Long id, String source, String title, boolean enabled, int chars) {
    }
}
