package com.swyp.backend.notification.service;

import com.swyp.backend.notification.entity.Notification;
import com.swyp.backend.notification.entity.NotificationType;
import com.swyp.backend.notification.repository.NotificationRepository;
import com.swyp.backend.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

	private final NotificationRepository notificationRepository;

	@Transactional
	public void notify(User user, NotificationType type, String title, String body, String deepLink) {
		notificationRepository.save(new Notification(user, type, title, body, deepLink));
	}
}
