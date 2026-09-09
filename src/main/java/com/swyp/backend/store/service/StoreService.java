package com.swyp.backend.store.service;

import com.swyp.backend.common.exception.BusinessException;
import com.swyp.backend.common.response.PageResponse;
import com.swyp.backend.store.dto.StoreDetailResponse;
import com.swyp.backend.store.dto.StoreRegisterRequest;
import com.swyp.backend.store.dto.StoreSummaryResponse;
import com.swyp.backend.store.entity.Store;
import com.swyp.backend.store.entity.StoreStatus;
import com.swyp.backend.store.exception.StoreErrorCode;
import com.swyp.backend.store.repository.StoreRepository;
import com.swyp.backend.user.entity.User;
import com.swyp.backend.user.entity.UserRole;
import com.swyp.backend.user.service.UserService;
import java.math.BigDecimal;
import java.util.List;
import org.hibernate.exception.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StoreService {

	private static final String OWNER_UNIQUE_CONSTRAINT = "uq_stores_owner";

	private final StoreRepository storeRepository;
	private final UserService userService;
	private final GeocodingClient geocodingClient;

	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public StoreDetailResponse registerStore(Long ownerId, StoreRegisterRequest request) {
		User owner = userService.validateAndGetUser(ownerId);
		if (owner.getRole() != UserRole.OWNER) {
			throw new BusinessException(StoreErrorCode.OWNER_ROLE_REQUIRED);
		}
		if (storeRepository.existsByOwnerId(ownerId)) {
			throw new BusinessException(StoreErrorCode.STORE_ALREADY_REGISTERED);
		}

		GeocodingClient.Coordinates coordinates = geocodingClient.geocode(request.address());
		Store store = new Store(
				owner,
				request.name(),
				request.postalCode(),
				request.address(),
				request.addressDetail(),
				request.phone(),
				coordinates.latitude(),
				coordinates.longitude(),
				request.businessOpenTime(),
				request.businessCloseTime());
		store.replaceCategories(request.categories());
		store.replaceBusinessDays(request.businessDays());
		store.submitApplication(request.businessRegistrationNumber(), request.applicationNote());
		try {
			storeRepository.saveAndFlush(store);
		} catch (DataIntegrityViolationException e) {
			if (isOwnerConflict(e)) {
				throw new BusinessException(StoreErrorCode.STORE_ALREADY_REGISTERED);
			}
			throw e;
		}
		return StoreDetailResponse.from(store);
	}

	private static boolean isOwnerConflict(DataIntegrityViolationException e) {
		return e.getCause() instanceof ConstraintViolationException violation
				&& OWNER_UNIQUE_CONSTRAINT.equalsIgnoreCase(violation.getConstraintName());
	}

	public StoreDetailResponse getMyStore(Long ownerId) {
		return StoreDetailResponse.from(validateAndGetStoreByOwnerId(ownerId));
	}

	public Store validateAndGetStoreByOwnerId(Long ownerId) {
		return storeRepository.findByOwnerId(ownerId)
				.orElseThrow(() -> new BusinessException(StoreErrorCode.STORE_NOT_REGISTERED));
	}

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
