package com.swyp.backend.hold.entity;

import com.swyp.backend.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

@Getter
@Entity
@Table(name = "hold_cancel_credit_events")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HoldCancelCreditEvent {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "hold_id")
	private Hold hold;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private HoldCancelCreditReason reason;

	@Column(nullable = false)
	private short delta;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	public HoldCancelCreditEvent(
			User user,
			@Nullable Hold hold,
			HoldCancelCreditReason reason,
			int delta,
			Instant createdAt) {
		if (delta == 0) {
			throw new IllegalArgumentException("credit event must move the balance");
		}
		this.user = user;
		this.hold = hold;
		this.reason = reason;
		this.delta = (short) delta;
		this.createdAt = createdAt;
	}
}
