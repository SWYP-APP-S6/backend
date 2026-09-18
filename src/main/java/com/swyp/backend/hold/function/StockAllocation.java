package com.swyp.backend.hold.function;

public final class StockAllocation {

	private StockAllocation() {
	}

	public static boolean[] doesNotFit(int stockQty, int[] qtyInHeldOrder) {
		int count = qtyInHeldOrder.length;
		boolean[] doesNotFit = new boolean[count];
		int heldTotal = 0;
		for (int qty : qtyInHeldOrder) {
			heldTotal += qty;
		}
		if (heldTotal <= stockQty) {
			return doesNotFit;
		}

		int capacity = Math.max(stockQty, 0);
		boolean[][] reachableFrom = new boolean[count + 1][capacity + 1];
		reachableFrom[count][0] = true;
		for (int i = count - 1; i >= 0; i--) {
			int qty = qtyInHeldOrder[i];
			for (int sum = 0; sum <= capacity; sum++) {
				reachableFrom[i][sum] = reachableFrom[i + 1][sum]
						|| (qty <= sum && reachableFrom[i + 1][sum - qty]);
			}
		}

		int remaining = capacity;
		while (!reachableFrom[0][remaining]) {
			remaining--;
		}
		for (int i = 0; i < count; i++) {
			int qty = qtyInHeldOrder[i];
			if (qty <= remaining && reachableFrom[i + 1][remaining - qty]) {
				remaining -= qty;
			} else {
				doesNotFit[i] = true;
			}
		}
		return doesNotFit;
	}
}
