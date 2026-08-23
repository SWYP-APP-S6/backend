package com.swyp.backend.common.response;

import java.util.List;
import org.springframework.data.domain.Page;

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
