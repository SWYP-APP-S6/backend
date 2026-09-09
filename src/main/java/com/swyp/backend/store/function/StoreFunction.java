package com.swyp.backend.store.function;

import com.swyp.backend.common.exception.BusinessException;
import com.swyp.backend.store.entity.Store;
import com.swyp.backend.store.entity.StoreStatus;
import com.swyp.backend.store.exception.StoreErrorCode;
import com.swyp.backend.store.repository.StoreRepository;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StoreFunction {

	private final StoreRepository storeRepository;

	public boolean existsByOwnerId(Long ownerId) {
		return storeRepository.findByOwnerId(ownerId).isPresent();
	}

	public Store getByOwnerId(Long ownerId) {
		return storeRepository.findByOwnerId(ownerId)
				.orElseThrow(() -> new BusinessException(StoreErrorCode.STORE_NOT_REGISTERED));
	}

	public Store getById(Long storeId) {
		return storeRepository.findById(storeId)
				.orElseThrow(() -> new BusinessException(StoreErrorCode.STORE_NOT_FOUND));
	}

	public Page<Store> findAllByStatus(StoreStatus status, Pageable pageable) {
		return status == null
				? storeRepository.findAllBy(pageable)
				: storeRepository.findByStatus(status, pageable);
	}

	public List<Store> findApprovedWithinBounds(
			BigDecimal minLatitude,
			BigDecimal maxLatitude,
			BigDecimal minLongitude,
			BigDecimal maxLongitude) {
		return storeRepository.findByStatusAndLatitudeBetweenAndLongitudeBetween(
				StoreStatus.APPROVED, minLatitude, maxLatitude, minLongitude, maxLongitude);
	}

	public Store save(Store store) {
		return storeRepository.saveAndFlush(store);
	}
}
