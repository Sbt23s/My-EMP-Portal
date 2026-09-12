package com.pixous.hrportal.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Says which requests are slow, so optimisation can start from a measurement.
 *
 * <p>"The application feels slow" is a real report and a useless instruction:
 * a dashboard makes a dozen calls and only one of them is usually the problem,
 * and rewriting the other eleven is work that buys nothing. This records how
 * long each one actually took.
 *
 * <p>Only the slow ones are logged. A line per request would bury the signal
 * and cost disk on a server that has little to spare; the threshold is
 * configurable so it can be lowered while hunting something and raised again
 * afterwards.
 *
 * <p>Deliberately logs the method, the path pattern and the duration, and
 * nothing else. No query string, no body, no user: an employee code or a
 * search term in a log file is personal data that has escaped the database,
 * and the timing question does not need it.
 */
@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class SlowRequestFilter extends OncePerRequestFilter {

    /** Above this many milliseconds a request is worth knowing about. */
    @Value("${app.slow-request-ms:800}")
    private long slowMs;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        long started = System.nanoTime();
        try {
            chain.doFilter(req, res);
        } finally {
            long ms = (System.nanoTime() - started) / 1_000_000;
            if (ms >= slowMs) {
                // The path only. A request to /api/users?q=balaji is logged as
                // /api/users, because the timing is about the endpoint and the
                // search term belongs to the person who typed it.
                log.warn("SLOW {} {} took {} ms (status {})",
                        req.getMethod(), req.getRequestURI(), ms, res.getStatus());
            }
        }
    }

    /** Static assets are not the application, and there are a great many. */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest req) {
        String p = req.getRequestURI();
        return !p.startsWith("/api/") || p.startsWith("/api/actuator");
    }
}
