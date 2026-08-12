package com.pixous.hrportal.modules.chatbot;

import com.pixous.hrportal.modules.org.SystemSetting;
import com.pixous.hrportal.modules.org.SystemSettingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Thin accessor over {@code system_settings} for the chatbot's configuration
 * and provider API keys. Keys live server-side only and are read here; the
 * REST layer never returns them in full.
 */
@Service
public class ChatbotSettingsService {

    // Setting keys (mirror V21__chatbot.sql).
    public static final String ENABLED = "CHATBOT_ENABLED";
    public static final String LLM_PROVIDER = "CHATBOT_LLM_PROVIDER";
    public static final String GROQ_API_KEY = "GROQ_API_KEY";
    public static final String GROQ_MODEL = "GROQ_MODEL";
    public static final String GROQ_STT_MODEL = "GROQ_STT_MODEL";
    public static final String GEMINI_API_KEY = "GEMINI_API_KEY";
    public static final String GEMINI_MODEL = "GEMINI_MODEL";
    public static final String ELEVENLABS_API_KEY = "ELEVENLABS_API_KEY";
    public static final String ELEVENLABS_VOICE_ID = "ELEVENLABS_VOICE_ID";
    public static final String ELEVENLABS_MODEL = "ELEVENLABS_MODEL";
    public static final String FIRECRAWL_API_KEY = "FIRECRAWL_API_KEY";
    public static final String WEBSITE_URL = "CHATBOT_WEBSITE_URL";

    private final SystemSettingRepository repository;

    public ChatbotSettingsService(SystemSettingRepository repository) {
        this.repository = repository;
    }

    public String get(String key, String def) {
        return repository.findById(key)
                .map(SystemSetting::getValue)
                .filter(v -> v != null && !v.isBlank())
                .orElse(def);
    }

    public boolean has(String key) {
        return repository.findById(key)
                .map(SystemSetting::getValue)
                .filter(v -> v != null && !v.isBlank())
                .isPresent();
    }

    public boolean enabled() {
        return Boolean.parseBoolean(get(ENABLED, "true"));
    }

    /** Upsert a setting. Blank/null values are ignored so keys are never wiped by accident. */
    @Transactional
    public void put(String key, String value, String description) {
        if (value == null) {
            return;
        }
        SystemSetting s = repository.findById(key).orElseGet(() -> {
            SystemSetting fresh = new SystemSetting();
            fresh.setKey(key);
            fresh.setDescription(description);
            return fresh;
        });
        s.setValue(value);
        repository.save(s);
    }

    /** Mask a secret for display: keep a short prefix, hide the rest. */
    public static String mask(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String v = value.trim();
        if (v.length() <= 8) {
            return "••••";
        }
        return v.substring(0, 4) + "••••••••" + v.substring(v.length() - 4);
    }
}
