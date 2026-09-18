package com.growdigitalbridge.document.security;

import com.growdigitalbridge.document.filter.CorrelationIdFilter;
import java.util.UUID;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Reads the correlation ID that {@link CorrelationIdFilter} attached to the current request. */
public final class CurrentCorrelation {

    private CurrentCorrelation() { }

    public static UUID resolve() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes servletAttributes) {
            Object value = servletAttributes.getRequest().getAttribute(CorrelationIdFilter.HEADER);
            if (value instanceof UUID correlationId) {
                return correlationId;
            }
        }
        return UUID.randomUUID();
    }
}
