package com.swyp.backend.shorts.dto;

import jakarta.validation.constraints.NotBlank;

public record SourceCreateRequest(
		@NotBlank String path,
		@NotBlank String title,
		String contentType,
		String origin,
		String context) {
}
