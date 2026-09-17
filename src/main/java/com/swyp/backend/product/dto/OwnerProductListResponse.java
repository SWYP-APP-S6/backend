package com.swyp.backend.product.dto;

import com.swyp.backend.common.response.PageResponse;
import java.time.Instant;

public record OwnerProductListResponse(
		Instant serverTime, PageResponse<OwnerProductSummaryResponse> products) {
}
