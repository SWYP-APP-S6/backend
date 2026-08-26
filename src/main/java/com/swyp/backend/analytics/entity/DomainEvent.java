package com.swyp.backend.analytics.entity;

import com.swyp.backend.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(name = "domain_events")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DomainEvent extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(name = "event_type", nullable = false, length = 40)
	private DomainEventType eventType;

	@Column(name = "user_id")
	private Long userId;

	@Column(name = "store_id")
	private Long storeId;

	@Column(name = "product_id")
	private Long productId;

	@Column(name = "hold_id")
	private Long holdId;

	@Column(name = "recipe_id")
	private Long recipeId;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, columnDefinition = "jsonb")
	private Map<String, Object> payload;

	@Builder
	private DomainEvent(
			DomainEventType eventType,
			Long userId,
			Long storeId,
			Long productId,
			Long holdId,
			Long recipeId,
			Map<String, Object> payload) {
		this.eventType = eventType;
		this.userId = userId;
		this.storeId = storeId;
		this.productId = productId;
		this.holdId = holdId;
		this.recipeId = recipeId;
		this.payload = payload == null ? new LinkedHashMap<>() : new LinkedHashMap<>(payload);
	}

	public Map<String, Object> getPayload() {
		return Collections.unmodifiableMap(payload);
	}
}
