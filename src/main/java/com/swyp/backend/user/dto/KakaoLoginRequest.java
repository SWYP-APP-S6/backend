package com.swyp.backend.user.dto;

import jakarta.validation.constraints.NotBlank;

public record KakaoLoginRequest(@NotBlank String kakaoAccessToken) {
}
