package com.pixous.hrportal.config;

import com.pixous.hrportal.security.SecurityUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;

import java.util.Optional;

/** Supplies the "who" for JPA auditing ({@code createdBy}/{@code updatedBy}). */
@Configuration
public class JpaConfig {

    @Bean(name = "auditorAware")
    public AuditorAware<String> auditorAware() {
        return () -> SecurityUtils.currentPrincipal()
                .map(p -> String.valueOf(p.getId()))
                .or(() -> Optional.of("system"));
    }
}
