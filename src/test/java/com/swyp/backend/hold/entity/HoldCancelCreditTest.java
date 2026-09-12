package com.swyp.backend.hold.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class HoldCancelCreditTest {

	private static final Duration A_DAY = Duration.ofHours(24);

	private static final int MAX = 3;

	private static final Instant START = Instant.parse("2026-09-12T00:00:00Z");

	@Test
	void aDayWithoutCancelingGivesOneCreditBack() {
		HoldCancelCredit credit = new HoldCancelCredit(null, 0, START);

		credit.refill(START.plus(Duration.ofHours(25)), A_DAY, MAX);

		assertThat(credit.getCredits()).isEqualTo(1);
	}

	@Test
	void anHourShortOfADayGivesNothing() {
		HoldCancelCredit credit = new HoldCancelCredit(null, 0, START);

		credit.refill(START.plus(Duration.ofHours(23)), A_DAY, MAX);

		assertThat(credit.getCredits()).isZero();
	}

	@Test
	void theLeftoverHoursCarryIntoTheNextRefill() {
		HoldCancelCredit credit = new HoldCancelCredit(null, 0, START);

		credit.refill(START.plus(Duration.ofHours(25)), A_DAY, MAX);
		credit.refill(START.plus(Duration.ofHours(48)), A_DAY, MAX);

		assertThat(credit.getCredits())
			.as("counting from the moment of the refill rather than from now keeps a user from "
					+ "losing the hours that had already passed")
			.isEqualTo(2);
	}

	@Test
	void aYearAwayStillComesBackToThreeRatherThanThreeHundred() {
		HoldCancelCredit credit = new HoldCancelCredit(null, 0, START);

		credit.refill(START.plus(Duration.ofDays(365)), A_DAY, MAX);

		assertThat(credit.getCredits()).isEqualTo(MAX);
	}

	@Test
	void aFullAccountStartsItsNextDayFromNowNotFromTheOldMark() {
		HoldCancelCredit credit = new HoldCancelCredit(null, MAX, START);
		Instant muchLater = START.plus(Duration.ofDays(10));

		credit.refill(muchLater, A_DAY, MAX);
		credit.spend(3);
		credit.refill(muchLater.plus(Duration.ofHours(1)), A_DAY, MAX);

		assertThat(credit.getCredits())
			.as("time spent at the cap must not bank into instant refills once spending starts")
			.isZero();
		assertThat(credit.nextRefillAt(A_DAY, MAX)).isEqualTo(muchLater.plus(A_DAY));
	}

	@Test
	void aFullAccountHasNoNextRefill() {
		assertThat(new HoldCancelCredit(null, MAX, START).nextRefillAt(A_DAY, MAX)).isNull();
	}

	@Test
	void givingBackNeverExceedsTheCap() {
		HoldCancelCredit credit = new HoldCancelCredit(null, MAX, START);

		credit.giveBack(MAX);

		assertThat(credit.getCredits()).isEqualTo(MAX);
	}
}
