package com.swyp.backend.user.dto;

import com.swyp.backend.user.entity.User;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

public record UserSummaryResponse(
		Long id,
		String role,
		String nickname,
		@Nullable String phone,
		@Nullable String oauthProvider,
		@Nullable String regionName,
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
