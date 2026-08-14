package com.pixous.hrportal.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.List;

/** CORS driven by {@code app.cors.allowed-origins} so the web/mobile dev servers work. */
@Configuration
public class WebConfig implements org.springframework.web.servlet.config.annotation.WebMvcConfigurer {

    private final AppProperties props;
    private final com.pixous.hrportal.modules.admin.UsageTracker usageTracker;

    public WebConfig(AppProperties props, com.pixous.hrportal.modules.admin.UsageTracker usageTracker) {
        this.props = props;
        this.usageTracker = usageTracker;
    }

    /**
     * An interceptor rather than a servlet filter, deliberately.
     *
     * Interceptors run after the security chain, so the signed-in person is
     * already known. A filter would have to be ordered by hand relative to
     * authentication, and getting that wrong records nothing — silently.
     */
    @Override
    public void addInterceptors(org.springframework.web.servlet.config.annotation.InterceptorRegistry registry) {
        registry.addInterceptor(usageTracker).addPathPatterns("/api/**");
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(props.cors().allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Authorization"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
