package com.swyp.backend.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record TestModeSwitchRequest(@NotNull Boolean on, @NotBlank String refreshToken) {
}
