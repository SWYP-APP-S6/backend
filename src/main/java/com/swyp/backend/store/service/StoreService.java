package com.swyp.backend.store.service;

import com.swyp.backend.common.exception.BusinessException;
import com.swyp.backend.common.response.PageResponse;
import com.swyp.backend.store.dto.StoreDetailResponse;
import com.swyp.backend.store.dto.StoreSummaryResponse;
import com.swyp.backend.store.entity.Store;
import com.swyp.backend.store.entity.StoreStatus;
import com.swyp.backend.store.exception.StoreErrorCode;
import com.swyp.backend.store.repository.StoreRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StoreService {

	private final StoreRepository storeRepository;

	public PageResponse<StoreSummaryResponse> getStores(StoreStatus status, Pageable pageable) {
		Page<Store> stores = status == null
				? storeRepository.findAllBy(pageable)
				: storeRepository.findByStatus(status, pageable);

		List<StoreSummaryResponse> content =
				stores.getContent().stream().map(StoreSummaryResponse::from).toList();
		return PageResponse.of(content, stores);
	}

	public StoreDetailResponse getStore(Long storeId) {
		return StoreDetailResponse.from(validateAndGetStore(storeId));
	}

	@Transactional
	public void updateStatus(Long storeId, StoreStatus status) {
		Store store = validateAndGetStore(storeId);
		switch (status) {
			case APPROVED -> store.approve();
			case REJECTED -> store.reject();
			// 되돌리기는 승인 취소에 가까워 반려와 의미가 다르다. 필요해지면 별도로 설계한다.
			case PENDING -> throw new BusinessException(StoreErrorCode.CANNOT_REVERT_TO_PENDING);
		}
	}

	private Store validateAndGetStore(Long storeId) {
		return storeRepository.findById(storeId)
				.orElseThrow(() -> new BusinessException(StoreErrorCode.STORE_NOT_FOUND));
	}
}
