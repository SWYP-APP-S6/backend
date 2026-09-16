package com.swyp.backend.user.dto;

import com.swyp.backend.store.entity.Store;
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
		Instant createdAt,
		@Nullable OwnedStore store) {

	public record OwnedStore(Long id, String name, String status) {
	}

	public static UserSummaryResponse of(User user, @Nullable String regionName, @Nullable Store store) {
		return new UserSummaryResponse(
				user.getId(),
				user.getRole().name(),
				user.getNickname(),
				user.getPhone(),
				user.getOauthProvider(),
				regionName,
				user.isMarketingOptIn(),
				user.getTermsAgreedAt(),
				user.getCreatedAt(),
				store == null
						? null
						: new OwnedStore(store.getId(), store.getName(), store.getStatus().name()));
	}
}
