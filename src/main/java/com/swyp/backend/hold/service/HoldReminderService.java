package com.swyp.backend.hold.service;

import com.swyp.backend.hold.HoldProperties;
import com.swyp.backend.hold.entity.Hold;
import com.swyp.backend.hold.function.HoldFunction;
import com.swyp.backend.notification.DeepLinks;
import com.swyp.backend.notification.entity.NotificationType;
import com.swyp.backend.notification.function.NotificationFunction;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class HoldReminderService {

	private final HoldFunction holdFunction;
	private final NotificationFunction notificationFunction;
	private final HoldProperties holdProperties;
	private final Clock clock;

	@Scheduled(
			fixedDelayString = "${hold.expiry-scan-interval}",
			initialDelayString = "${hold.expiry-scan-interval}")
	@Transactional
	public int remindExpiringHolds() {
		Instant now = Instant.now(clock);
		List<Hold> expiringSoon =
				holdFunction.findExpiringSoon(now, holdProperties.expiryReminderLead());

		int reminded = 0;
		Set<Long> told = new HashSet<>();
		for (Hold hold : expiringSoon) {
			if (!holdFunction.markExpiryReminded(hold.getId(), now)) {
				continue;
			}
			if (!told.add(hold.getGroupId())) {
				continue;
			}
			notificationFunction.notify(
					hold.getUser(),
					NotificationType.HOLD_EXPIRING_SOON,
					"찜 시간이 곧 끝나요",
					hold.getStore().getName() + " 픽업 마감이 얼마 남지 않았어요.",
					DeepLinks.consumerHold(hold.getId()));
			reminded++;
		}
		if (reminded > 0) {
			log.info("Reminded {} holds that are about to expire", reminded);
		}
		return reminded;
	}
}
