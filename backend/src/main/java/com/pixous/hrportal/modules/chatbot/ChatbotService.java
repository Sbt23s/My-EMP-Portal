package com.pixous.hrportal.modules.chatbot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pixous.hrportal.common.ApiException;
import com.pixous.hrportal.common.ErrorCode;
import com.pixous.hrportal.modules.chatbot.ChatbotDtos.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Orchestrates the AI assistant: chat (Groq → Gemini → rule-based fallback),
 * text-to-speech (ElevenLabs), speech-to-text (Groq Whisper) and website
 * knowledge ingestion (Firecrawl). Every provider call happens server-side so
 * the API keys are never exposed to the browser.
 */
@Service
public class ChatbotService {

    private static final Logger log = LoggerFactory.getLogger(ChatbotService.class);

    private static final String GROQ_CHAT_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final String GROQ_STT_URL = "https://api.groq.com/openai/v1/audio/transcriptions";
    private static final String GEMINI_URL = "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s";
    private static final String ELEVENLABS_URL = "https://api.elevenlabs.io/v1/text-to-speech/%s";
    private static final String FIRECRAWL_URL = "https://api.firecrawl.dev/v1/scrape";

    private static final int MAX_HISTORY_TURNS = 8;
    private static final int MAX_KNOWLEDGE_CHARS = 6000;
    private static final int MAX_INGEST_CHARS = 12000;

    /** Baked-in guide to the portal so the assistant is grounded even before any crawl. */
    private static final String EMS_GUIDE = """
            === PIXOUS HR PORTAL — EMPLOYEE MANAGEMENT SYSTEM ===
            Pixous HR Portal is a full HR & Employee Management System for a company that spans
            IT (desk staff) and Civil/Facilities (on-site field workers). It handles attendance,
            leave, payroll, assets, helpdesk, reports and more, with role-based access.

            SIDEBAR MODULES (guide the user to the matching menu item):
            - Dashboard: personal overview — today's attendance status, pending leaves, assets, open tickets. Punch In / Punch Out lives here.
            - Attendance: view your punch logs; field workers punch with GPS geofencing.
            - Team Attendance: managers/HR view their team's attendance.
            - Leave: check leave balances and apply for leave. Approvals appear under "Approvals" for managers/HR.
            - Leave Policies: HR/Admin configure leave types and rules.
            - Payroll Runs / Payslip Requests: HR/Finance run payroll and approve payslip requests.
            - Payslips: employees view and download monthly payslips (PDF).
            - Work Reports: log and review daily project work hours.
            - Employees: HR/Admin manage the employee directory, create new employees.
            - Claims: travel-allowance claims (per-km for hills/plains, bus fare, etc.).
            - Assets: company devices/machinery assigned to you (with QR tags).
            - Helpdesk: raise and track support tickets (with SLA + ratings).
            - Complaints / Needs: raise complaints or needs; HR/Admin review and respond.
            - Onboarding: HR-managed onboarding checklists for new joiners.
            - Safety: site safety incident reporting (Civil).
            - Reports: analytics and exports for leadership.
            - Communities / Chat: internal group chat and audio/video calls.
            - Admin > AI Assistant: (admins only) configure the assistant's API keys and website knowledge.

            HOW-TO QUICK ANSWERS:
            - Punch attendance: open the Dashboard and use "Punch In" / "Punch Out". Field staff must be inside the site geofence.
            - Apply for leave: Leave menu → choose leave type and dates → submit. HR approves it.
            - View salary/payslip: Payslips menu → open the month → download PDF.
            - Raise an IT/facility issue: Helpdesk → New Ticket.
            - See assigned laptop/equipment: Assets menu.
            - Reset password / profile details: Profile (top-right avatar menu).

            ROLES: Super Admin (full config), IT/Civil Employee (self-service), IT/Civil HR (employees, payroll,
            leave approvals), HR/Supervisor (team + approvals), Finance (payroll approvals), CEO (executive dashboards),
            Asset Managers, Facilities Admin. Login is by username; every account has its own permissions.

            STYLE: You are the Pixous HR Assistant. Be warm, concise and professional. Answer HR/portal questions and
            general questions. When a task maps to a screen, name the exact sidebar menu. You guide users — you cannot
            perform actions or read a specific person's private data yourself. If unsure, say so briefly.
            """;

