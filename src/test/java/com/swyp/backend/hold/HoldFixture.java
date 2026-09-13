package com.swyp.backend.hold;

import com.swyp.backend.hold.entity.Hold;
import com.swyp.backend.product.entity.Product;
import com.swyp.backend.user.entity.User;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public final class HoldFixture {

	// 애플리케이션은 holds_group_id_seq 에서 묶음 키를 받는다. 픽스처가 만든 찜이 그 값과
	// 겹치면 서로 다른 방문이 한 묶음으로 읽히므로, 시퀀스가 닿지 않는 높은 값에서 센다.
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
