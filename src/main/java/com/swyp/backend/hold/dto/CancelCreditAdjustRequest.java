package com.swyp.backend.hold.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CancelCreditAdjustRequest(@NotNull @Min(-10) @Max(10) Integer delta) {
}
