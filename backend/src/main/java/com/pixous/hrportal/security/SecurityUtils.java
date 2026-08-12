package com.pixous.hrportal.security;

import com.pixous.hrportal.common.ApiException;
import com.pixous.hrportal.common.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/** Convenience accessors for the authenticated principal. */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static Optional<UserPrincipal> currentPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
            return Optional.of(principal);
        }
        return Optional.empty();
    }

    public static Long currentUserId() {
        return currentPrincipal()
                .map(UserPrincipal::getId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED, "Not authenticated"));
    }

    public static Long currentCompanyId() {
        return currentPrincipal()
                .map(UserPrincipal::getCompanyId)
                .orElse(null);
    }

    public static boolean hasAuthority(String authority) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(authority));
    }
}
