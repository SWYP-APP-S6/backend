package com.swyp.backend.user.dto;

import com.swyp.backend.user.entity.User;
import com.swyp.backend.user.entity.UserRole;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

public record MeResponse(
		Long id,
		UserRole role,
		String nickname,
		@Nullable String phone,
		boolean marketingOptIn,
		boolean testerAllowed,
		boolean tester,
		Instant termsAgreedAt,
		Instant joinedAt) {

	public static MeResponse from(User user) {
		return new MeResponse(
				user.getId(),
				user.getRole(),
				user.getNickname(),
				user.getPhone(),
				user.isMarketingOptIn(),
				user.isTesterAllowed(),
				user.isTester(),
				user.getTermsAgreedAt(),
				user.getCreatedAt());
	}
}
