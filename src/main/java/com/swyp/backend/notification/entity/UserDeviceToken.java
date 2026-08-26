package com.swyp.backend.notification.entity;

import com.swyp.backend.common.BaseTimeEntity;
import com.swyp.backend.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
		name = "user_device_tokens",
		uniqueConstraints = @UniqueConstraint(columnNames = "fcm_token"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserDeviceToken extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private DevicePlatform platform;

	@Column(name = "fcm_token", nullable = false, length = 255)
	private String fcmToken;

	@Column(name = "last_used_at", nullable = false)
	private Instant lastUsedAt;

	public UserDeviceToken(
			User user, DevicePlatform platform, String fcmToken, Instant lastUsedAt) {
		this.user = user;
		this.platform = platform;
		this.fcmToken = fcmToken;
		this.lastUsedAt = lastUsedAt;
	}

	public void reassignTo(User user, DevicePlatform platform, Instant lastUsedAt) {
		this.user = user;
		this.platform = platform;
		this.lastUsedAt = lastUsedAt;
	}

	public void touch(Instant lastUsedAt) {
		this.lastUsedAt = lastUsedAt;
	}
}
