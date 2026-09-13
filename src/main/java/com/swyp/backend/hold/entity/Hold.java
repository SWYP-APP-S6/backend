package com.swyp.backend.hold.entity;

import com.swyp.backend.common.BaseTimeEntity;
import com.swyp.backend.product.entity.Product;
import com.swyp.backend.store.entity.Store;
import com.swyp.backend.user.entity.User;
import jakarta.persistence.Column;
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "holds")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Hold extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "store_id", nullable = false)
	private Store store;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "product_id", nullable = false)
	private Product product;

	@Column(nullable = false)
	private int qty;

	@Column(name = "group_id", nullable = false)
	private Long groupId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private HoldStatus status;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "completed_at")
	private Instant completedAt;

	@Column(name = "canceled_at")
	private Instant canceledAt;

	@Enumerated(EnumType.STRING)
	@Column(name = "canceled_by", length = 20)
	private HoldCanceledBy canceledBy;

	@Column(name = "cancel_reason", length = 200)
	private String cancelReason;

	@Column(name = "no_show_charged_at")
	private Instant noShowChargedAt;

	@Column(name = "expiry_reminded_at")
	private Instant expiryRemindedAt;

	public Hold(User user, Store store, Product product, int qty, Long groupId, Instant expiresAt) {
		if (qty < 1) {
			throw new IllegalArgumentException("hold qty must be positive");
		}
		this.user = user;
		this.store = store;
		this.product = product;
		this.qty = qty;
		this.groupId = groupId;
		this.status = HoldStatus.HOLDING;
		this.expiresAt = expiresAt;
	}

	public void addQty(int more) {
		requireHolding();
		if (more < 1) {
			throw new IllegalArgumentException("added qty must be positive");
		}
		this.qty += more;
	}

	public void restrictExpiryTo(Instant limit) {
		requireHolding();
		if (limit.isBefore(expiresAt)) {
			this.expiresAt = limit;
		}
	}

	public boolean isOverdueAt(Instant at) {
		return status == HoldStatus.HOLDING && !expiresAt.isAfter(at);
	}

	public HoldStatus statusAt(Instant at) {
		return isOverdueAt(at) ? HoldStatus.EXPIRED : status;
	}

	public void complete(Instant completedAt) {
		if (status != HoldStatus.HOLDING && status != HoldStatus.EXPIRED) {
			throw new IllegalStateException("hold is no longer waiting for a pickup");
		}
		this.status = HoldStatus.COMPLETED;
		this.completedAt = completedAt;
	}

	public void markNoShowCharged(Instant chargedAt) {
		this.noShowChargedAt = chargedAt;
	}

	public boolean wasChargedAsNoShow() {
		return noShowChargedAt != null;
	}

	public void cancelByUser(Instant canceledAt) {
		requireHolding();
		this.status = HoldStatus.CANCELED;
		this.canceledAt = canceledAt;
		this.canceledBy = HoldCanceledBy.USER;
	}

	public void cancelByOwner(Instant canceledAt, String cancelReason) {
		requireHolding();
		this.status = HoldStatus.CANCELED;
		this.canceledAt = canceledAt;
		this.canceledBy = HoldCanceledBy.OWNER;
		this.cancelReason = cancelReason;
	}

	public void expire() {
		requireHolding();
		this.status = HoldStatus.EXPIRED;
	}

	private void requireHolding() {
		if (this.status != HoldStatus.HOLDING) {
			throw new IllegalStateException("hold is no longer in HOLDING state");
		}
	}
}
