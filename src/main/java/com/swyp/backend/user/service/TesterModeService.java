package com.swyp.backend.user.service;

import com.swyp.backend.common.exception.BusinessException;
import com.swyp.backend.hold.function.HoldFunction;
import com.swyp.backend.user.dto.MeResponse;
import com.swyp.backend.user.entity.User;
import com.swyp.backend.user.exception.UserAuthErrorCode;
import com.swyp.backend.user.function.UserFunction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TesterModeService {

	private final UserFunction userFunction;
	private final HoldFunction holdFunction;

	@Transactional
	public MeResponse switchMine(Long userId, boolean tester) {
		User user = userFunction.getByIdForUpdate(userId);
		if (user.isTester() != tester) {
			if (tester && !user.isTesterAllowed()) {
				throw new BusinessException(UserAuthErrorCode.TESTER_NOT_ALLOWED);
			}
			requireNoOpenHolds(userId);
			user.changeTester(tester);
		}
		return MeResponse.from(user);
	}

	@Transactional
	public void changePermission(Long userId, boolean allowed) {
		User user = userFunction.getByIdForUpdate(userId);
		if (allowed) {
			user.allowTesting();
			return;
		}
		if (user.isTester()) {
			requireNoOpenHolds(userId);
		}
		user.revokeTesting();
	}

	private void requireNoOpenHolds(Long userId) {
		if (holdFunction.hasHoldingOf(userId) || holdFunction.hasHoldingAtStoreOwnedBy(userId)) {
			throw new BusinessException(UserAuthErrorCode.TESTER_CHANGE_BLOCKED_BY_HOLDS);
		}
	}
}
