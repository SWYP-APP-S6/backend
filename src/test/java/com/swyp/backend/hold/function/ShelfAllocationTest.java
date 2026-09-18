package com.swyp.backend.hold.function;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ShelfAllocationTest {

	@Test
	void servesEveryone_whenTheShelfCoversEveryHold() {
		assertThat(ShelfAllocation.unserved(8, new int[] {1, 1, 1, 2, 3}))
			.containsExactly(false, false, false, false, false);
	}

	@Test
	void sellsTheWholeShelf_whereServingInHeldOrderWouldLeaveSomeUnsold() {
		assertThat(ShelfAllocation.unserved(5, new int[] {2, 2, 3}))
			.containsExactly(false, true, false);
	}

	@Test
	void skipsAHoldThatDoesNotFit_andKeepsServingTheOnesAfterIt() {
		assertThat(ShelfAllocation.unserved(4, new int[] {1, 1, 1, 2, 3}))
			.containsExactly(false, false, true, false, true);
	}

	@Test
	void prefersWhoeverHeldFirst_amongWaysToSellTheSameAmount() {
		assertThat(ShelfAllocation.unserved(3, new int[] {1, 1, 1, 1}))
			.containsExactly(false, false, false, true);
	}

	@Test
	void sellsMoreUnits_evenWhenThatServesFewerCustomers() {
		assertThat(ShelfAllocation.unserved(3, new int[] {1, 3, 1}))
			.containsExactly(true, false, true);
	}

	@Test
	void servesNobody_fromAnEmptyShelf() {
		assertThat(ShelfAllocation.unserved(0, new int[] {1, 2}))
			.containsExactly(true, true);
	}

	@Test
	void servesNobody_whenEveryHoldIsBiggerThanTheShelf() {
		assertThat(ShelfAllocation.unserved(1, new int[] {2, 3}))
			.containsExactly(true, true);
	}

	@Test
	void hasNothingToServe_withoutHolds() {
		assertThat(ShelfAllocation.unserved(5, new int[] {})).isEmpty();
	}
}
