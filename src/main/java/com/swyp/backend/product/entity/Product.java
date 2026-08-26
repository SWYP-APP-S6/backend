package com.swyp.backend.product.entity;

import com.swyp.backend.common.BaseTimeEntity;
import com.swyp.backend.store.entity.Store;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
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
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "products")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "store_id", nullable = false)
	private Store store;

	@Column(nullable = false, length = 30)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ProductCategory category;

	@Column(name = "initial_qty", nullable = false)
	private int initialQty;

	@Column(name = "available_qty", nullable = false)
	private int availableQty;

	@Column(name = "held_qty", nullable = false)
	private int heldQty;

	@Column(name = "original_price", nullable = false)
	private int originalPrice;

	@Column(name = "sale_price", nullable = false)
	private int salePrice;

	@Column(name = "discount_rate", nullable = false)
	private short discountRate;

	@Column(name = "pickup_start_at", nullable = false)
	private LocalDateTime pickupStartAt;

	@Column(name = "pickup_end_at", nullable = false)
	private LocalDateTime pickupEndAt;

	@Column(name = "photo_url", nullable = false, length = 512)
	private String photoUrl;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ProductStatus status;

	@Column(name = "reconfirm_sent_at")
	private Instant reconfirmSentAt;

	@Column(name = "reconfirm_answered_at")
	private Instant reconfirmAnsweredAt;

	@ElementCollection
	@CollectionTable(
			name = "product_ingredients",
			joinColumns = @JoinColumn(name = "product_id"))
	@Column(name = "ingredient_id", nullable = false)
	private Set<Integer> ingredientIds = new LinkedHashSet<>();

	public Product(
			Store store,
			String name,
			ProductCategory category,
			int initialQty,
			int originalPrice,
			int salePrice,
			LocalDateTime pickupStartAt,
			LocalDateTime pickupEndAt,
			String photoUrl) {
		this.store = store;
		this.name = name;
		this.category = category;
		this.initialQty = initialQty;
		this.availableQty = initialQty;
		this.originalPrice = originalPrice;
		this.salePrice = salePrice;
		this.discountRate = discountRateOf(originalPrice, salePrice);
		this.pickupStartAt = pickupStartAt;
		this.pickupEndAt = pickupEndAt;
		this.photoUrl = photoUrl;
		this.status = ProductStatus.ON_SALE;
	}

	public Set<Integer> getIngredientIds() {
		return Collections.unmodifiableSet(ingredientIds);
	}

	public void replaceIngredientIds(Collection<Integer> ingredientIds) {
		this.ingredientIds.clear();
		this.ingredientIds.addAll(ingredientIds);
	}

	public void hold(int qty) {
		if (qty <= 0) {
			throw new IllegalArgumentException("hold qty must be positive");
		}
		if (this.status != ProductStatus.ON_SALE) {
			throw new IllegalStateException("product is not on sale");
		}
		if (qty > availableQty) {
			throw new IllegalStateException("hold qty exceeds available qty");
		}
		this.availableQty -= qty;
		this.heldQty += qty;
		syncStatusWithAvailableQty();
	}

	public void releaseHold(int qty) {
		if (qty <= 0) {
			throw new IllegalArgumentException("release qty must be positive");
		}
		this.availableQty += qty;
		syncStatusWithAvailableQty();
	}

	public void adjustAvailableQty(int availableQty) {
		if (availableQty < 0) {
			throw new IllegalArgumentException("available qty must not be negative");
		}
		this.availableQty = availableQty;
		syncStatusWithAvailableQty();
	}

	public void changePrice(int originalPrice, int salePrice) {
		this.originalPrice = originalPrice;
		this.salePrice = salePrice;
		this.discountRate = discountRateOf(originalPrice, salePrice);
	}

	public void changePickupWindow(LocalDateTime pickupStartAt, LocalDateTime pickupEndAt) {
		this.pickupStartAt = pickupStartAt;
		this.pickupEndAt = pickupEndAt;
	}

	public void changePhotoUrl(String photoUrl) {
		this.photoUrl = photoUrl;
	}

	public void close() {
		this.status = ProductStatus.CLOSED;
	}

	public void markReconfirmSent(Instant reconfirmSentAt) {
		this.reconfirmSentAt = reconfirmSentAt;
	}

	public void markReconfirmAnswered(Instant reconfirmAnsweredAt) {
		this.reconfirmAnsweredAt = reconfirmAnsweredAt;
	}

	private void syncStatusWithAvailableQty() {
		if (this.status == ProductStatus.CLOSED) {
			return;
		}
		this.status = availableQty == 0 ? ProductStatus.SOLD_OUT : ProductStatus.ON_SALE;
	}

	private static short discountRateOf(int originalPrice, int salePrice) {
		return (short) Math.round((originalPrice - salePrice) * 100.0 / originalPrice);
	}
}
