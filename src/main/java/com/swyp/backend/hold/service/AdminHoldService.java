package com.swyp.backend.hold.service;

import com.swyp.backend.common.response.PageResponse;
import com.swyp.backend.hold.dto.AdminHoldQuery;
import com.swyp.backend.hold.dto.AdminHoldResponse;
import com.swyp.backend.hold.dto.CancelCreditBalance;
import com.swyp.backend.hold.entity.HoldCancelCredit;
import com.swyp.backend.hold.entity.HoldCancelCreditReason;
import com.swyp.backend.hold.exception.HoldErrorCode;
import com.swyp.backend.hold.function.HoldCancelCreditFunction;
import com.swyp.backend.hold.HoldProperties;
import com.swyp.backend.common.exception.BusinessException;
import com.swyp.backend.user.entity.User;
import com.swyp.backend.user.function.UserFunction;
import com.swyp.backend.hold.entity.Hold;
import com.swyp.backend.hold.function.HoldFunction;
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
public class AdminHoldService {

	private final HoldFunction holdFunction;
	private final HoldCancelCreditFunction holdCancelCreditFunction;
	private final HoldCancelCreditSettler holdCancelCreditSettler;
	private final UserFunction userFunction;
	private final HoldProperties holdProperties;
	private final Clock clock;

	public PageResponse<AdminHoldResponse> getHolds(AdminHoldQuery query, Pageable pageable) {
		Instant now = Instant.now(clock);
		Page<Hold> holds = holdFunction.findForAdmin(query, pageable);
		List<AdminHoldResponse> content = holds.getContent().stream()
				.map(hold -> AdminHoldResponse.from(hold, now))
				.toList();
		return PageResponse.of(content, holds);
	}

	@Transactional
	public CancelCreditBalance adjustCancelCredits(Long userId, int delta) {
		Instant now = Instant.now(clock);
		User user = userFunction.getByIdForUpdate(userId);
		HoldCancelCredit credit = holdCancelCreditSettler.settle(user, now);
		int moved = credit.adjust(delta, holdProperties.cancelCreditMax());
		if (moved == 0) {
			throw new BusinessException(HoldErrorCode.CREDIT_ADJUST_NO_EFFECT);
		}
		holdCancelCreditFunction.record(user, null, HoldCancelCreditReason.ADMIN_ADJUST, moved, now);
		return new CancelCreditBalance(
				credit.getCredits(),
				holdProperties.cancelCreditMax(),
				credit.nextRefillAt(holdProperties.cancelCreditRefill(), holdProperties.cancelCreditMax()));
	}
}
