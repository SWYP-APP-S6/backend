package com.swyp.backend.dev.dto;

import com.swyp.backend.user.entity.UserRole;

public record DevTokenResponse(
		Long userId, String nickname, UserRole role, String accessToken, long expiresInSeconds) {}
