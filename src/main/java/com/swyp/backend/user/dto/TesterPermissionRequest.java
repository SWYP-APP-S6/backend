package com.swyp.backend.user.dto;

import jakarta.validation.constraints.NotNull;

public record TesterPermissionRequest(@NotNull Boolean allowed) {
}
