package com.swyp.backend.notification.dto;

import com.swyp.backend.notification.entity.NotificationPushState;
import java.time.Instant;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public record PushOutboxSummaryResponse(
		boolean pushEnabled,
		Counts allTime,
		Counts last24Hours,
		@Nullable Instant oldestPendingAt) {

	public record Counts(long pending, long sent, long skipped, long failed) {
		public static Counts from(Map<NotificationPushState, Long> counts) {
			return new Counts(
					counts.getOrDefault(NotificationPushState.PENDING, 0L),
					counts.getOrDefault(NotificationPushState.SENT, 0L),
					counts.getOrDefault(NotificationPushState.SKIPPED, 0L),
					counts.getOrDefault(NotificationPushState.FAILED, 0L));
		}
	}
}