    private final ChatbotSettingsService settings;
    private final ChatbotKnowledgeRepository knowledgeRepo;
    private final ObjectMapper mapper;
    private final ChatbotOrgContext orgContext;
    /** Read so the assistant does not describe features this company switched off. */
    private final com.pixous.hrportal.modules.admin.CompanyModuleRepository companyModuleRepository;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    public ChatbotService(ChatbotSettingsService settings,
                          ChatbotKnowledgeRepository knowledgeRepo,
                          ObjectMapper mapper,
                          ChatbotOrgContext orgContext,
                          com.pixous.hrportal.modules.admin.CompanyModuleRepository companyModuleRepository) {
        this.settings = settings;
        this.knowledgeRepo = knowledgeRepo;
        this.mapper = mapper;
        this.orgContext = orgContext;
        this.companyModuleRepository = companyModuleRepository;
    }

    // ---------------------------------------------------------------- config

    public ChatbotConfig config() {
        boolean groq = settings.has(ChatbotSettingsService.GROQ_API_KEY);
        boolean gemini = settings.has(ChatbotSettingsService.GEMINI_API_KEY);
        boolean eleven = settings.has(ChatbotSettingsService.ELEVENLABS_API_KEY);
        return new ChatbotConfig(
                settings.enabled(),
                eleven,                       // TTS available (browser speech is the fallback anyway)
                groq,                         // STT via Groq Whisper
                groq || gemini,               // LLM available
                "Pixous HR Assistant",
                List.of("en", "ta", "hi")
        );
    }

    // ---------------------------------------------------------------- chat

    public ChatResponse chat(ChatRequest req) {
        return chat(req, false);
    }

    public ChatResponse chat(ChatRequest req, boolean privileged) {
        String message = req.message() == null ? "" : req.message().trim();
        String lang = normaliseLang(req.lang());
        if (message.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Message is required");
        }
        if (!settings.enabled()) {
            return new ChatResponse(localFallback(message, lang), "disabled", lang);
        }

        String system = systemPrompt(lang);
        // Admins/HR get a live org-data snapshot so the bot answers real questions
        // (present/absent counts, team members, pending approvals, open tickets).
        if (privileged) {
            String live = orgContext.snapshot();
            if (live != null && !live.isBlank()) {
                system = system + live;
            }
        }
        List<ChatTurn> history = req.history() == null ? List.of() : req.history();
        String provider = settings.get(ChatbotSettingsService.LLM_PROVIDER, "groq");

        // Provider order: preferred first, then the other, then rule-based.
        List<String> order = "gemini".equalsIgnoreCase(provider)
                ? List.of("gemini", "groq")
                : List.of("groq", "gemini");

        for (String p : order) {
            try {
                if ("groq".equals(p) && settings.has(ChatbotSettingsService.GROQ_API_KEY)) {
                    return new ChatResponse(callGroqChat(system, history, message), "groq", lang);
                }
                if ("gemini".equals(p) && settings.has(ChatbotSettingsService.GEMINI_API_KEY)) {
                    return new ChatResponse(callGemini(system, history, message), "gemini", lang);
                }
            } catch (Exception e) {
                log.warn("Chatbot provider {} failed: {}", p, e.getMessage());
            }
        }
        return new ChatResponse(localFallback(message, lang), "local", lang);
    }

