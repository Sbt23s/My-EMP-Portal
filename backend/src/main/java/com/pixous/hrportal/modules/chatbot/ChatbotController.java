package com.pixous.hrportal.modules.chatbot;

import com.pixous.hrportal.common.ApiResponse;
import com.pixous.hrportal.modules.chatbot.ChatbotDtos.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * AI assistant API. Chat / TTS / STT / translate are available to any
 * authenticated user; everything under {@code /admin} requires USER_MANAGE
 * (HR / Super Admin) — this is the only place API keys can be configured.
 */
@RestController
@RequestMapping("/api/chatbot")
@Tag(name = "Chatbot", description = "AI HR assistant — chat, voice and knowledge")
public class ChatbotController {

    private final ChatbotService service;

    public ChatbotController(ChatbotService service) {
        this.service = service;
    }

    @GetMapping("/config")
    @Operation(summary = "Public (secret-free) assistant config for the widget")
    public ApiResponse<ChatbotConfig> config() {
        return ApiResponse.ok(service.config());
    }

    @PostMapping("/chat")
    @Operation(summary = "Send a message and get the assistant's reply")
    public ApiResponse<ChatResponse> chat(@RequestBody ChatRequest req) {
        // HR / Admins get a live org-data snapshot injected so the bot can answer
        // real questions (attendance counts, team members, approvals, tickets).
        boolean privileged = com.pixous.hrportal.security.SecurityUtils.hasAuthority("USER_MANAGE")
                || com.pixous.hrportal.security.SecurityUtils.hasAuthority("DASHBOARD_EXEC");
        return ApiResponse.ok(service.chat(req, privileged));
    }

    @PostMapping("/translate")
    @Operation(summary = "Translate text into a target language")
    public ApiResponse<TranslateResponse> translate(@RequestBody TranslateRequest req) {
        return ApiResponse.ok(service.translate(req));
    }

    @PostMapping("/tts")
    @Operation(summary = "Text-to-speech (ElevenLabs); 204 when unavailable so the client uses browser speech")
    public ResponseEntity<byte[]> tts(@RequestBody TtsRequest req) {
        byte[] audio = service.textToSpeech(req);
        if (audio == null || audio.length == 0) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("audio/mpeg"))
                .cacheControl(CacheControl.noCache())
                .body(audio);
    }

    @PostMapping(value = "/stt", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Speech-to-text (Groq Whisper); auto-detects Tamil / Hindi / English")
    public ApiResponse<SttResponse> stt(@RequestPart("audio") MultipartFile audio,
                                        @RequestParam(value = "lang", required = false) String lang)
            throws IOException {
        return ApiResponse.ok(service.speechToText(
                audio.getBytes(),
                audio.getOriginalFilename(),
                audio.getContentType(),
                lang));
    }

    // ------------------------------------------------------------ admin only

    @GetMapping("/admin/settings")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    @Operation(summary = "Read chatbot settings (keys masked)")
    public ApiResponse<AdminSettingsResponse> adminSettings() {
        return ApiResponse.ok(service.adminSettings());
    }

    @PutMapping("/admin/settings")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    @Operation(summary = "Update chatbot settings and API keys")
    public ApiResponse<AdminSettingsResponse> updateSettings(@RequestBody AdminSettingsRequest req) {
        service.updateSettings(req);
        return ApiResponse.ok(service.adminSettings(), "Settings saved");
    }

    @PostMapping("/admin/ingest")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    @Operation(summary = "Crawl the company website (Firecrawl) into the knowledge base")
    public ApiResponse<IngestResponse> ingest(@RequestBody(required = false) IngestRequest req) {
        String url = req == null ? null : req.url();
        return ApiResponse.ok(service.ingestWebsite(url));
    }

    @GetMapping("/admin/knowledge")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    @Operation(summary = "List knowledge-base documents")
    public ApiResponse<List<KnowledgeDoc>> knowledge() {
        return ApiResponse.ok(service.knowledge());
    }

    @DeleteMapping("/admin/knowledge/{id}")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    @Operation(summary = "Delete a knowledge-base document")
    public ApiResponse<Void> deleteKnowledge(@PathVariable Long id) {
        service.deleteKnowledge(id);
        return ApiResponse.message("Deleted");
    }
}
