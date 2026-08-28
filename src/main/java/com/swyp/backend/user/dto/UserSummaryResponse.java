package com.swyp.backend.user.dto;

import com.swyp.backend.user.entity.User;
import java.time.Instant;

public record UserSummaryResponse(
		Long id,
		String role,
		String nickname,
		String phone,
		String oauthProvider,
		String regionName,
		boolean marketingOptIn,
		Instant termsAgreedAt,
		Instant createdAt) {

	public static UserSummaryResponse of(User user, String regionName) {
		return new UserSummaryResponse(
				user.getId(),
				user.getRole().name(),
				user.getNickname(),
				user.getPhone(),
				user.getOauthProvider(),
				regionName,
				user.isMarketingOptIn(),
				user.getTermsAgreedAt(),
				user.getCreatedAt());
	}
}
