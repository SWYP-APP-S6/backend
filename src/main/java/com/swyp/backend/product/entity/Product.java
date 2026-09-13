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

	// 매장에 실제로 있는 총 수량. 찜된 몫까지 포함한다. availableQty 는 여기서 찜을 뺀 나머지고
	// 0 에서 잘리므로, 모자란 정도는 이 값으로만 알 수 있다.
	@Column(name = "stock_qty", nullable = false)
	private int stockQty;

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

	@Column(name = "stock_confirmed_at")
	private Instant stockConfirmedAt;

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
		this.stockQty = initialQty;
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
		this.heldQty += qty;
		syncAvailableWithStock();
	}

	public void releaseHold(int qty) {
		if (qty <= 0) {
			throw new IllegalArgumentException("release qty must be positive");
		}
		if (qty > heldQty) {
			throw new IllegalStateException("release qty exceeds held qty");
		}
		this.heldQty -= qty;
		syncAvailableWithStock();
	}

	public void takeFromAvailable(int qty) {
		if (qty <= 0) {
			throw new IllegalArgumentException("take qty must be positive");
		}
		if (qty > availableQty) {
			throw new IllegalStateException("take qty exceeds available qty");
		}
		this.stockQty -= qty;
		syncAvailableWithStock();
	}

	public void completeHold(int qty) {
		if (qty <= 0) {
			throw new IllegalArgumentException("complete qty must be positive");
		}
		if (qty > heldQty) {
			throw new IllegalStateException("complete qty exceeds held qty");
		}
		// 물건이 매장을 떠났다. 예약도 실제 재고도 함께 줄어든다.
		this.heldQty -= qty;
		this.stockQty -= qty;
		syncAvailableWithStock();
	}

	// 찜이 최초 등록의 이만큼에 닿으면 재고를 다시 묻는다. 남은 수량이 적을수록 한 건의
	// 오차가 손님 한 명의 헛걸음이 된다.
	private static final double RECONFIRM_THRESHOLD_RATIO = 0.6;

	public int reconfirmThresholdQty() {
		return (int) Math.ceil(initialQty * RECONFIRM_THRESHOLD_RATIO);
	}

	public boolean needsStockReconfirm() {
		return reconfirmSentAt == null && heldQty >= reconfirmThresholdQty();
	}

	public boolean isStockReconfirmPending() {
		return reconfirmSentAt != null && reconfirmAnsweredAt == null;
	}

	public boolean isStockLocked(LocalDateTime now) {
		return stockConfirmedAt != null && now.isBefore(pickupEndAt);
	}

	public boolean isStockEditableAt(LocalDateTime now) {
		return status != ProductStatus.CLOSED && !isStockLocked(now);
	}

	// 재확인을 묻기 전까지는 최초 등록의 60% 밑으로 내리지 못한다. 팔리지도 않은 상품이
	// 목록에서 사라지는 걸 막기 위해서다. 재확인이 시작된 뒤에는 실제 재고를 적어야 하므로
	// 0 까지 열린다.
	public int minAdjustableQty() {
		return reconfirmSentAt == null ? reconfirmThresholdQty() : 0;
	}

	public void confirmStock(Instant confirmedAt) {
		this.reconfirmAnsweredAt = confirmedAt;
		this.stockConfirmedAt = confirmedAt;
	}

	public void denyStockConfirmation(Instant answeredAt) {
		this.reconfirmAnsweredAt = answeredAt;
	}

	// 점주가 적는 수는 매장에 실제로 있는 총 수량이다. 그중 찜이 잡고 있는 몫을 빼야
	// 새 손님에게 보여줄 수량이 된다. 찜이 더 많으면 보여줄 것은 없다(오버셀, BR-015).
	public void restock(int stockQty) {
		if (stockQty < 0) {
			throw new IllegalArgumentException("stock qty must not be negative");
		}
		this.stockQty = stockQty;
		syncAvailableWithStock();
	}

	public int shortfallQty() {
		return Math.max(0, heldQty - stockQty);
	}

	private void syncAvailableWithStock() {
		adjustAvailableQty(Math.max(0, stockQty - heldQty));
	}

	// 없는 물건에 걸린 예약을 걷어낸다. 재고가 돌아오는 게 아니라 애초에 없었던 것이라
	// availableQty 는 건드리지 않는다.
	public void dropHeldQty(int qty) {
		if (qty <= 0) {
			throw new IllegalArgumentException("dropped qty must be positive");
		}
		if (qty > heldQty) {
			throw new IllegalStateException("dropped qty exceeds held qty");
		}
		this.heldQty -= qty;
		syncAvailableWithStock();
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
