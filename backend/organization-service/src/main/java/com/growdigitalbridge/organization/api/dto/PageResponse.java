package com.growdigitalbridge.organization.api.dto;

import java.util.List;
import org.springframework.data.domain.Page;

public record PageResponse<T>(List<T> items, PageMeta page) {

    public record PageMeta(int number, int size, long total) { }

    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(page.getContent(), new PageMeta(page.getNumber(), page.getSize(), page.getTotalElements()));
    }
}
