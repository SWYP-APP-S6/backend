package com.swyp.backend.admin.entity;

/**
 * Admin role. Persisted as the enum name via {@code @Enumerated(STRING)}; the {@code admins.type}
 * CHECK constraint lists these exact (uppercase) names, so keep the two in sync.
 */
public enum AdminType {
	SUPER,
	MANAGER,
	DEVELOPER
}
