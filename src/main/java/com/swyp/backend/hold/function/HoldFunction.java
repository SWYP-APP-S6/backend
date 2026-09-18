package com.swyp.backend.hold.function;

import com.swyp.backend.common.exception.BusinessException;
import com.swyp.backend.hold.dto.ActiveHoldQty;
import com.swyp.backend.hold.dto.HoldRef;
import com.swyp.backend.hold.dto.HoldStatusCount;
import com.swyp.backend.hold.dto.OverdueHold;
import com.swyp.backend.hold.dto.OwnerHoldFilter;
import com.swyp.backend.hold.dto.OwnerHoldStatus;
import com.swyp.backend.hold.dto.ProductHoldId;
import com.swyp.backend.hold.entity.Hold;
import com.swyp.backend.hold.entity.HoldStatus;
import com.swyp.backend.hold.exception.HoldErrorCode;
import com.swyp.backend.hold.repository.HoldRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class HoldFunction {

	private static final List<HoldStatus> PICKUPABLE =
			List.of(HoldStatus.HOLDING, HoldStatus.EXPIRED);

	private final HoldRepository holdRepository;
	private final Clock clock;

	public Hold getByIdForUpdate(Long holdId) {
		return holdRepository.findByIdForUpdate(holdId)
				.orElseThrow(() -> new BusinessException(HoldErrorCode.HOLD_NOT_FOUND));
	}

	public Hold getDetailOfUserHold(Long userId, Long holdId) {
		return holdRepository.findDetailByUserIdAndHoldId(userId, holdId)
				.orElseThrow(() -> new BusinessException(HoldErrorCode.HOLD_NOT_FOUND));
	}

	public List<Hold> findActiveGroupOf(Long userId, Instant now) {
		List<Hold> active = holdRepository.findActiveDetailsByUserId(userId, now);
		if (active.isEmpty()) {
			return active;
		}
		Long groupId = active.getFirst().getGroupId();
		return active.stream().filter(hold -> hold.getGroupId().equals(groupId)).toList();
	}

	public Optional<HoldRef> findHoldingRefOf(Long userId) {
		return holdRepository.findHoldingRefsByUserId(userId).stream().findFirst();
	}

	public Long getProductIdOfUserHold(Long userId, Long holdId) {
		return holdRepository.findProductIdOfUserHold(userId, holdId)
				.orElseThrow(() -> new BusinessException(HoldErrorCode.HOLD_NOT_FOUND));
	}

	public List<Long> findProductIdsOfGroup(Long groupId) {
		return holdRepository.findProductIdsOfGroup(groupId);
	}

	public List<Hold> findHoldingOfGroup(Long groupId) {
		return holdRepository.findGroupByStatus(groupId, HoldStatus.HOLDING);
	}

	public List<Long> findHoldingIdsOfGroup(Long groupId) {
		return holdRepository.findHoldingIdsOfGroup(groupId);
	}

	public List<Long> findProductIdsOfHolds(List<Long> holdIds) {
		return holdIds.isEmpty() ? List.of() : holdRepository.findProductIdsOfHolds(holdIds);
	}

	public Map<Long, Long> getProductIdByHold(List<Long> holdIds) {
		Map<Long, Long> productIdByHold = holdRepository.findProductHoldIdsByHoldIds(holdIds).stream()
				.collect(Collectors.toMap(ProductHoldId::holdId, ProductHoldId::productId));
		if (productIdByHold.size() != holdIds.stream().distinct().count()) {
			throw new BusinessException(HoldErrorCode.HOLD_NOT_FOUND);
		}
		return productIdByHold;
	}

	public long nextGroupId() {
		return holdRepository.nextGroupId();
	}

	public List<Long> getPickupableHoldIdsOfGroup(Long holdId) {
		List<Long> ids = holdRepository.findGroupHoldIdsOfHold(holdId, PICKUPABLE);
		if (ids.isEmpty()) {
			throw new BusinessException(HoldErrorCode.HOLD_ALREADY_RESOLVED);
		}
		return ids;
	}

	public List<Long> getPickupableProductIdsOfGroup(Long holdId) {
		return holdRepository.findGroupProductIdsOfHold(holdId, PICKUPABLE);
	}

	public Long getStoreIdOfHold(Long holdId) {
		return holdRepository.findStoreIdById(holdId)
				.orElseThrow(() -> new BusinessException(HoldErrorCode.HOLD_NOT_FOUND));
	}

	public List<Long> findStoreIdsOfHolds(List<Long> holdIds) {
		return holdIds.isEmpty() ? List.of() : holdRepository.findStoreIdsOfHolds(holdIds);
	}

	public Optional<Long> findHoldingIdOf(Long userId, Long productId) {
		return holdRepository.findHoldingIdOfProduct(userId, productId);
	}

	public List<OverdueHold> findOverdue(Instant now) {
		return holdRepository.findOverdueByStatus(HoldStatus.HOLDING, now);
	}

	public List<Hold> findExpiringSoon(Instant now, Duration reminderLead) {
		return holdRepository.findExpiringSoon(HoldStatus.HOLDING, now, now.plus(reminderLead));
	}

	public boolean markExpiryReminded(Long holdId, Instant now) {
		return holdRepository.markExpiryReminded(holdId, now) == 1;
	}

	public List<Hold> findUnchargedNoShows(Long userId, Instant decidedBefore) {
		return holdRepository.findUnchargedNoShows(userId, decidedBefore);
	}

	public void flush() {
		holdRepository.flush();
	}

	public Hold save(Hold hold) {
		return holdRepository.save(hold);
	}

	public Map<Long, Long> activeQtyByProductOfStore(Long storeId) {
		return holdRepository.findActiveHoldQtyByStoreId(storeId).stream()
				.collect(Collectors.toMap(ActiveHoldQty::productId, ActiveHoldQty::qty));
	}

	public List<Hold> findActiveHoldsOfProduct(Long productId) {
		return holdRepository.findByProductIdAndStatus(productId, HoldStatus.HOLDING);
	}

	public List<Hold> findHoldingOfProducts(List<Long> productIds) {
		return productIds.isEmpty() ? List.of() : holdRepository.findHoldingWithUserOfProducts(productIds);
	}

	public Map<Long, Integer> heldOrderOfProducts(List<Long> productIds) {
		if (productIds.isEmpty()) {
			return Map.of();
		}
		Map<Long, Integer> placedSoFar = new HashMap<>();
		Map<Long, Integer> orderByHold = new HashMap<>();
		for (ProductHoldId held : holdRepository.findProductHoldIdsInHeldOrder(productIds)) {
			orderByHold.put(held.holdId(), placedSoFar.merge(held.productId(), 1, Integer::sum));
		}
		return orderByHold;
	}

	public List<Hold> findHoldingOfStore(Long storeId) {
		return holdRepository.findStoreHoldsByStatus(storeId, HoldStatus.HOLDING);
	}

	public Map<OwnerHoldStatus, Long> countStoreHoldsByOwnerStatus(Long storeId) {
		return holdRepository.countStoreHoldsByStatus(storeId).stream()
				.collect(Collectors.toMap(
						count -> OwnerHoldStatus.of(count.status(), count.canceledBy()),
						HoldStatusCount::count,
						Long::sum));
	}

	public Page<Hold> findStoreHolds(Long storeId, OwnerHoldFilter filter, Pageable pageable) {
		return holdRepository.findStoreHolds(
				storeId,
				filter == null ? null : filter.status(),
				filter == null ? null : filter.canceledBy(),
				PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sortFor(filter)));
	}

	public Page<Hold> findUserHolds(Long userId, Pageable pageable) {
		Page<Long> ids = holdRepository.findUserHoldIds(
				userId,
				PageRequest.of(
						pageable.getPageNumber(),
						pageable.getPageSize(),
						Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"))));
		if (ids.isEmpty()) {
			return new PageImpl<>(List.of(), ids.getPageable(), ids.getTotalElements());
		}
		Map<Long, Hold> byId = holdRepository.findDetailsByIds(ids.getContent()).stream()
				.collect(Collectors.toMap(Hold::getId, hold -> hold));
		List<Hold> ordered = ids.getContent().stream().map(byId::get).toList();
		return new PageImpl<>(ordered, ids.getPageable(), ids.getTotalElements());
	}

	public Hold getDetailById(Long holdId) {
		return holdRepository.findDetailById(holdId)
				.orElseThrow(() -> new BusinessException(HoldErrorCode.HOLD_NOT_FOUND));
	}

	public long countCompletedTodayOfStore(Long storeId) {
		return holdRepository.countCompletedSince(storeId, startOfToday());
	}

	public long countExpiredTodayOfStore(Long storeId) {
		return holdRepository.countExpiredSince(storeId, startOfToday());
	}

	public long completedQtyOfProduct(Long productId) {
		return holdRepository.sumQtyByProductIdAndStatus(productId, HoldStatus.COMPLETED);
	}

	private Instant startOfToday() {
		return LocalDate.now(clock).atStartOfDay(clock.getZone()).toInstant();
	}

	private static Sort sortFor(OwnerHoldFilter filter) {
		Sort.Direction direction = filter == OwnerHoldFilter.HOLDING
				? Sort.Direction.ASC
				: Sort.Direction.DESC;
		return Sort.by(direction, "expiresAt").and(Sort.by(direction, "id"));
	}

	public boolean hasHoldingOf(Long userId) {
		return holdRepository.existsByUserIdAndStatus(userId, HoldStatus.HOLDING);
	}

	public boolean hasHoldingAtStoreOwnedBy(Long ownerId) {
		return holdRepository.existsByStoreOwnerIdAndStatus(ownerId, HoldStatus.HOLDING);
	}

	public void deleteAllInvolving(Long userId) {
		holdRepository.deleteAllInvolving(userId);
	}
}
