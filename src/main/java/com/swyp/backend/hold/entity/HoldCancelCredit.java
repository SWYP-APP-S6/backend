package com.swyp.backend.hold.entity;

import com.swyp.backend.common.BaseTimeEntity;
import com.swyp.backend.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

@Getter
@Entity
@Table(name = "hold_cancel_credits")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HoldCancelCredit extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Column(nullable = false)
	private int credits;

	@Column(name = "refilled_at", nullable = false)
	private Instant refilledAt;

	public HoldCancelCredit(User user, int credits, Instant refilledAt) {
		this.user = user;
		this.credits = credits;
		this.refilledAt = refilledAt;
	}

	// 세 메서드가 **실제로 움직인 양**을 돌려준다. 상한과 0 에서 잘리므로 부탁한 양과 다를 수
	// 있고, 이력에는 잘린 뒤의 값이 적혀야 한다.
	public int refill(Instant now, Duration interval, int max) {
		int before = credits;
		long earned = Duration.between(refilledAt, now).dividedBy(interval);
		if (earned > 0) {
			credits = (int) Math.min(max, credits + earned);
			refilledAt = refilledAt.plus(interval.multipliedBy(earned));
		}
		if (credits >= max) {
			refilledAt = now;
		}
		return credits - before;
	}

	public int spend(int count) {
		int before = credits;
		credits = Math.max(0, credits - count);
		return before - credits;
	}

	public int giveBack(int max) {
		int before = credits;
		credits = Math.min(max, credits + 1);
		return credits - before;
	}

	public int adjust(int delta, int max) {
		int before = credits;
		credits = Math.max(0, Math.min(max, credits + delta));
		return credits - before;
	}

	public boolean isEmpty() {
		return credits == 0;
	}

	@Nullable
	public Instant nextRefillAt(Duration interval, int max) {
		return credits >= max ? null : refilledAt.plus(interval);
	}
}
