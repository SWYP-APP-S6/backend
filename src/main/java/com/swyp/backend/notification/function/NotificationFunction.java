package com.swyp.backend.notification.function;

import com.swyp.backend.common.exception.BusinessException;
import com.swyp.backend.notification.entity.Notification;
import com.swyp.backend.notification.entity.NotificationType;
import com.swyp.backend.notification.exception.NotificationErrorCode;
import com.swyp.backend.notification.repository.NotificationRepository;
import com.swyp.backend.user.entity.User;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationFunction {

	private final NotificationRepository notificationRepository;

	public long countUnread(Long userId) {
		return notificationRepository.countByUserIdAndReadAtIsNull(userId);
	}

	public Page<Notification> findInboxOf(Long userId, Pageable pageable) {
		return notificationRepository.findByUserId(
				userId,
				PageRequest.of(
						pageable.getPageNumber(),
						pageable.getPageSize(),
						Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"))));
	}

	public List<Notification> findUnreadOf(Long userId) {
		return notificationRepository.findByUserIdAndReadAtIsNull(userId);
	}

	public Notification getOwnedBy(Long userId, Long notificationId) {
		return notificationRepository.findByIdAndUserId(notificationId, userId)
				.orElseThrow(() -> new BusinessException(NotificationErrorCode.NOTIFICATION_NOT_FOUND));
	}

	public Notification notify(
			User user, NotificationType type, String title, String body, String deepLink) {
		return notificationRepository.save(new Notification(user, type, title, body, deepLink));
	}
}
