package com.swyp.backend.hold.dto;

import java.time.Instant;

public record HoldRef(Long holdId, Long storeId, Instant expiresAt) {}
