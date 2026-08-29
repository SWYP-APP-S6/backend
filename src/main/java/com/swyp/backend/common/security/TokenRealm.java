package com.swyp.backend.common.security;

public enum TokenRealm {

	ADMIN,
	USER;

	public String authority() {
		return "REALM_" + name();
	}
}
