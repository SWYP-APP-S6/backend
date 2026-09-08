package com.swyp.backend.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GuestTokenRequest(@NotBlank @Size(max = 64) String installId) {
}
