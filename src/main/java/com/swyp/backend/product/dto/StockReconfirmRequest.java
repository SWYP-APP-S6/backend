package com.swyp.backend.product.dto;

import jakarta.validation.constraints.NotNull;

public record StockReconfirmRequest(@NotNull Boolean confirmed) {
}
