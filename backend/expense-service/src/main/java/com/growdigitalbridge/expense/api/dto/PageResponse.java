package com.growdigitalbridge.expense.api.dto;

import java.util.List;
import org.springframework.data.domain.Page;

public record PageResponse<T>(List<T> items, PageMeta page) {

    /**
     * The authorization scope that actually produced this page - SELF/TEAM/ALL - taken directly
     * from {@link com.growdigitalbridge.expense.service.ExpenseAccessGuard.ListScope}'s own
     * decision (Reporting V1 authorization review, Part A: docs/REPORTING_AUTHORIZATION_REVIEW.md).
     * Never inferred from the returned claims or their count - computed before the query runs, so
     * it is correct even when {@code items} is empty.
     */
    public enum ResponseScope { SELF, TEAM, ALL }

    public record PageMeta(int number, int size, long total, ResponseScope scope) { }

    public static <T> PageResponse<T> of(Page<T> page, ResponseScope scope) {
        return new PageResponse<>(page.getContent(), new PageMeta(page.getNumber(), page.getSize(), page.getTotalElements(), scope));
    }

    public static <T> PageResponse<T> empty(int page, int size, ResponseScope scope) {
        return new PageResponse<>(List.of(), new PageMeta(page, size, 0, scope));
    }
}
