package com.swyp.backend.hold.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record HoldCreateRequest(@NotNull Long productId, @NotNull @Min(1) Integer qty) {
}
