package com.swyp.backend.hold;

import com.swyp.backend.hold.HoldFixture;
import com.swyp.backend.hold.entity.Hold;
import com.swyp.backend.product.entity.Product;
import com.swyp.backend.user.entity.User;
import java.time.Instant;

public final class HoldFixture {

	private HoldFixture() {}

	public static Hold hold(User user, Product product, int qty, Instant expiresAt) {
		Hold hold = new Hold(user, product.getStore(), expiresAt);
		hold.addItem(product, qty);
		return hold;
	}

	public static Hold hold(User user, Instant expiresAt, Product first, int firstQty,
			Product second, int secondQty) {
		Hold hold = hold(user, first, firstQty, expiresAt);
		hold.addItem(second, secondQty);
		return hold;
	}
}
