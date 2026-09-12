package com.swyp.backend.hold.repository;

import com.swyp.backend.hold.dto.ActiveHoldQty;
import com.swyp.backend.hold.dto.HoldRef;
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

	@Query("""
			select h from Hold h
			join fetch h.store
			join fetch h.items i
			join fetch i.product
			where h.user.id = :userId and h.id = :holdId
			""")
	Optional<Hold> findDetailByUserIdAndHoldId(
			@Param("userId") Long userId, @Param("holdId") Long holdId);

	@Query("""
			select h from Hold h
			join fetch h.store
			join fetch h.items i
			join fetch i.product
			where h.user.id = :userId
				and h.status = com.swyp.backend.hold.entity.HoldStatus.HOLDING
				and h.expiresAt > :now
			""")
	Optional<Hold> findActiveDetailByUserId(@Param("userId") Long userId, @Param("now") Instant now);

	@Query("""
			select new com.swyp.backend.hold.dto.HoldRef(h.id, h.store.id, h.expiresAt)
			from Hold h
			where h.user.id = :userId
				and h.status = com.swyp.backend.hold.entity.HoldStatus.HOLDING
			""")
	Optional<HoldRef> findHoldingRefByUserId(@Param("userId") Long userId);

	@Query("""
			select i.product.id from HoldItem i
			where i.hold.id = :holdId and i.hold.user.id = :userId
			order by i.product.id
			""")
	List<Long> findProductIdsOfUserHold(
			@Param("userId") Long userId, @Param("holdId") Long holdId);

	@Query("select i.product.id from HoldItem i where i.hold.id = :holdId order by i.product.id")
	List<Long> findProductIdsOfHold(@Param("holdId") Long holdId);

	@Query("""
			select h from Hold h
			where h.user.id = :userId
				and h.status = com.swyp.backend.hold.entity.HoldStatus.EXPIRED
				and h.completedAt is null
				and h.noShowChargedAt is null
				and h.expiresAt < :decidedBefore
			""")
	List<Hold> findUnchargedNoShows(
			@Param("userId") Long userId, @Param("decidedBefore") Instant decidedBefore);

	@Query("select h.store.id from Hold h where h.id = :holdId")
	Optional<Long> findStoreIdById(@Param("holdId") Long holdId);

	@Query("""
			select i.hold.id from HoldItem i
			where i.hold.user.id = :userId
				and i.product.id = :productId
				and i.hold.status = com.swyp.backend.hold.entity.HoldStatus.HOLDING
			""")
	Optional<Long> findHoldingIdOfProduct(
			@Param("userId") Long userId, @Param("productId") Long productId);

	@Query("""
			select new com.swyp.backend.hold.dto.OverdueHold(h.id, i.product.id)
			from HoldItem i join i.hold h
			where h.status = :status and h.expiresAt <= :expiresAt
			order by h.id, i.product.id
			""")
	List<OverdueHold> findOverdueByStatus(
			@Param("status") HoldStatus status, @Param("expiresAt") Instant expiresAt);

	@Query("""
			select h from Hold h
			join fetch h.user
			join fetch h.items i
			join fetch i.product
			where i.product.id = :productId and h.status = :status
			""")
	List<Hold> findByItemProductIdAndStatus(
			@Param("productId") Long productId, @Param("status") HoldStatus status);

	@Query("""
			select distinct sibling.product.id from HoldItem i
			join i.hold h
			join h.items sibling
			where i.product.id = :productId
				and h.status = com.swyp.backend.hold.entity.HoldStatus.HOLDING
			order by sibling.product.id
			""")
	List<Long> findProductIdsOfActiveHoldsContaining(@Param("productId") Long productId);

	@Query("""
			select coalesce(sum(i.qty), 0) from HoldItem i
			where i.product.id = :productId and i.hold.status = :status
			""")
	long sumItemQtyByProductIdAndStatus(
			@Param("productId") Long productId, @Param("status") HoldStatus status);

	@Query("""
			select count(h) from Hold h
			where h.store.id = :storeId
				and h.status = com.swyp.backend.hold.entity.HoldStatus.COMPLETED
				and h.completedAt >= :since
			""")
	long countCompletedSince(@Param("storeId") Long storeId, @Param("since") Instant since);

	@Query("""
			select count(h) from Hold h
			where h.store.id = :storeId
				and h.status = com.swyp.backend.hold.entity.HoldStatus.EXPIRED
				and h.expiresAt >= :since
			""")
	long countExpiredSince(@Param("storeId") Long storeId, @Param("since") Instant since);

	@Query("""
			select distinct h from Hold h
			join fetch h.user
			join fetch h.items i
			join fetch i.product
			where h.store.id = :storeId and h.status = :status
			order by h.expiresAt asc
			""")
	List<Hold> findStoreHoldsByStatus(
			@Param("storeId") Long storeId, @Param("status") HoldStatus status);

	@Query(value = """
			select h from Hold h
			join fetch h.user
			where h.store.id = :storeId
				and (:status is null or h.status = :status)
				and (:canceledBy is null or h.canceledBy = :canceledBy)
			""",
			countQuery = """
			select count(h) from Hold h
			where h.store.id = :storeId
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
			join fetch h.store
			join fetch h.items i
			join fetch i.product
			where h.id = :id
			""")
	Optional<Hold> findDetailById(@Param("id") Long id);

	@Query("""
			select new com.swyp.backend.hold.dto.ActiveHoldQty(i.product.id, sum(i.qty))
			from HoldItem i join i.hold h
			where h.store.id = :storeId
				and h.status = com.swyp.backend.hold.entity.HoldStatus.HOLDING
			group by i.product.id
			""")
	List<ActiveHoldQty> findActiveHoldQtyByStoreId(@Param("storeId") Long storeId);

}
