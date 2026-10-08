package com.swyp.backend.common.security;

import org.jspecify.annotations.Nullable;
import org.springframework.security.core.Authentication;

public final class AppUserPrincipal {

	private AppUserPrincipal() {
	}

	public static @Nullable Long idOf(@Nullable Authentication authentication) {
		if (authentication == null || !(authentication.getPrincipal() instanceof Long id)) {
			return null;
		}
		return TokenRealm.fromAuthorities(authentication.getAuthorities())
				.filter(realm -> realm == TokenRealm.USER)
				.map(realm -> id)
				.orElse(null);
	}
}
