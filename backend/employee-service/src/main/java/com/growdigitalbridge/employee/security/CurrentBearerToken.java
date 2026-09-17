package com.growdigitalbridge.employee.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Exposes the caller's own validated bearer token so it can be relayed, unmodified, to
 * Organization Service when resolving team scope. This is a real on-behalf-of token relay,
 * not a service credential or bypass: if the caller isn't authenticated, there is no token
 * to relay and the downstream call fails closed.
 */
public final class CurrentBearerToken {

    private CurrentBearerToken() { }

    public static String resolve() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
            return jwtAuthentication.getToken().getTokenValue();
        }
        return null;
    }
}
