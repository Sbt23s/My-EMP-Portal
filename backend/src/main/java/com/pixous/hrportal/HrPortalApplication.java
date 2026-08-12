package com.pixous.hrportal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * HR Portal — backend entry point.
 * IT &amp; Civil industry HR platform. See /docs for the full requirements analysis.
 */
@SpringBootApplication(exclude = {org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration.class})
@ConfigurationPropertiesScan
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
@EnableAsync
@EnableScheduling
public class HrPortalApplication {

    /**
     * The zone everything is stamped in. A container defaults to UTC, and the
     * portal writes wall-clock times — a punch, a message, a payslip date — with
     * no zone attached, so a UTC clock made every one of them read five and a
     * half hours early. Worse than the display: after half past six in the
     * evening the *date* rolled over, so a late punch was recorded against the
     * previous day.
     *
     * <p>Set here rather than left to the container. TZ and -Duser.timezone are
     * both already set in docker-compose and the clock was still UTC, so this
     * does not consult them — a deployment that genuinely runs elsewhere sets
     * APP_TIMEZONE instead.
     */
    private static final String DEFAULT_ZONE = "Asia/Kolkata";

    public static void main(String[] args) {
        String zone = System.getenv("APP_TIMEZONE");
        if (zone == null || zone.isBlank()) zone = DEFAULT_ZONE;
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone(zone));
        System.setProperty("user.timezone", zone);

        SpringApplication.run(HrPortalApplication.class, args);
    }
}
