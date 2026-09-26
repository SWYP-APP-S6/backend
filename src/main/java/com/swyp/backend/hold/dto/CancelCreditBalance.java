package com.swyp.backend.hold.dto;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

public record CancelCreditBalance(int credits, int max, @Nullable Instant nextRefillAt) {
}
