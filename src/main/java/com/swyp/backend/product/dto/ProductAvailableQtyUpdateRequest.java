package com.swyp.backend.product.dto;

import jakarta.validation.constraints.Min;

public record ProductAvailableQtyUpdateRequest(@Min(0) int availableQty, HoldDisposition disposition) {
}
