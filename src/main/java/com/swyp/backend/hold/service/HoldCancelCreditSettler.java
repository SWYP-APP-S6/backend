package com.swyp.backend.hold.service;

import com.swyp.backend.hold.HoldProperties;
import com.swyp.backend.hold.entity.Hold;
import com.swyp.backend.hold.entity.HoldCancelCredit;
import com.swyp.backend.hold.entity.HoldCancelCreditReason;
import com.swyp.backend.hold.function.HoldCancelCreditFunction;
import com.swyp.backend.hold.function.HoldFunction;
import com.swyp.backend.user.entity.User;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class HoldCancelCreditSettler {

	private final HoldFunction holdFunction;
	private final HoldCancelCreditFunction holdCancelCreditFunction;
	private final HoldProperties holdProperties;

	// 밀려 있던 노쇼를 여기서 정산한다. 찜 하나당 한 번 깎고 그 찜을 가리키는 이력을 남긴다 --
	// 묶어서 한 번에 깎으면 나중에 "어느 찜 때문이었나"를 되짚을 수 없다.
	public HoldCancelCredit settle(User user, Instant now) {
		HoldCancelCredit credit = holdCancelCreditFunction.getOrStart(
				user, holdProperties.cancelCreditMax(), now);
		int refilled = credit.refill(
				now, holdProperties.cancelCreditRefill(), holdProperties.cancelCreditMax());
		if (refilled > 0) {
			holdCancelCreditFunction.record(
					user, null, HoldCancelCreditReason.REFILL, refilled, now);
		}

		List<Hold> noShows = holdFunction.findUnchargedNoShows(
				user.getId(), now.minus(holdProperties.noShowGrace()));
		Set<Long> charged = new HashSet<>();
		for (Hold noShow : noShows) {
			// 잔액이 없어 못 깎아도 표시는 남긴다. 안 그러면 다음 정산에서 또 걸린다.
			noShow.markNoShowCharged(now);
			if (!charged.add(noShow.getGroupId())) {
				continue;
			}
			int spent = credit.spend(1);
			if (spent > 0) {
				holdCancelCreditFunction.record(
						user, noShow, HoldCancelCreditReason.NO_SHOW, -spent, now);
			}
		}
		return credit;
	}
}
