package com.swyp.backend.hold.dto;

import com.swyp.backend.common.response.PageResponse;
import java.time.Instant;

public record HoldHistoryResponse(
		Instant serverTime, PageResponse<HoldSummaryResponse> holds) {}
