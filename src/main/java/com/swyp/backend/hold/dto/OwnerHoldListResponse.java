package com.swyp.backend.hold.dto;

import com.swyp.backend.common.response.PageResponse;

public record OwnerHoldListResponse(
		OwnerHoldCounts counts, PageResponse<OwnerHoldSummaryResponse> holds) {
}