    private String callGroqChat(String system, List<ChatTurn> history, String message) throws Exception {
        String key = settings.get(ChatbotSettingsService.GROQ_API_KEY, "");
        String model = settings.get(ChatbotSettingsService.GROQ_MODEL, "llama-3.3-70b-versatile");

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", system));
        for (ChatTurn t : trimHistory(history)) {
            String role = "bot".equalsIgnoreCase(t.role()) || "assistant".equalsIgnoreCase(t.role())
                    ? "assistant" : "user";
            messages.add(Map.of("role", role, "content", t.content()));
        }
        messages.add(Map.of("role", "user", "content", message));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("temperature", 0.3);
        body.put("max_tokens", 800);
        body.put("messages", messages);

        HttpRequest request = HttpRequest.newBuilder(URI.create(GROQ_CHAT_URL))
                .timeout(Duration.ofSeconds(60))
                .header("Authorization", "Bearer " + key)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();

        HttpResponse<String> res = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() / 100 != 2) {
            throw new IllegalStateException("Groq " + res.statusCode() + ": " + res.body());
        }
        JsonNode root = mapper.readTree(res.body());
        String content = root.path("choices").path(0).path("message").path("content").asText("");
        if (content.isBlank()) {
            throw new IllegalStateException("Empty Groq response");
        }
        return content.trim();
    }

    private String callGemini(String system, List<ChatTurn> history, String message) throws Exception {
        String key = settings.get(ChatbotSettingsService.GEMINI_API_KEY, "");
        String model = settings.get(ChatbotSettingsService.GEMINI_MODEL, "gemini-1.5-flash");

        List<Map<String, Object>> contents = new ArrayList<>();
        for (ChatTurn t : trimHistory(history)) {
            String role = "bot".equalsIgnoreCase(t.role()) || "model".equalsIgnoreCase(t.role())
                    ? "model" : "user";
            contents.add(Map.of("role", role, "parts", List.of(Map.of("text", t.content()))));
        }
        contents.add(Map.of("role", "user", "parts", List.of(Map.of("text", message))));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("systemInstruction", Map.of("parts", List.of(Map.of("text", system))));
        body.put("contents", contents);
        body.put("generationConfig", Map.of("temperature", 0.3, "maxOutputTokens", 800));

        String url = String.format(GEMINI_URL, model, key);
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();

        HttpResponse<String> res = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() / 100 != 2) {
            throw new IllegalStateException("Gemini " + res.statusCode() + ": " + res.body());
        }
        JsonNode root = mapper.readTree(res.body());
        String content = root.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText("");
        if (content.isBlank()) {
            throw new IllegalStateException("Empty Gemini response");
        }
        return content.trim();
    }

    // ---------------------------------------------------------------- translate

    public TranslateResponse translate(TranslateRequest req) {
        String text = req.text() == null ? "" : req.text().trim();
        String target = normaliseLang(req.targetLang());
        if (text.isEmpty()) {
            return new TranslateResponse("", target);
        }
        String system = "You are a precise translation engine. Translate the user's text into "
                + langName(target) + ". Reply with ONLY the translation, no notes or quotes.";
        try {
            if (settings.has(ChatbotSettingsService.GROQ_API_KEY)) {
                return new TranslateResponse(callGroqChat(system, List.of(), text), target);
            }
            if (settings.has(ChatbotSettingsService.GEMINI_API_KEY)) {
                return new TranslateResponse(callGemini(system, List.of(), text), target);
            }
        } catch (Exception e) {
            log.warn("Translate failed: {}", e.getMessage());
        }
        return new TranslateResponse(text, target); // graceful: echo original
    }

    // ---------------------------------------------------------------- text to speech

    /** @return MP3 audio bytes, or {@code null} when TTS is unavailable (caller falls back to browser speech). */
    public byte[] textToSpeech(TtsRequest req) {
        String text = req.text() == null ? "" : req.text().trim();
        if (text.isEmpty()) return null;

        String reqLang = req.lang() == null ? "" : req.lang().trim().toLowerCase();
        if (reqLang.length() > 2) reqLang = reqLang.substring(0, 2);

        // Tamil / Hindi: use Google's native voice (server-side, reliable — the
        // browser can't fetch it directly). No ElevenLabs key required.
        if (reqLang.equals("ta") || reqLang.equals("hi")) {
            byte[] g = googleTts(text, reqLang);
            if (g != null && g.length > 0) return g;
        }

        if (!settings.has(ChatbotSettingsService.ELEVENLABS_API_KEY)) {
            return null;
        }
        String key = settings.get(ChatbotSettingsService.ELEVENLABS_API_KEY, "");
        String voiceId = (req.voiceId() != null && !req.voiceId().isBlank())
                ? req.voiceId().trim()
                : settings.get(ChatbotSettingsService.ELEVENLABS_VOICE_ID, "EXAVITQu4vr4xnSDxMaL");
        String model = settings.get(ChatbotSettingsService.ELEVENLABS_MODEL, "eleven_multilingual_v2");

        // Language enforcement fixes accent/pronunciation for non-English (e.g.
        // Tamil, Hindi). Only Turbo/Flash v2.5 support language_code, so when a
        // non-English language is requested, switch to Turbo v2.5 and enforce it.
        String lc = req.lang() == null ? "" : req.lang().trim().toLowerCase();
        if (lc.length() > 2) lc = lc.substring(0, 2);
        boolean nonEnglish = !lc.isEmpty() && !lc.equals("en");
        if (nonEnglish && (model == null || (!model.contains("turbo") && !model.contains("flash")))) {
            model = "eleven_turbo_v2_5";
        }
        boolean supportsLangCode = model.contains("turbo") || model.contains("flash");

        // ElevenLabs caps request length; keep spoken replies snappy.
        String speak = text.length() > 2500 ? text.substring(0, 2500) : text;

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("text", speak);
        body.put("model_id", model);
        if (supportsLangCode && !lc.isEmpty()) {
            body.put("language_code", lc);
        }
        body.put("voice_settings", Map.of("stability", 0.5, "similarity_boost", 0.75, "style", 0.0));

        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(String.format(ELEVENLABS_URL, voiceId)))
                    .timeout(Duration.ofSeconds(60))
                    .header("xi-api-key", key)
                    .header("Content-Type", "application/json")
                    .header("Accept", "audio/mpeg")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();
            HttpResponse<byte[]> res = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (res.statusCode() / 100 == 2 && res.body().length > 0) {
                return res.body();
            }
            log.warn("ElevenLabs TTS {}: {}", res.statusCode(),
                    new String(res.body(), StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.warn("ElevenLabs TTS failed: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Native TTS via Google Translate (server-side; the browser can't call it
     * directly). Splits into &lt;=180-char chunks and concatenates the MP3 bytes.
     */
    private byte[] googleTts(String text, String lang) {
        try {
            java.util.List<String> chunks = new java.util.ArrayList<>();
            String remaining = text;
            while (!remaining.isEmpty()) {
                int cut = Math.min(180, remaining.length());
                if (cut < remaining.length()) {
                    int sp = remaining.lastIndexOf(' ', cut);
                    if (sp > 40) cut = sp;
                }
                chunks.add(remaining.substring(0, cut).trim());
                remaining = remaining.substring(cut).trim();
                if (chunks.size() > 20) break; // safety cap
            }
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            for (String c : chunks) {
                if (c.isEmpty()) continue;
                String url = "https://translate.google.com/translate_tts?ie=UTF-8&tl=" + lang
                        + "&client=tw-ob&q=" + java.net.URLEncoder.encode(c, StandardCharsets.UTF_8);
                HttpRequest r = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(20))
                        .header("User-Agent", "Mozilla/5.0")
                        .GET().build();
                HttpResponse<byte[]> res = http.send(r, HttpResponse.BodyHandlers.ofByteArray());
                if (res.statusCode() / 100 == 2 && res.body().length > 0) {
                    out.write(res.body());
                } else {
                    log.warn("Google TTS {} for chunk", res.statusCode());
                }
            }
            byte[] bytes = out.toByteArray();
            return bytes.length > 0 ? bytes : null;
        } catch (Exception e) {
            log.warn("Google TTS failed: {}", e.getMessage());
            return null;
        }
    }

    // ---------------------------------------------------------------- speech to text

    public SttResponse speechToText(byte[] audio, String filename, String contentType, String lang) {
        if (audio == null || audio.length == 0) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Audio is required");
        }
        if (!settings.has(ChatbotSettingsService.GROQ_API_KEY)) {
            throw new ApiException(ErrorCode.BUSINESS_RULE, "Speech-to-text is not configured");
        }
        String key = settings.get(ChatbotSettingsService.GROQ_API_KEY, "");
        String model = settings.get(ChatbotSettingsService.GROQ_STT_MODEL, "whisper-large-v3");
        String boundary = "----hrportalstt" + System.nanoTime();
        String safeName = (filename == null || filename.isBlank()) ? "audio.webm" : filename;
        String safeType = (contentType == null || contentType.isBlank()) ? "audio/webm" : contentType;

        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            writeFormField(out, boundary, "model", model);
            if (lang != null && !lang.isBlank() && !"auto".equalsIgnoreCase(lang)) {
                writeFormField(out, boundary, "language", normaliseLang(lang));
            }
            writeFormField(out, boundary, "response_format", "verbose_json");
            writeFormField(out, boundary, "temperature", "0");
            writeFileHeader(out, boundary, "file", safeName, safeType);
            out.write(audio);
            out.write("\r\n".getBytes(StandardCharsets.UTF_8));
            out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

            HttpRequest request = HttpRequest.newBuilder(URI.create(GROQ_STT_URL))
                    .timeout(Duration.ofSeconds(90))
                    .header("Authorization", "Bearer " + key)
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray()))
                    .build();

            HttpResponse<String> res = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() / 100 != 2) {
                throw new IllegalStateException("Groq STT " + res.statusCode() + ": " + res.body());
            }
            JsonNode root = mapper.readTree(res.body());
            String text = root.path("text").asText("").trim();
            String detected = mapWhisperLanguage(root.path("language").asText(""));
            return new SttResponse(text, detected);
        } catch (Exception e) {
            log.warn("Speech-to-text failed: {}", e.getMessage());
            throw new ApiException(ErrorCode.BUSINESS_RULE, "Could not transcribe audio");
        }
    }

    // ---------------------------------------------------------------- knowledge / Firecrawl

    public IngestResponse ingestWebsite(String url) {
        String target = (url == null || url.isBlank())
                ? settings.get(ChatbotSettingsService.WEBSITE_URL, "https://pixoustech.com")
                : url.trim();
        if (!settings.has(ChatbotSettingsService.FIRECRAWL_API_KEY)) {
            return new IngestResponse(false, "Firecrawl API key is not configured", null, 0);
        }
        String key = settings.get(ChatbotSettingsService.FIRECRAWL_API_KEY, "");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("url", target);
        body.put("formats", List.of("markdown"));
        body.put("onlyMainContent", true);

        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(FIRECRAWL_URL))
                    .timeout(Duration.ofSeconds(120))
                    .header("Authorization", "Bearer " + key)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> res = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() / 100 != 2) {
                return new IngestResponse(false, "Firecrawl " + res.statusCode(), null, 0);
            }
            JsonNode root = mapper.readTree(res.body());
            JsonNode data = root.path("data");
            String markdown = data.path("markdown").asText("");
            String title = data.path("metadata").path("title").asText(target);
            if (markdown.isBlank()) {
                return new IngestResponse(false, "No content returned for that URL", title, 0);
            }
            if (markdown.length() > MAX_INGEST_CHARS) {
                markdown = markdown.substring(0, MAX_INGEST_CHARS);
            }

            ChatbotKnowledge doc = new ChatbotKnowledge();
            doc.setSource("firecrawl");
            doc.setTitle(title);
            doc.setContent("Source URL: " + target + "\n\n" + markdown);
            doc.setEnabled(true);
            knowledgeRepo.save(doc);

            return new IngestResponse(true, "Ingested website content into the knowledge base", title, markdown.length());
        } catch (Exception e) {
            log.warn("Firecrawl ingest failed: {}", e.getMessage());
            return new IngestResponse(false, "Ingestion failed: " + e.getMessage(), null, 0);
        }
    }

    public List<KnowledgeDoc> knowledge() {
        return knowledgeRepo.findAll().stream()
                .map(k -> new KnowledgeDoc(k.getId(), k.getSource(), k.getTitle(), k.isEnabled(),
                        k.getContent() == null ? 0 : k.getContent().length()))
                .toList();
    }

    public void deleteKnowledge(Long id) {
        knowledgeRepo.deleteById(id);
    }

    // ---------------------------------------------------------------- admin settings

    public AdminSettingsResponse adminSettings() {
        return new AdminSettingsResponse(
                settings.enabled(),
                settings.get(ChatbotSettingsService.LLM_PROVIDER, "groq"),
                settings.get(ChatbotSettingsService.GROQ_MODEL, "llama-3.3-70b-versatile"),
                settings.get(ChatbotSettingsService.GROQ_STT_MODEL, "whisper-large-v3"),
                settings.get(ChatbotSettingsService.GEMINI_MODEL, "gemini-1.5-flash"),
                settings.get(ChatbotSettingsService.ELEVENLABS_VOICE_ID, "EXAVITQu4vr4xnSDxMaL"),
                settings.get(ChatbotSettingsService.ELEVENLABS_MODEL, "eleven_multilingual_v2"),
                settings.get(ChatbotSettingsService.WEBSITE_URL, "https://pixoustech.com"),
                settings.has(ChatbotSettingsService.GROQ_API_KEY),
                settings.has(ChatbotSettingsService.GEMINI_API_KEY),
                settings.has(ChatbotSettingsService.ELEVENLABS_API_KEY),
                settings.has(ChatbotSettingsService.FIRECRAWL_API_KEY),
                ChatbotSettingsService.mask(settings.get(ChatbotSettingsService.GROQ_API_KEY, "")),
                ChatbotSettingsService.mask(settings.get(ChatbotSettingsService.GEMINI_API_KEY, "")),
                ChatbotSettingsService.mask(settings.get(ChatbotSettingsService.ELEVENLABS_API_KEY, "")),
                ChatbotSettingsService.mask(settings.get(ChatbotSettingsService.FIRECRAWL_API_KEY, "")),
                knowledgeRepo.findAll().size()
        );
    }

    public void updateSettings(AdminSettingsRequest req) {
        if (req.enabled() != null) {
            settings.put(ChatbotSettingsService.ENABLED, String.valueOf(req.enabled()), "Master on/off switch");
        }
        putIfPresent(ChatbotSettingsService.LLM_PROVIDER, req.llmProvider(), "Primary LLM provider");
        putIfPresent(ChatbotSettingsService.GROQ_MODEL, req.groqModel(), "Groq chat model");
        putIfPresent(ChatbotSettingsService.GROQ_STT_MODEL, req.groqSttModel(), "Groq Whisper model");
        putIfPresent(ChatbotSettingsService.GEMINI_MODEL, req.geminiModel(), "Gemini model");
        putIfPresent(ChatbotSettingsService.ELEVENLABS_VOICE_ID, req.elevenLabsVoiceId(), "ElevenLabs voice id");
        putIfPresent(ChatbotSettingsService.ELEVENLABS_MODEL, req.elevenLabsModel(), "ElevenLabs model");
        putIfPresent(ChatbotSettingsService.WEBSITE_URL, req.websiteUrl(), "Company website");
        // Keys are write-only: only overwrite when a non-blank value is supplied.
        putIfPresent(ChatbotSettingsService.GROQ_API_KEY, req.groqApiKey(), "Groq API key");
        putIfPresent(ChatbotSettingsService.GEMINI_API_KEY, req.geminiApiKey(), "Gemini API key");
        putIfPresent(ChatbotSettingsService.ELEVENLABS_API_KEY, req.elevenLabsApiKey(), "ElevenLabs API key");
        putIfPresent(ChatbotSettingsService.FIRECRAWL_API_KEY, req.firecrawlApiKey(), "Firecrawl API key");
    }

    private void putIfPresent(String key, String value, String description) {
        if (value != null && !value.isBlank()) {
            settings.put(key, value.trim(), description);
        }
    }

    // ---------------------------------------------------------------- prompt / helpers

    private String systemPrompt(String lang) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are the Pixous HR Assistant, an advanced AI helper embedded in the Pixous HR Portal.\n");
        sb.append("Reply strictly in ").append(langName(lang)).append(", regardless of the language of the question, ")
                .append("unless the user explicitly asks for another language.\n");
        sb.append("Keep answers clear and concise (usually 1–4 sentences). Use the knowledge below to answer ")
                .append("questions about the portal. For general questions, answer helpfully.\n\n");
        sb.append(EMS_GUIDE).append("\n");

        String extra = knowledgeContext();
        if (!extra.isBlank()) {
            sb.append("\n=== ADDITIONAL KNOWLEDGE (from the company website / admin) ===\n");
            sb.append(extra).append("\n");
        }
        return sb.toString();
    }

    private String knowledgeContext() {
        StringBuilder sb = new StringBuilder();
        for (ChatbotKnowledge k : knowledgeRepo.findByEnabledTrueOrderByIdAsc()) {
            if (k.getContent() == null || k.getContent().isBlank()) {
                continue;
            }
            if (k.getTitle() != null && !k.getTitle().isBlank()) {
                sb.append("# ").append(k.getTitle()).append("\n");
            }
            sb.append(k.getContent().trim()).append("\n\n");
            if (sb.length() >= MAX_KNOWLEDGE_CHARS) {
                break;
            }
        }
        String ctx = sb.toString();
        return ctx.length() > MAX_KNOWLEDGE_CHARS ? ctx.substring(0, MAX_KNOWLEDGE_CHARS) : ctx;
    }

    private List<ChatTurn> trimHistory(List<ChatTurn> history) {
        List<ChatTurn> clean = new ArrayList<>();
        for (ChatTurn t : history) {
            if (t != null && t.content() != null && !t.content().isBlank()) {
                clean.add(t);
            }
        }
        if (clean.size() > MAX_HISTORY_TURNS) {
            return clean.subList(clean.size() - MAX_HISTORY_TURNS, clean.size());
        }
        return clean;
    }

    /**
     * Module codes switched on for the caller's company.
     *
     * <p>Empty means "could not tell", and every caller below treats that as
     * "answer about everything" — the assistant going quiet about leave because
     * a lookup failed is worse than it mentioning a page somebody cannot open.
     */
    private java.util.Set<String> enabledModules() {
        try {
            Long companyId = com.pixous.hrportal.security.SecurityUtils.currentCompanyId();
            if (companyId == null) return java.util.Set.of();
            java.util.Set<String> codes = new java.util.HashSet<>();
            companyModuleRepository.findByCompanyId(companyId).forEach(m -> {
                if (m.isEnabled() && m.getModuleCode() != null) {
                    codes.add(m.getModuleCode().trim().toUpperCase());
                }
            });
            return codes;
        } catch (Exception e) {
            log.debug("Could not read module settings for the assistant", e);
            return java.util.Set.of();
        }
    }

    /** True when this module may be talked about. Unknown counts as yes. */
    private boolean canDiscuss(java.util.Set<String> on, String moduleCode) {
        return on.isEmpty() || on.contains(moduleCode);
    }

    private String localFallback(String message, String lang) {
        String q = message.toLowerCase();

        /*
         * Each topic is gated by its module, in one place, so every language
         * below is fixed at once.
         *
         * This answered about payslips while payroll was switched off — sending
         * someone to a 'Payslips' menu the navigation no longer has. Clearing
         * the flag here makes the topic fall through to the general reply
         * instead of naming a page that is not there.
         */
        java.util.Set<String> on = enabledModules();

        boolean leaveAsked = q.contains("leave") || q.contains("விடுப்பு") || q.contains("छुट्टी") || q.contains("अवकाश");
        boolean attendanceAsked = q.contains("attendance") || q.contains("punch") || q.contains("வருகை") || q.contains("हाज़िरी") || q.contains("उपस्थिति");
        boolean payAsked = q.contains("pay") || q.contains("salary") || q.contains("payslip") || q.contains("சம்பளம்") || q.contains("वेतन") || q.contains("सैलरी");
        boolean assetAsked = q.contains("asset") || q.contains("laptop") || q.contains("சொத்து") || q.contains("संपत्ति");

        boolean leave = leaveAsked && canDiscuss(on, "LEAVE");
        boolean attendance = attendanceAsked && canDiscuss(on, "ATTENDANCE");
        boolean pay = payAsked && canDiscuss(on, "PAYROLL");
        boolean asset = assetAsked && canDiscuss(on, "ASSETS");

        // Asked about something real that this company does not have. Saying so
        // is kinder than a general reply, which reads as not having understood
        // and invites the same question again.
        boolean askedSomethingOff =
                (leaveAsked && !leave) || (attendanceAsked && !attendance)
                        || (payAsked && !pay) || (assetAsked && !asset);
        if (askedSomethingOff) {
            return switch (lang) {
                case "ta" -> "அந்த வசதி உங்கள் நிறுவனத்தில் இயக்கப்படவில்லை. நிர்வாகியை அணுகுங்கள்.";
                case "hi" -> "यह सुविधा आपकी कंपनी में सक्रिय नहीं है। कृपया अपने प्रशासक से संपर्क करें।";
                default -> "That is not switched on for your company. Ask your administrator if you need it.";
            };
        }

        switch (lang) {
            case "ta" -> {
                if (leave) return "'Leave' மெனுவில் உங்கள் விடுப்பு விவரங்களைப் பார்த்து விண்ணப்பிக்கலாம்.";
                if (attendance) return "Dashboard-இல் 'Punch In/Out' மூலம் வருகையைப் பதிவு செய்யலாம்; விவரங்கள் 'Attendance' பக்கத்தில்.";
                if (pay) return "உங்கள் சம்பள விவரங்களையும் பேஸ்லிப்களையும் 'Payslips' பக்கத்தில் காணலாம்.";
                if (asset) return "உங்களுக்கு வழங்கப்பட்ட சாதனங்கள் 'Assets' மெனுவில் பட்டியலிடப்பட்டுள்ளன.";
                return "நான் Pixous HR உதவியாளர். விடுப்பு, வருகை, சம்பளம், சொத்துக்கள் குறித்து கேளுங்கள். (AI சேவை தற்காலிகமாக கிடைக்கவில்லை.)";
            }
            case "hi" -> {
                if (leave) return "'Leave' मेन्यू में आप अपनी छुट्टी का बैलेंस देख सकते हैं और आवेदन कर सकते हैं।";
                if (attendance) return "Dashboard पर 'Punch In/Out' से हाज़िरी दर्ज करें; विवरण 'Attendance' पेज पर देखें।";
                if (pay) return "अपने वेतन और मासिक पे-स्लिप 'Payslips' पेज पर देखें और डाउनलोड करें।";
                if (asset) return "आपको सौंपे गए कंपनी उपकरण 'Assets' मेन्यू में सूचीबद्ध हैं।";
                return "मैं Pixous HR सहायक हूँ। छुट्टी, हाज़िरी, वेतन और संपत्ति के बारे में पूछें। (AI सेवा अभी उपलब्ध नहीं है।)";
            }
            default -> {
                if (leave) return "You can check your leave balance and apply under the 'Leave' menu.";
                if (attendance) return "Punch in/out on the Dashboard, and view your logs under 'Attendance'.";
                if (pay) return "View and download your monthly payslips under the 'Payslips' menu.";
                if (asset) return "Company devices assigned to you are listed under the 'Assets' menu.";
                return "I'm the Pixous HR Assistant. Ask me about leave, attendance, payslips or assets. (The AI service is temporarily unavailable.)";
            }
        }
    }

    private static String normaliseLang(String lang) {
        if (lang == null) return "en";
        String l = lang.trim().toLowerCase();
        if (l.startsWith("ta")) return "ta";
        if (l.startsWith("hi")) return "hi";
        return "en";
    }

    private static String langName(String lang) {
        return switch (normaliseLang(lang)) {
            case "ta" -> "Tamil (தமிழ்)";
            case "hi" -> "Hindi (हिन्दी)";
            default -> "English";
        };
    }

    private static String mapWhisperLanguage(String raw) {
        if (raw == null) return "en";
        String r = raw.trim().toLowerCase();
        if (r.startsWith("ta") || r.contains("tamil")) return "ta";
        if (r.startsWith("hi") || r.contains("hindi")) return "hi";
        return "en";
    }

    private static void writeFormField(ByteArrayOutputStream out, String boundary, String name, String value)
            throws java.io.IOException {
        String s = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n"
                + value + "\r\n";
        out.write(s.getBytes(StandardCharsets.UTF_8));
    }

    private static void writeFileHeader(ByteArrayOutputStream out, String boundary, String name,
                                        String filename, String contentType) throws java.io.IOException {
        String s = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"" + name + "\"; filename=\"" + filename + "\"\r\n"
                + "Content-Type: " + contentType + "\r\n\r\n";
        out.write(s.getBytes(StandardCharsets.UTF_8));
    }
}
