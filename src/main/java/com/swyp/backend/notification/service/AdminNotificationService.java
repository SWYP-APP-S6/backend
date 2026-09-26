package com.swyp.backend.notification.service;

import com.swyp.backend.common.response.PageResponse;
import com.swyp.backend.notification.dto.AdminNotificationQuery;
import com.swyp.backend.notification.dto.AdminNotificationResponse;
import com.swyp.backend.notification.dto.PushOutboxSummaryResponse;
import com.swyp.backend.notification.entity.Notification;
import com.swyp.backend.notification.function.NotificationFunction;
import java.time.Clock;
import java.time.Duration;
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
public class AdminNotificationService {

	private static final Duration RECENT_WINDOW = Duration.ofHours(24);

	private final NotificationFunction notificationFunction;
	private final PushSender pushSender;
	private final Clock clock;

	public PushOutboxSummaryResponse getPushSummary() {
		Instant now = Instant.now(clock);
		return new PushOutboxSummaryResponse(
				pushSender.isEnabled(),
				PushOutboxSummaryResponse.Counts.from(
						notificationFunction.countByPushStateSince(Instant.EPOCH)),
				PushOutboxSummaryResponse.Counts.from(
						notificationFunction.countByPushStateSince(now.minus(RECENT_WINDOW))),
				notificationFunction.findOldestPendingAt().orElse(null));
	}

	public PageResponse<AdminNotificationResponse> getNotifications(
			AdminNotificationQuery query, Pageable pageable) {
		Page<Notification> notifications = notificationFunction.findForAdmin(query, pageable);
		List<AdminNotificationResponse> content = notifications.getContent().stream()
				.map(AdminNotificationResponse::from)
				.toList();
		return PageResponse.of(content, notifications);
	}

	public AdminNotificationResponse getNotification(Long notificationId) {
		return AdminNotificationResponse.from(notificationFunction.getById(notificationId));
	}
}
