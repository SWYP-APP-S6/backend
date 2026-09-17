package com.swyp.backend.product.repository;

import com.swyp.backend.product.dto.StoreProductSummary;
import com.swyp.backend.product.entity.Product;
import com.swyp.backend.product.entity.ProductCategory;
import com.swyp.backend.product.entity.ProductStatus;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {

	String WHERE_SELLABLE_AS_OF_NOW = """
			where p.status = com.swyp.backend.product.entity.ProductStatus.ON_SALE
			and p.availableQty >= 1
			and p.pickupEndAt > :now
			""";

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from Product p where p.id = :id")
	Optional<Product> findByIdForUpdate(@Param("id") Long id);

	@Query("""
			select p from Product p
			join fetch p.store s
			""" + WHERE_SELLABLE_AS_OF_NOW + """
			and s.status = com.swyp.backend.store.entity.StoreStatus.APPROVED
			and :today member of s.businessDays
			and s.latitude between :minLatitude and :maxLatitude
			and s.longitude between :minLongitude and :maxLongitude
			and (:category is null or p.category = :category)
			order by p.pickupEndAt asc, p.id asc
			""")
	List<Product> findSellableWithinBounds(
			@Param("now") LocalDateTime now,
			@Param("today") DayOfWeek today,
			@Param("category") ProductCategory category,
			@Param("minLatitude") BigDecimal minLatitude,
			@Param("maxLatitude") BigDecimal maxLatitude,
			@Param("minLongitude") BigDecimal minLongitude,
			@Param("maxLongitude") BigDecimal maxLongitude);

	@Query("select p from Product p join fetch p.store where p.id = :id")
	Optional<Product> findWithStoreById(@Param("id") Long id);

	@Query("""
			select p from Product p
			where p.store.id = :storeId
				and p.status <> com.swyp.backend.product.entity.ProductStatus.CLOSED
				and p.pickupEndAt > :now
			order by p.createdAt desc, p.id desc
			""")
	List<Product> findSellingByStoreId(
			@Param("storeId") Long storeId, @Param("now") LocalDateTime now);

	boolean existsByStoreId(Long storeId);

	@Query("""
			select new com.swyp.backend.product.dto.StoreProductSummary(p.store.id, count(p))
			from Product p
			""" + WHERE_SELLABLE_AS_OF_NOW + """
			and :today member of p.store.businessDays
			and p.store.id in :storeIds
			group by p.store.id
			""")
	List<StoreProductSummary> summarizeSellableByStoreIds(
			@Param("now") LocalDateTime now,
			@Param("today") DayOfWeek today,
			@Param("storeIds") Collection<Long> storeIds);

	@Query("""
			select p from Product p
			""" + WHERE_SELLABLE_AS_OF_NOW + """
			and p.store.id = :storeId
			order by p.pickupEndAt asc, p.id asc
			""")
	List<Product> findSellableByStoreId(
			@Param("storeId") Long storeId, @Param("now") LocalDateTime now);

	Optional<Product> findByIdAndStoreId(Long id, Long storeId);

	@Query("select p from Product p where p.store.id = :storeId")
	Page<Product> findStoreProducts(@Param("storeId") Long storeId, Pageable pageable);

	@Query("""
			select p from Product p
			where p.store.id = :storeId
				and p.availableQty = 0
			""")
	Page<Product> findStoreProductsSoldOut(@Param("storeId") Long storeId, Pageable pageable);

	@Query("""
			select p from Product p
			where p.store.id = :storeId
				and p.availableQty between 1 and :runningLowQty
				and p.status <> com.swyp.backend.product.entity.ProductStatus.CLOSED
			""")
	Page<Product> findStoreProductsRunningLow(
			@Param("storeId") Long storeId,
			@Param("runningLowQty") int runningLowQty,
			Pageable pageable);

	List<Product> findByStatusNotAndPickupEndAtLessThanEqual(
			ProductStatus status, LocalDateTime pickupEndAt);

	boolean existsByStoreIdAndReconfirmSentAtIsNotNullAndReconfirmAnsweredAtIsNull(Long storeId);

	@Modifying
	@Query("delete from Product p where p.store.id in (select s.id from Store s where s.owner.id = :ownerId)")
	int deleteAllOfStoreOwnedBy(@Param("ownerId") Long ownerId);
}
