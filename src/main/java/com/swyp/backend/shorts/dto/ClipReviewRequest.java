package com.swyp.backend.shorts.dto;

import jakarta.validation.constraints.Pattern;

public record ClipReviewRequest(@Pattern(regexp = "OK|NG") String verdict, String note) {
}
