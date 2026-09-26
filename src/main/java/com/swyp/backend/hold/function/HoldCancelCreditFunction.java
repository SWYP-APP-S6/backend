package com.swyp.backend.hold.function;

import com.swyp.backend.hold.HoldProperties;
import com.swyp.backend.hold.dto.CancelCreditBalance;
import com.swyp.backend.hold.entity.Hold;
import com.swyp.backend.hold.entity.HoldCancelCredit;
import com.swyp.backend.hold.entity.HoldCancelCreditEvent;
import com.swyp.backend.hold.entity.HoldCancelCreditReason;
import com.swyp.backend.hold.repository.HoldCancelCreditEventRepository;
import com.swyp.backend.hold.repository.HoldCancelCreditRepository;
import com.swyp.backend.hold.repository.HoldRepository;
import com.swyp.backend.user.entity.User;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class HoldCancelCreditFunction {

	private final HoldCancelCreditRepository holdCancelCreditRepository;
	private final HoldCancelCreditEventRepository holdCancelCreditEventRepository;
	private final HoldRepository holdRepository;
	private final HoldProperties holdProperties;

	public Optional<HoldCancelCredit> findOf(Long userId) {
		return holdCancelCreditRepository.findByUserId(userId);
	}

	public HoldCancelCredit getOrStart(User user, int startingCredits, Instant now) {
		return holdCancelCreditRepository.findByUserId(user.getId())
				.orElseGet(() -> holdCancelCreditRepository.save(
						new HoldCancelCredit(user, startingCredits, now)));
	}

	// 정산을 저장하지 않고 "지금 정산하면 얼마인가"만 계산한다. 밀린 노쇼가 아직 안 깎였어도
	// 다음 찜에서 깎일 것이므로, 보여주는 잔액은 그것까지 뺀 값이어야 한다.
	public CancelCreditBalance balanceAsOf(Long userId, Instant now) {
		int max = holdProperties.cancelCreditMax();
		HoldCancelCredit credit = holdCancelCreditRepository.findByUserId(userId)
				.orElseGet(() -> new HoldCancelCredit(null, max, now));
		credit.refill(now, holdProperties.cancelCreditRefill(), max);
		credit.spend((int) holdRepository
				.findUnchargedNoShows(userId, now.minus(holdProperties.noShowGrace()))
				.stream()
				.map(Hold::getGroupId)
				.distinct()
				.count());
		return new CancelCreditBalance(
				credit.getCredits(),
				max,
				credit.nextRefillAt(holdProperties.cancelCreditRefill(), max));
	}

	public List<HoldCancelCreditEvent> findEventsOf(Long userId) {
		return holdCancelCreditEventRepository.findAllOfUser(userId);
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
