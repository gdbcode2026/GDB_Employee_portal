package com.growdigitalbridge.performance.api;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/** Parses the documented {@code page,size,sort} collection-endpoint parameters. */
final class PagingSupport {

    private PagingSupport() { }

    static Pageable of(int page, int size, String sort, String defaultProperty) {
        int boundedSize = Math.min(Math.max(size, 1), 200);
        int boundedPage = Math.max(page, 0);
        return PageRequest.of(boundedPage, boundedSize, parseSort(sort, defaultProperty));
    }

    private static Sort parseSort(String sort, String defaultProperty) {
        if (sort == null || sort.isBlank()) {
            return Sort.by(defaultProperty).ascending();
        }
        String[] parts = sort.split(",", 2);
        String property = parts[0].isBlank() ? defaultProperty : parts[0];
        boolean descending = parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim());
        return descending ? Sort.by(property).descending() : Sort.by(property).ascending();
    }
}
