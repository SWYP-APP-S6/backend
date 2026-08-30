package com.swyp.backend.shorts.dto;

import jakarta.validation.constraints.NotBlank;

public record SourceFromUrlRequest(@NotBlank String url, String context) {
}
