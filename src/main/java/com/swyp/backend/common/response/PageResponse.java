package com.swyp.backend.common.response;

import java.util.List;
import org.springframework.data.domain.Page;

/** Pagination envelope for list endpoints. {@code content} may be a different type than the source
 *  {@link Page} (e.g. entities mapped to a summary DTO) — pagination metadata still comes from it. */
public record PageResponse<T>(
		List<T> content, int page, int size, long totalElements, int totalPages, boolean last) {

	public static <T> PageResponse<T> of(List<T> content, Page<?> source) {
		return new PageResponse<>(
				content,
				source.getNumber(),
				source.getSize(),
				source.getTotalElements(),
				source.getTotalPages(),
				source.isLast());
	}
}
