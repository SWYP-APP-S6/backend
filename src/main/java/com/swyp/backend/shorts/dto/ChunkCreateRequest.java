package com.swyp.backend.shorts.dto;

import jakarta.validation.constraints.PositiveOrZero;

public record ChunkCreateRequest(@PositiveOrZero double startSec, @PositiveOrZero double endSec) {
}
