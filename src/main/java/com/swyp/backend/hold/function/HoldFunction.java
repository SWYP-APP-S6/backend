package com.swyp.backend.hold.function;

import com.swyp.backend.common.exception.BusinessException;
import com.swyp.backend.hold.dto.ActiveHoldQty;
import com.swyp.backend.hold.dto.OverdueHold;
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
}
