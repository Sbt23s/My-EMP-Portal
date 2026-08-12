package com.pixous.hrportal.modules.presence;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/presence")
@RequiredArgsConstructor
public class PresenceController {

    private final PresenceService presenceService;

    /**
     * Who is online now, and when everybody was last seen. The live updates
     * arrive on {@code /topic/presence}; this is the starting picture.
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> snapshot() {
        return ResponseEntity.ok(presenceService.snapshot());
    }
}
