package com.vikisol.arena.common.dto;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;

import java.util.List;

// One place that bounds every page/size a client can ask for. A negative page reads as the first
// page and size is clamped to 1..MAX_SIZE, so no request can pull an unbounded list. List
// endpoints that return a bare array (no PagedResponse) take the same page/size, defaulting to
// the first DEFAULT_LIST_SIZE rows - their JSON shape is unchanged (see API-CHANGES.md). Their
// paging state travels in two response headers instead of the body: X-Total-Count and
// X-Has-More (exposed to the browser in SecurityConfig's CORS setup).
public final class PageLimits {

    public static final String TOTAL_COUNT_HEADER = "X-Total-Count";
    public static final String HAS_MORE_HEADER = "X-Has-More";
    public static final int MAX_SIZE = 100;
    public static final int DEFAULT_LIST_SIZE = 100;

    private PageLimits() {
    }

    // The first page a bare-array list endpoint returns when the client sends no page/size.
    public static PageRequest firstPage() {
        return PageRequest.of(0, DEFAULT_LIST_SIZE);
    }

    public static int page(int page) {
        return Math.max(0, page);
    }

    public static int size(int size) {
        return Math.max(1, Math.min(size, MAX_SIZE));
    }

    public static PageRequest of(int page, int size) {
        return PageRequest.of(page(page), size(size));
    }

    public static PageRequest of(int page, int size, Sort sort) {
        return PageRequest.of(page(page), size(size), sort);
    }

    // A bare-array list response: the page's rows as `data`, the totals as headers.
    public static <T> ResponseEntity<ApiResponse<List<T>>> ok(Page<T> page) {
        return ResponseEntity.ok()
                .header(TOTAL_COUNT_HEADER, String.valueOf(page.getTotalElements()))
                .header(HAS_MORE_HEADER, String.valueOf(page.hasNext()))
                .body(ApiResponse.ok(page.getContent()));
    }

    // For lists that are ranked in memory before they can be cut.
    public static <T> Page<T> page(List<T> all, int page, int size) {
        return new PageImpl<>(slice(all, page, size), of(page, size), all.size());
    }

    public static <T> List<T> slice(List<T> all, int page, int size) {
        long from = (long) page(page) * size(size);
        if (from >= all.size()) return List.of();
        return all.subList((int) from, (int) Math.min(all.size(), from + size(size)));
    }
}
