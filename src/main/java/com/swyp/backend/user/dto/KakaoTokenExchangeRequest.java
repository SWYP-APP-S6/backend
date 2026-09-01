package com.swyp.backend.user.dto;

import jakarta.validation.constraints.NotBlank;

public record KakaoTokenExchangeRequest(@NotBlank String code, @NotBlank String redirectUri) {
}
