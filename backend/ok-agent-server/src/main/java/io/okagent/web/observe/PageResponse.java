package io.okagent.web.observe;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * Stable, flat pagination projection used in place of serializing a Spring Data
 * {@link Page} directly. The field set matches the observability frontend's
 * {@code SessionPage} contract ({@code content/totalElements/totalPages/number/size});
 * without it, Spring emits a WARN about the non-guaranteed JSON shape of {@code PageImpl}.
 */
public record PageResponse<T>(List<T> content, long totalElements, int totalPages, int number, int size) {

    static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(
                page.getContent(), page.getTotalElements(), page.getTotalPages(), page.getNumber(), page.getSize());
    }
}
