package com.swyp.backend.user.dto;

import jakarta.validation.constraints.NotNull;

public record TesterChangeRequest(@NotNull Boolean tester) {
}
