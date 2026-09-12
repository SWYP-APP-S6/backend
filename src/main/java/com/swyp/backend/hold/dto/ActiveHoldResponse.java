package com.swyp.backend.hold.dto;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

public record ActiveHoldResponse(
		@Nullable HoldDetailResponse hold,
		int cancelsLeft,
		@Nullable Instant nextCancelCreditAt) {}
