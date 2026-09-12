package com.swyp.backend.hold.entity;

import com.swyp.backend.common.BaseTimeEntity;
import com.swyp.backend.product.entity.Product;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "hold_items")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HoldItem extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "hold_id", nullable = false)
	private Hold hold;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "product_id", nullable = false)
	private Product product;

	@Column(nullable = false)
	private int qty;

	HoldItem(Hold hold, Product product, int qty) {
		requirePositive(qty);
		this.hold = hold;
		this.product = product;
		this.qty = qty;
	}

	void addQty(int qty) {
		requirePositive(qty);
		this.qty += qty;
	}

	private static void requirePositive(int qty) {
		if (qty <= 0) {
			throw new IllegalArgumentException("hold item qty must be positive");
		}
	}
}
