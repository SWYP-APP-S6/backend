package com.swyp.backend.hold.function;

import com.swyp.backend.hold.dto.ActiveHoldQty;
import com.swyp.backend.hold.entity.Hold;
import com.swyp.backend.hold.entity.HoldStatus;
import com.swyp.backend.hold.repository.HoldRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class HoldFunction {

	private final HoldRepository holdRepository;

	public Map<Long, Long> activeQtyByProductOfStore(Long storeId) {
		return holdRepository.findActiveHoldQtyByStoreId(storeId).stream()
				.collect(Collectors.toMap(ActiveHoldQty::productId, ActiveHoldQty::qty));
	}

	public List<Hold> findActiveHoldsOfProduct(Long productId) {
		return holdRepository.findByProductIdAndStatus(productId, HoldStatus.HOLDING);
	}

	public long activeHoldCountOfStore(Long storeId) {
		return holdRepository.countByStoreIdAndStatus(storeId, HoldStatus.HOLDING);
	}

	public long completedQtyOfStore(Long storeId) {
		return holdRepository.sumQtyByStoreIdAndStatus(storeId, HoldStatus.COMPLETED);
	}

	public long completedQtyOfProduct(Long productId) {
		return holdRepository.sumQtyByProductIdAndStatus(productId, HoldStatus.COMPLETED);
	}
}
