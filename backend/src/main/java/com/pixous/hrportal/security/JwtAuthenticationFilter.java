package com.pixous.hrportal.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Reads the Bearer token, validates it, and populates the security context. */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtService jwtService,
                                   CustomUserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                if (jwtService.isValid(token)
                        && SecurityContextHolder.getContext().getAuthentication() == null) {
                    Long userId = jwtService.extractUserId(token);
                    
                    // Default to USER if claim is missing for backwards compatibility
                    String userType = "USER";
                    try {
                        Object typeClaim = jwtService.claimsAsMap(token).get("userType");
                        if (typeClaim != null) {
                            userType = typeClaim.toString();
                        }
                    } catch (Exception ignored) { }

                    UserDetails details;
                    if ("TECHNICAL_ADMIN".equals(userType)) {
                        com.pixous.hrportal.modules.admin.TechnicalAdmin admin = 
                            org.springframework.web.context.support.WebApplicationContextUtils
                                .getRequiredWebApplicationContext(request.getServletContext())
                                .getBean(com.pixous.hrportal.modules.admin.TechnicalAdminRepository.class)
                                .findById(userId)
                                .orElseThrow(() -> new org.springframework.security.core.userdetails.UsernameNotFoundException("Admin not found"));
                        details = new TechnicalAdminPrincipal(admin);
                    } else {
                        details = userDetailsService.loadById(userId);
                    }

                    if (details.isEnabled() && details.isAccountNonLocked()) {
                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(
                                        details, null, details.getAuthorities());
                        authentication.setDetails(
                                new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    }
                }
            } catch (Exception ex) {
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }
}
