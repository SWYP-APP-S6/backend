package com.swyp.backend.notification.service;

import com.swyp.backend.common.response.PageResponse;
import com.swyp.backend.notification.dto.NotificationResponse;
import com.swyp.backend.notification.dto.NotificationsReadResponse;
import com.swyp.backend.notification.dto.NotificationsResponse;
import com.swyp.backend.notification.entity.Notification;
import com.swyp.backend.notification.function.NotificationFunction;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

	private final NotificationFunction notificationFunction;
	private final Clock clock;

	public NotificationsResponse getNotifications(Long userId, Pageable pageable) {
		Page<Notification> notifications = notificationFunction.findInboxOf(userId, pageable);
		List<NotificationResponse> content = notifications.getContent().stream()
				.map(NotificationResponse::from)
				.toList();
		return new NotificationsResponse(
				notificationFunction.countUnread(userId), PageResponse.of(content, notifications));
	}

	@Transactional
	public NotificationResponse read(Long userId, Long notificationId) {
		Notification notification = notificationFunction.getOwnedBy(userId, notificationId);
		notification.markAsRead(Instant.now(clock));
		return NotificationResponse.from(notification);
	}

	@Transactional
	public NotificationsReadResponse readAll(Long userId) {
		Instant now = Instant.now(clock);
		List<Notification> unread = notificationFunction.findUnreadOf(userId);
		unread.forEach(notification -> notification.markAsRead(now));
		return new NotificationsReadResponse(unread.size());
	}
}
