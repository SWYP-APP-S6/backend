package com.swyp.backend.common.security;

import java.util.Collection;
import java.util.Optional;
import org.springframework.security.core.GrantedAuthority;

public enum TokenRealm {

	ADMIN,
	USER,
	GUEST;

	private static final String AUTHORITY_PREFIX = "REALM_";

	public String authority() {
		return AUTHORITY_PREFIX + name();
	}

	public static Optional<TokenRealm> fromAuthorities(Collection<? extends GrantedAuthority> authorities) {
		for (GrantedAuthority authority : authorities) {
			String name = authority.getAuthority();
			if (name != null && name.startsWith(AUTHORITY_PREFIX)) {
				for (TokenRealm realm : values()) {
					if (realm.authority().equals(name)) {
						return Optional.of(realm);
					}
				}
			}
		}
		return Optional.empty();
	}
}
