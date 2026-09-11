package com.swyp.backend.hold.function;

import com.swyp.backend.common.exception.BusinessException;
import com.swyp.backend.hold.dto.ActiveHoldQty;
import com.swyp.backend.hold.dto.HoldTarget;
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
import java.util.Optional;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
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

	public Long getProductIdOfUserHold(Long holdId, Long userId) {
		return holdRepository.findProductIdByIdAndUserId(holdId, userId)
				.orElseThrow(() -> new BusinessException(HoldErrorCode.HOLD_NOT_FOUND));
	}

	public Optional<Hold> findHoldingOf(Long userId, Long productId) {
		return holdRepository.findByUserIdAndProductIdAndStatus(
				userId, productId, HoldStatus.HOLDING);
	}

	public Optional<Long> findHoldingIdOf(Long userId, Long productId) {
		return holdRepository.findByUserIdAndProductIdAndStatus(userId, productId, HoldStatus.HOLDING)
				.map(Hold::getId);
	}

	public List<OverdueHold> findOverdue(Instant now) {
		return holdRepository.findOverdueByStatus(HoldStatus.HOLDING, now);
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

	public List<Hold> findHoldingOfStore(Long storeId) {
		return holdRepository.findStoreHoldsByStatus(storeId, HoldStatus.HOLDING);
	}

	public Page<Hold> findStoreHolds(Long storeId, OwnerHoldStatus filter, Pageable pageable) {
		return holdRepository.findStoreHolds(
				storeId,
				filter == null ? null : filter.status(),
				filter == null ? null : filter.canceledBy(),
				PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sortFor(filter)));
	}

	public Hold getDetailById(Long holdId) {
		return holdRepository.findDetailById(holdId)
				.orElseThrow(() -> new BusinessException(HoldErrorCode.HOLD_NOT_FOUND));
	}

	public HoldTarget getTargetById(Long holdId) {
		return holdRepository.findTargetById(holdId)
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

	private static Sort sortFor(OwnerHoldStatus filter) {
		Sort.Direction direction = filter == OwnerHoldStatus.HOLDING
				? Sort.Direction.ASC
				: Sort.Direction.DESC;
		return Sort.by(direction, "expiresAt").and(Sort.by(direction, "id"));
	}
}
