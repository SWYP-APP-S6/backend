package com.swyp.backend.hold;

import com.swyp.backend.hold.entity.Hold;
import com.swyp.backend.product.entity.Product;
import com.swyp.backend.user.entity.User;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public final class HoldFixture {

	private static final AtomicLong GROUP_IDS = new AtomicLong(900_000_000L);

	private HoldFixture() {}

	public static Hold hold(User user, Product product, int qty, Instant expiresAt) {
		return new Hold(
				user, product.getStore(), product, qty, GROUP_IDS.incrementAndGet(), expiresAt);
	}

	public static List<Hold> group(User user, Instant expiresAt, Product first, int firstQty,
			Product second, int secondQty) {
		long groupId = GROUP_IDS.incrementAndGet();
		return List.of(
				new Hold(user, first.getStore(), first, firstQty, groupId, expiresAt),
				new Hold(user, second.getStore(), second, secondQty, groupId, expiresAt));
	}
}
