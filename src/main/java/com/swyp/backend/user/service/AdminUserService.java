package com.swyp.backend.user.service;

import com.swyp.backend.common.exception.BusinessException;
import com.swyp.backend.hold.function.HoldCancelCreditFunction;
import com.swyp.backend.hold.function.HoldFunction;
import com.swyp.backend.notification.function.DeviceTokenFunction;
import com.swyp.backend.notification.function.NotificationFunction;
import com.swyp.backend.store.function.StoreFunction;
import com.swyp.backend.terms.function.TermsFunction;
import com.swyp.backend.user.dto.AdminUserDetailResponse;
import com.swyp.backend.user.entity.User;
import com.swyp.backend.user.exception.UserAuthErrorCode;
import com.swyp.backend.user.function.UserFunction;
import com.swyp.backend.user.function.UserLocationFunction;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminUserService {

	private static final int RECENT_NOTIFICATIONS = 10;

	private final UserFunction userFunction;
	private final UserLocationFunction userLocationFunction;
	private final StoreFunction storeFunction;
	private final HoldFunction holdFunction;
	private final HoldCancelCreditFunction holdCancelCreditFunction;
	private final NotificationFunction notificationFunction;
	private final DeviceTokenFunction deviceTokenFunction;
	private final TermsFunction termsFunction;
	private final Clock clock;

	public AdminUserDetailResponse getUser(Long userId) {
		Instant now = Instant.now(clock);
		User user = userFunction.getById(userId);
		return AdminUserDetailResponse.of(
				user,
				userLocationFunction.findOf(userId).orElse(null),
				storeFunction.findByOwnerIds(List.of(userId)).get(userId),
				holdCancelCreditFunction.balanceAsOf(userId, now),
				holdCancelCreditFunction.findEventsOf(userId),
				holdFunction.countUserHoldsByOwnerStatus(userId),
				notificationFunction.countUnread(userId),
				notificationFunction.findInboxOf(userId, PageRequest.of(0, RECENT_NOTIFICATIONS)).getContent(),
				deviceTokenFunction.findTokensOf(userId),
				termsFunction.findAgreementsOf(userId));
	}

	@Transactional
	public void changeTesterPermission(Long userId, boolean allowed) {
		User user = userFunction.getByIdForUpdate(userId);
		if (user.isTester()) {
			throw new BusinessException(UserAuthErrorCode.TESTER_PERMISSION_ON_TEST_ACCOUNT);
		}
		if (allowed) {
			user.allowTesting();
			user.enterTestMode();
			userFunction.getOrCreateTestAccountOf(user, Instant.now(clock));
		} else {
			user.revokeTesting();
		}
	}
}
