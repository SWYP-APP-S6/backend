package com.swyp.backend.hold.function;

import com.swyp.backend.hold.entity.HoldCancelCredit;
import com.swyp.backend.hold.repository.HoldCancelCreditRepository;
import com.swyp.backend.user.entity.User;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class HoldCancelCreditFunction {

	private final HoldCancelCreditRepository holdCancelCreditRepository;

	public Optional<HoldCancelCredit> findOf(Long userId) {
		return holdCancelCreditRepository.findByUserId(userId);
	}

	public HoldCancelCredit getOrStart(User user, int startingCredits, Instant now) {
		return holdCancelCreditRepository.findByUserId(user.getId())
				.orElseGet(() -> holdCancelCreditRepository.save(
						new HoldCancelCredit(user, startingCredits, now)));
	}
}
