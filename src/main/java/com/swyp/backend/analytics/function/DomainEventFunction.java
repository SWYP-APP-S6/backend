package com.swyp.backend.analytics.function;

import com.swyp.backend.analytics.dto.DomainEventFilter;
import com.swyp.backend.analytics.entity.DomainEvent;
import com.swyp.backend.analytics.entity.DomainEventType;
import com.swyp.backend.analytics.repository.DomainEventRepository;
import com.swyp.backend.hold.entity.Hold;
import com.swyp.backend.product.entity.Product;
import com.swyp.backend.store.entity.Store;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DomainEventFunction {

	private static final LocalDate FAR_FUTURE = LocalDate.of(9999, 1, 1);

	private final DomainEventRepository domainEventRepository;
	private final Clock clock;

	public DomainEvent record(DomainEvent event) {
		return domainEventRepository.save(event);
	}

	public DomainEvent record(DomainEventType type, Hold hold, Map<String, Object> details) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("nickname", hold.getUser().getNickname());
		payload.put("storeName", hold.getStore().getName());
		payload.put("productName", hold.getProduct().getName());
		payload.put("qty", hold.getQty());
		payload.put("groupId", hold.getGroupId());
		payload.putAll(details);
		return record(DomainEvent.builder()
				.eventType(type)
				.userId(hold.getUser().getId())
				.storeId(hold.getStore().getId())
				.productId(hold.getProduct().getId())
				.holdId(hold.getId())
				.payload(payload)
				.build());
	}

	public DomainEvent record(
			DomainEventType type, Product product, @Nullable Long actorUserId,
			Map<String, Object> details) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("storeName", product.getStore().getName());
		payload.put("productName", product.getName());
		payload.putAll(details);
		return record(DomainEvent.builder()
				.eventType(type)
				.userId(actorUserId)
				.storeId(product.getStore().getId())
				.productId(product.getId())
				.payload(payload)
				.build());
	}

	public DomainEvent record(DomainEventType type, Store store, Map<String, Object> details) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("storeName", store.getName());
		payload.put("nickname", store.getOwner().getNickname());
		payload.putAll(details);
		return record(DomainEvent.builder()
				.eventType(type)
				.userId(store.getOwner().getId())
				.storeId(store.getId())
				.payload(payload)
				.build());
	}

	public Page<DomainEvent> findEvents(DomainEventFilter filter, Pageable pageable) {
		return domainEventRepository.findMatching(
				filter.type(),
				filter.userId(),
				filter.storeId(),
				filter.productId(),
				filter.holdId(),
				filter.from() == null ? Instant.EPOCH : startOfDay(filter.from()),
				filter.to() == null ? startOfDay(FAR_FUTURE) : startOfDay(filter.to().plusDays(1)),
				PageRequest.of(
						pageable.getPageNumber(),
						pageable.getPageSize(),
						Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"))));
	}

	public void deleteAllInvolving(Long userId) {
		domainEventRepository.deleteAllInvolving(userId);
	}

	private Instant startOfDay(LocalDate date) {
		return date.atStartOfDay(clock.getZone()).toInstant();
	}
}
