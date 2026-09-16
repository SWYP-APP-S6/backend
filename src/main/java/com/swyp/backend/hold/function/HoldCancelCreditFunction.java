package com.swyp.backend.hold.function;

import com.swyp.backend.hold.entity.Hold;
import com.swyp.backend.hold.entity.HoldCancelCredit;
import com.swyp.backend.hold.entity.HoldCancelCreditEvent;
import com.swyp.backend.hold.entity.HoldCancelCreditReason;
import com.swyp.backend.hold.repository.HoldCancelCreditEventRepository;
import com.swyp.backend.hold.repository.HoldCancelCreditRepository;
import com.swyp.backend.user.entity.User;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class HoldCancelCreditFunction {

	private final HoldCancelCreditRepository holdCancelCreditRepository;
	private final HoldCancelCreditEventRepository holdCancelCreditEventRepository;

	public Optional<HoldCancelCredit> findOf(Long userId) {
		return holdCancelCreditRepository.findByUserId(userId);
	}

	public HoldCancelCredit getOrStart(User user, int startingCredits, Instant now) {
		return holdCancelCreditRepository.findByUserId(user.getId())
				.orElseGet(() -> holdCancelCreditRepository.save(
						new HoldCancelCredit(user, startingCredits, now)));
	}

	// 잔액이 실제로 움직였을 때만 부른다. 0 짜리 행은 "차감됐다"로 읽히므로 남기지 않는다.
	public void record(
			User user,
			@Nullable Hold hold,
			HoldCancelCreditReason reason,
			int delta,
			Instant now) {
		holdCancelCreditEventRepository.save(
				new HoldCancelCreditEvent(user, hold, reason, delta, now));
	}

	/** 넘긴 찜 중 취소권을 실제로 깎은 것들. 찜 내역이 행마다 표시하는 데 쓴다. */
	public Set<Long> chargedHoldIdsAmong(Collection<Long> holdIds) {
		if (holdIds.isEmpty()) {
			return Set.of();
		}
		List<Long> charged = holdCancelCreditEventRepository.findChargedHoldIds(holdIds);
		return Set.copyOf(charged);
	}

	public void deleteAllInvolving(Long userId) {
		holdCancelCreditEventRepository.deleteAllInvolving(userId);
		holdCancelCreditRepository.deleteByUserId(userId);
	}
}
