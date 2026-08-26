package com.swyp.backend.product.repository;

import com.swyp.backend.product.entity.Product;
import com.swyp.backend.product.entity.ProductStatus;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from Product p where p.id = :id")
	Optional<Product> findByIdForUpdate(@Param("id") Long id);

	@Query("""
			select p from Product p
			join fetch p.store s
			where p.status = com.swyp.backend.product.entity.ProductStatus.ON_SALE
				and p.availableQty >= 1
				and p.pickupEndAt > :now
				and s.status = com.swyp.backend.store.entity.StoreStatus.APPROVED
				and s.latitude between :minLatitude and :maxLatitude
				and s.longitude between :minLongitude and :maxLongitude
			""")
	List<Product> findSellableWithinBounds(
			@Param("now") LocalDateTime now,
			@Param("minLatitude") BigDecimal minLatitude,
			@Param("maxLatitude") BigDecimal maxLatitude,
			@Param("minLongitude") BigDecimal minLongitude,
			@Param("maxLongitude") BigDecimal maxLongitude);

	@Query("select p from Product p join fetch p.store where p.id = :id")
	Optional<Product> findWithStoreById(@Param("id") Long id);

	List<Product> findByStoreIdOrderByCreatedAtDesc(Long storeId);

	List<Product> findByStatusNotAndPickupEndAtLessThanEqual(
			ProductStatus status, LocalDateTime pickupEndAt);

	boolean existsByStoreIdAndReconfirmSentAtIsNotNullAndReconfirmAnsweredAtIsNull(Long storeId);
}
