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
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "notifications")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private NotificationType type;

	@Column(nullable = false, length = 100)
	private String title;

	@Column(nullable = false, length = 255)
	private String body;

	@Column(name = "deep_link", length = 255)
	private String deepLink;

	@Column(name = "read_at")
	private Instant readAt;

	public Notification(
			User user, NotificationType type, String title, String body, String deepLink) {
		this.user = user;
		this.type = type;
		this.title = title;
		this.body = body;
		this.deepLink = deepLink;
	}

	public void markAsRead(Instant readAt) {
		if (this.readAt == null) {
			this.readAt = readAt;
		}
	}
}
