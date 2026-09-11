package com.swyp.backend.notification.function;

import com.swyp.backend.notification.entity.Notification;
import com.swyp.backend.notification.entity.NotificationType;
import com.swyp.backend.notification.repository.NotificationRepository;
import com.swyp.backend.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationFunction {

	private final NotificationRepository notificationRepository;

	public long countUnread(Long userId) {
		return notificationRepository.countByUserIdAndReadAtIsNull(userId);
	}

	public Notification notify(
			User user, NotificationType type, String title, String body, String deepLink) {
		return notificationRepository.save(new Notification(user, type, title, body, deepLink));
	}
}
