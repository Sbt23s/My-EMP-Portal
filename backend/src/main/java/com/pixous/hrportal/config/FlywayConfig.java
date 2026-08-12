package com.pixous.hrportal.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Custom migration strategy that always runs {@code repair()} before
 * {@code migrate()}.
 *
 * <p>Why: if a migration ever fails halfway (e.g. a transient DB hiccup) it is
 * recorded in {@code flyway_schema_history} with {@code success = 0}. On the
 * next start, plain {@code migrate()} refuses to proceed and throws
 * <em>"Detected failed migration to version N"</em>. {@code repair()} removes
 * that failed marker (and realigns checksums when a migration file was edited
 * after it was first applied), so the subsequent {@code migrate()} can move
 * forward. On a clean or already-consistent history, {@code repair()} is a
 * harmless no-op.
 */
@Slf4j
@Configuration
public class FlywayConfig {

    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy() {
        return flyway -> {
            try {
                flyway.repair();
            } catch (Exception e) {
                // Never let a repair problem block startup; migrate() will
                // surface any genuine schema issue with a clearer message.
                log.warn("Flyway repair skipped: {}", e.getMessage());
            }
            flyway.migrate();
        };
    }
}
