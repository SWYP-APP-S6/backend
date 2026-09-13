package com.swyp.backend.hold.dto;

import java.time.Instant;

public record HoldRef(Long groupId, Long storeId, Instant expiresAt) {}
