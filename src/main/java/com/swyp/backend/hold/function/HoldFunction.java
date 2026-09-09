package com.swyp.backend.hold.function;

import com.swyp.backend.hold.dto.ActiveHoldQty;
import com.swyp.backend.hold.entity.Hold;
import com.swyp.backend.hold.entity.HoldStatus;
import com.swyp.backend.hold.repository.HoldRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class HoldFunction {

	private final HoldRepository holdRepository;

	public List<ActiveHoldQty> findActiveQtyByStoreId(Long storeId) {
		return holdRepository.findActiveHoldQtyByStoreId(storeId);
	}

	public List<Hold> findByProductIdAndStatus(Long productId, HoldStatus status) {
		return holdRepository.findByProductIdAndStatus(productId, status);
	}

	public long sumQtyByStoreIdAndStatus(Long storeId, HoldStatus status) {
		return holdRepository.sumQtyByStoreIdAndStatus(storeId, status);
	}

	public long sumQtyByProductIdAndStatus(Long productId, HoldStatus status) {
		return holdRepository.sumQtyByProductIdAndStatus(productId, status);
	}

	public long countByStoreIdAndStatus(Long storeId, HoldStatus status) {
		return holdRepository.countByStoreIdAndStatus(storeId, status);
	}
}
