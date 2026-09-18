package com.swyp.backend.hold.function;

public final class ShelfAllocation {

	private ShelfAllocation() {
	}

	public static boolean[] unserved(int stockQty, int[] qtyInHeldOrder) {
		int count = qtyInHeldOrder.length;
		boolean[] unserved = new boolean[count];
		int heldTotal = 0;
		for (int qty : qtyInHeldOrder) {
			heldTotal += qty;
		}
		if (heldTotal <= stockQty) {
			return unserved;
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
				unserved[i] = true;
			}
		}
		return unserved;
	}
}
