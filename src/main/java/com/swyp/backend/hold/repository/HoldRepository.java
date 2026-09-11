package com.swyp.backend.hold.repository;

import com.swyp.backend.hold.dto.ActiveHoldQty;
import com.swyp.backend.hold.dto.OverdueHold;
import com.swyp.backend.hold.entity.Hold;
import com.swyp.backend.hold.entity.HoldCanceledBy;
import com.swyp.backend.hold.entity.HoldStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HoldRepository extends JpaRepository<Hold, Long> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select h from Hold h where h.id = :id")
	Optional<Hold> findByIdForUpdate(@Param("id") Long id);

	boolean existsByUserIdAndProductIdAndStatus(Long userId, Long productId, HoldStatus status);

	@Query("select h from Hold h join fetch h.product p join fetch p.store "
			+ "where h.user.id = :userId and p.id = :productId and h.status = :status")
	Optional<Hold> findByUserIdAndProductIdAndStatus(
			@Param("userId") Long userId,
			@Param("productId") Long productId,
			@Param("status") HoldStatus status);

	@Query("select new com.swyp.backend.hold.dto.OverdueHold(h.id, h.product.id) from Hold h "
			+ "where h.status = :status and h.expiresAt <= :expiresAt order by h.product.id, h.id")
	List<OverdueHold> findOverdueByStatus(
			@Param("status") HoldStatus status, @Param("expiresAt") Instant expiresAt);

	List<Hold> findByUserIdAndStatusOrderByExpiresAtAsc(Long userId, HoldStatus status);

	Page<Hold> findByUserIdAndStatusNot(Long userId, HoldStatus status, Pageable pageable);

	List<Hold> findByStatusAndExpiresAtLessThanEqual(HoldStatus status, Instant expiresAt);

	List<Hold> findByProductIdAndStatus(Long productId, HoldStatus status);

	@Query("select coalesce(sum(h.qty), 0) from Hold h where h.product.id = :productId and h.status = :status")
	long sumQtyByProductIdAndStatus(@Param("productId") Long productId, @Param("status") HoldStatus status);

	@Query("""
			select count(h) from Hold h
			where h.product.store.id = :storeId
				and h.status = com.swyp.backend.hold.entity.HoldStatus.COMPLETED
				and h.completedAt >= :since
			""")
	long countCompletedSince(@Param("storeId") Long storeId, @Param("since") Instant since);

	@Query("""
			select count(h) from Hold h
			where h.product.store.id = :storeId
				and h.status = com.swyp.backend.hold.entity.HoldStatus.EXPIRED
				and h.expiresAt >= :since
			""")
	long countExpiredSince(@Param("storeId") Long storeId, @Param("since") Instant since);

	@Query("""
			select h from Hold h
			join fetch h.user
			join fetch h.product p
			where p.store.id = :storeId and h.status = :status
			order by h.expiresAt asc
			""")
	List<Hold> findStoreHoldsByStatus(
			@Param("storeId") Long storeId, @Param("status") HoldStatus status);

	@Query(value = """
			select h from Hold h
			join fetch h.user
			join fetch h.product p
			where p.store.id = :storeId
				and (:status is null or h.status = :status)
				and (:canceledBy is null or h.canceledBy = :canceledBy)
			""",
			countQuery = """
			select count(h) from Hold h
			where h.product.store.id = :storeId
				and (:status is null or h.status = :status)
				and (:canceledBy is null or h.canceledBy = :canceledBy)
			""")
	Page<Hold> findStoreHolds(
			@Param("storeId") Long storeId,
			@Param("status") HoldStatus status,
			@Param("canceledBy") HoldCanceledBy canceledBy,
			Pageable pageable);

	@Query("""
			select h from Hold h
			join fetch h.user
			join fetch h.product p
			join fetch p.store
			where h.id = :id
			""")
	Optional<Hold> findDetailById(@Param("id") Long id);

	@Query("""
			select new com.swyp.backend.hold.dto.ActiveHoldQty(h.product.id, sum(h.qty))
			from Hold h
			where h.product.store.id = :storeId
				and h.status = com.swyp.backend.hold.entity.HoldStatus.HOLDING
			group by h.product.id
			""")
	List<ActiveHoldQty> findActiveHoldQtyByStoreId(@Param("storeId") Long storeId);
}
