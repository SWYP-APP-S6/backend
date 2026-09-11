package com.swyp.backend.product.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class ProductTest {

	private static Product sellableProduct(int initialQty) {
		LocalDateTime now = LocalDateTime.now();
		return new Product(
				null, "복숭아 4입", ProductCategory.FRUIT, initialQty, 10_000, 4_000,
				now, now.plusHours(3), "https://cdn.example.com/peach.jpg");
	}

	@Test
	void holdMovesQuantityOutOfAvailableAndReleaseMovesItBack() {
		Product product = sellableProduct(5);

		product.hold(2);
		assertThat(product.getAvailableQty()).isEqualTo(3);
		assertThat(product.getHeldQty()).isEqualTo(2);

		product.releaseHold(2);
		assertThat(product.getAvailableQty()).isEqualTo(5);
		assertThat(product.getHeldQty())
			.as("a released hold stops being held -- otherwise the owner's home screen counts "
					+ "every hold the product ever had")
			.isZero();
	}

	@Test
	void repeatedHoldAndReleaseLeavesTheCountsWhereTheyStarted() {
		Product product = sellableProduct(3);

		for (int i = 0; i < 10; i++) {
			product.hold(1);
			product.releaseHold(1);
		}

		assertThat(product.getAvailableQty()).isEqualTo(3);
		assertThat(product.getHeldQty()).isZero();
	}

	@Test
	void releasingMoreThanIsHeldIsRejected() {
		Product product = sellableProduct(5);
		product.hold(1);

		assertThatThrownBy(() -> product.releaseHold(2))
			.as("held_qty carries a check constraint, so letting this through would surface as a "
					+ "constraint violation at flush time instead of here")
			.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void sellingOutAndReleasingMovesTheStatusBothWays() {
		Product product = sellableProduct(1);

		product.hold(1);
		assertThat(product.getStatus()).isEqualTo(ProductStatus.SOLD_OUT);

		product.releaseHold(1);
		assertThat(product.getStatus()).isEqualTo(ProductStatus.ON_SALE);
	}
}
