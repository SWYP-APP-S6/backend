package com.swyp.backend.product.dto;

import java.time.LocalDateTime;

public record StoreProductSummary(
		Long storeId, Long productCount, LocalDateTime earliestPickupEndAt) {}
