package com.swyp.backend.hold.function;

import com.swyp.backend.common.exception.BusinessException;
import com.swyp.backend.hold.dto.ActiveHoldQty;
import com.swyp.backend.hold.dto.HoldRef;
import com.swyp.backend.hold.dto.HoldStatusCount;
import com.swyp.backend.hold.dto.OverdueHold;
import com.swyp.backend.hold.dto.OwnerHoldStatus;
import com.swyp.backend.hold.entity.Hold;
import com.swyp.backend.hold.entity.HoldStatus;
import com.swyp.backend.hold.exception.HoldErrorCode;
import com.swyp.backend.hold.repository.HoldRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
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

	public Optional<Hold> findActiveOf(Long userId, Instant now) {
		return holdRepository.findActiveDetailByUserId(userId, now);
	}

	public Optional<HoldRef> findHoldingRefOf(Long userId) {
		return holdRepository.findHoldingRefByUserId(userId);
	}

	public List<Long> getProductIdsOfUserHold(Long userId, Long holdId) {
		List<Long> productIds = holdRepository.findProductIdsOfUserHold(userId, holdId);
		if (productIds.isEmpty()) {
			throw new BusinessException(HoldErrorCode.HOLD_NOT_FOUND);
		}
		return productIds;
	}

	public List<Long> getProductIdsOfHold(Long holdId) {
		List<Long> productIds = holdRepository.findProductIdsOfHold(holdId);
		if (productIds.isEmpty()) {
			throw new BusinessException(HoldErrorCode.HOLD_NOT_FOUND);
		}
		return productIds;
	}

	public Long getStoreIdOfHold(Long holdId) {
		return holdRepository.findStoreIdById(holdId)
				.orElseThrow(() -> new BusinessException(HoldErrorCode.HOLD_NOT_FOUND));
	}

	public Optional<Long> findHoldingIdOf(Long userId, Long productId) {
		return holdRepository.findHoldingIdOfProduct(userId, productId);
	}

	public List<OverdueHold> findOverdue(Instant now) {
		return holdRepository.findOverdueByStatus(HoldStatus.HOLDING, now);
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

	public List<Long> findProductIdsSharingActiveHoldsWith(Long productId) {
		return holdRepository.findProductIdsOfActiveHoldsContaining(productId);
	}

	public List<Hold> findActiveHoldsOfProduct(Long productId) {
		return holdRepository.findByItemProductIdAndStatus(productId, HoldStatus.HOLDING);
	}

	public List<Hold> findHoldingOfStore(Long storeId) {
		return holdRepository.findStoreHoldsByStatus(storeId, HoldStatus.HOLDING);
	}

	public Map<OwnerHoldStatus, Long> countStoreHoldsByStatus(Long storeId) {
		return holdRepository.countStoreHoldsByStatus(storeId).stream()
				.collect(Collectors.toMap(
						count -> OwnerHoldStatus.of(count.status(), count.canceledBy()),
						HoldStatusCount::count,
						Long::sum));
	}

	public Page<Hold> findStoreHolds(Long storeId, OwnerHoldStatus filter, Pageable pageable) {
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
		return holdRepository.sumItemQtyByProductIdAndStatus(productId, HoldStatus.COMPLETED);
	}

	private Instant startOfToday() {
		return LocalDate.now(clock).atStartOfDay(clock.getZone()).toInstant();
	}

	private static Sort sortFor(OwnerHoldStatus filter) {
		Sort.Direction direction = filter == OwnerHoldStatus.HOLDING
				? Sort.Direction.ASC
				: Sort.Direction.DESC;
		return Sort.by(direction, "expiresAt").and(Sort.by(direction, "id"));
	}
}
