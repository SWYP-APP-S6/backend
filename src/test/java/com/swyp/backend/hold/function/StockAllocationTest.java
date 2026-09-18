package com.swyp.backend.hold.function;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StockAllocationTest {

	@Test
	void servesEveryone_whenTheStockCoversEveryHold() {
		assertThat(StockAllocation.doesNotFit(8, new int[] {1, 1, 1, 2, 3}))
			.containsExactly(false, false, false, false, false);
	}

	@Test
	void sellsAllTheStock_whereServingInHeldOrderWouldLeaveSomeUnsold() {
		assertThat(StockAllocation.doesNotFit(5, new int[] {2, 2, 3}))
			.containsExactly(false, true, false);
	}

	@Test
	void skipsAHoldThatDoesNotFit_andKeepsServingTheOnesAfterIt() {
		assertThat(StockAllocation.doesNotFit(4, new int[] {1, 1, 1, 2, 3}))
			.containsExactly(false, false, true, false, true);
	}

	@Test
	void prefersWhoeverHeldFirst_amongWaysToSellTheSameAmount() {
		assertThat(StockAllocation.doesNotFit(3, new int[] {1, 1, 1, 1}))
			.containsExactly(false, false, false, true);
	}

	@Test
	void sellsMoreUnits_evenWhenThatServesFewerCustomers() {
		assertThat(StockAllocation.doesNotFit(3, new int[] {1, 3, 1}))
			.containsExactly(true, false, true);
	}

	@Test
	void servesNobody_whenNoStockIsLeft() {
		assertThat(StockAllocation.doesNotFit(0, new int[] {1, 2}))
			.containsExactly(true, true);
	}

	@Test
	void servesNobody_whenEveryHoldIsBiggerThanTheStock() {
		assertThat(StockAllocation.doesNotFit(1, new int[] {2, 3}))
			.containsExactly(true, true);
	}

	@Test
	void hasNothingToServe_withoutHolds() {
		assertThat(StockAllocation.doesNotFit(5, new int[] {})).isEmpty();
	}
}
