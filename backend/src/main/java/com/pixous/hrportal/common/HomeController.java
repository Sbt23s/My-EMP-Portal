package com.pixous.hrportal.common;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Friendly landing response for the API root. This backend serves only the REST
 * API (the UI lives on the separate web/Netlify deployment), so hitting "/" used
 * to return a bare 403 Whitelabel page. This returns a clear status instead.
 */
@RestController
public class HomeController {

    @GetMapping("/")
    public Map<String, Object> home() {
        return Map.of(
                "app", "Pixous HR Portal API",
                "status", "running",
                "health", "/actuator/health",
                "docs", "/swagger-ui.html",
                "note", "This is the API server. Open the web app URL to use the portal."
        );
    }
}
