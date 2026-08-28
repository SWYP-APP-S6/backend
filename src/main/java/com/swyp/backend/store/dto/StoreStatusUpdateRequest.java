package com.swyp.backend.store.dto;

import com.swyp.backend.store.entity.StoreStatus;
import jakarta.validation.constraints.NotNull;

public record StoreStatusUpdateRequest(@NotNull StoreStatus status) {
}
