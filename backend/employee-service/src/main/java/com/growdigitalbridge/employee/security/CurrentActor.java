package com.growdigitalbridge.employee.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** Resolves the audit "actor" identity from the validated JWT subject, never from client input. */
public final class CurrentActor {

    private CurrentActor() { }

    public static String resolve() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
            return jwtAuthentication.getToken().getSubject();
        }
        return "system";
    }
}
