package com.swyp.backend.hold.repository;

import com.swyp.backend.hold.dto.ActiveHoldQty;
import com.swyp.backend.hold.dto.HoldRef;
import com.swyp.backend.hold.dto.HoldStatusCount;
import com.swyp.backend.hold.dto.OverdueHold;
import com.swyp.backend.hold.dto.ProductHoldId;
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
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HoldRepository extends JpaRepository<Hold, Long> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select h from Hold h where h.id = :id")
	Optional<Hold> findByIdForUpdate(@Param("id") Long id);

	@Query("""
			select h from Hold h
			join fetch h.store
			join fetch h.product
			where h.user.id = :userId and h.id = :holdId
			""")
	Optional<Hold> findDetailByUserIdAndHoldId(
			@Param("userId") Long userId, @Param("holdId") Long holdId);

	@Query("""
			select h from Hold h
			join fetch h.store
			join fetch h.product
			where h.user.id = :userId
				and h.status = com.swyp.backend.hold.entity.HoldStatus.HOLDING
				and h.expiresAt > :now
			order by h.groupId, h.product.id
			""")
	List<Hold> findActiveDetailsByUserId(@Param("userId") Long userId, @Param("now") Instant now);

	@Query("""
			select distinct new com.swyp.backend.hold.dto.HoldRef(h.groupId, h.store.id, h.expiresAt)
			from Hold h
			where h.user.id = :userId
				and h.status = com.swyp.backend.hold.entity.HoldStatus.HOLDING
			order by h.groupId
			""")
	List<HoldRef> findHoldingRefsByUserId(@Param("userId") Long userId);

	@Query("select h.product.id from Hold h where h.id = :holdId and h.user.id = :userId")
	Optional<Long> findProductIdOfUserHold(
			@Param("userId") Long userId, @Param("holdId") Long holdId);

	@Query("""
			select h.product.id from Hold h
			where h.groupId = :groupId
				and h.status = com.swyp.backend.hold.entity.HoldStatus.HOLDING
			order by h.product.id
			""")
	List<Long> findProductIdsOfGroup(@Param("groupId") Long groupId);

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

	@Query(value = "select h.id from Hold h where h.user.id = :userId",
			countQuery = "select count(h) from Hold h where h.user.id = :userId")
	Page<Long> findUserHoldIds(@Param("userId") Long userId, Pageable pageable);

	@Query("""
			select h from Hold h
			join fetch h.store
			join fetch h.product
			where h.id in :ids
			""")
	List<Hold> findDetailsByIds(@Param("ids") List<Long> ids);

	@Query("select h.store.id from Hold h where h.id = :holdId")
	Optional<Long> findStoreIdById(@Param("holdId") Long holdId);

	@Query("""
			select h.id from Hold h
			where h.user.id = :userId
				and h.product.id = :productId
				and h.status = com.swyp.backend.hold.entity.HoldStatus.HOLDING
			""")
	Optional<Long> findHoldingIdOfProduct(
			@Param("userId") Long userId, @Param("productId") Long productId);

	@Query("""
			select new com.swyp.backend.hold.dto.OverdueHold(h.id, h.product.id)
			from Hold h
			where h.status = :status and h.expiresAt <= :expiresAt
			order by h.id
			""")
	List<OverdueHold> findOverdueByStatus(
			@Param("status") HoldStatus status, @Param("expiresAt") Instant expiresAt);

	@Modifying
	@Query("""
			update Hold h set h.expiryRemindedAt = :now
			where h.id = :id
				and h.status = com.swyp.backend.hold.entity.HoldStatus.HOLDING
				and h.expiryRemindedAt is null
			""")
	int markExpiryReminded(@Param("id") Long id, @Param("now") Instant now);

	@Query("""
			select h from Hold h
			join fetch h.user
			join fetch h.store
			where h.status = :status
				and h.expiryRemindedAt is null
				and h.expiresAt > :now
				and h.expiresAt <= :remindBy
			order by h.expiresAt
			""")
	List<Hold> findExpiringSoon(
			@Param("status") HoldStatus status,
			@Param("now") Instant now,
			@Param("remindBy") Instant remindBy);

	@Query("""
			select h from Hold h
			join fetch h.user
			join fetch h.product
			where h.product.id = :productId and h.status = :status
			order by h.createdAt asc, h.id asc
			""")
	List<Hold> findByProductIdAndStatus(
			@Param("productId") Long productId, @Param("status") HoldStatus status);

	@Query("""
			select h from Hold h
			join fetch h.user
			where h.product.id in :productIds
				and h.status = com.swyp.backend.hold.entity.HoldStatus.HOLDING
			order by h.createdAt asc, h.id asc
			""")
	List<Hold> findHoldingWithUserOfProducts(@Param("productIds") List<Long> productIds);

	@Query("""
			select new com.swyp.backend.hold.dto.ProductHoldId(h.product.id, h.id)
			from Hold h
			where h.product.id in :productIds
			order by h.createdAt asc, h.id asc
			""")
	List<ProductHoldId> findProductHoldIdsInHeldOrder(@Param("productIds") List<Long> productIds);

	@Query("""
			select new com.swyp.backend.hold.dto.ProductHoldId(h.product.id, h.id)
			from Hold h
			where h.id in :holdIds
			""")
	List<ProductHoldId> findProductHoldIdsByHoldIds(@Param("holdIds") List<Long> holdIds);

	@Query("""
			select distinct h.store.id from Hold h
			where h.id in :holdIds
			""")
	List<Long> findStoreIdsOfHolds(@Param("holdIds") List<Long> holdIds);

	@Query("""
			select coalesce(sum(h.qty), 0) from Hold h
			where h.product.id = :productId and h.status = :status
			""")
	long sumQtyByProductIdAndStatus(
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
			select h from Hold h
			join fetch h.user
			join fetch h.product
			where h.store.id = :storeId and h.status = :status
			order by h.expiresAt asc, h.id asc
			""")
	List<Hold> findStoreHoldsByStatus(
			@Param("storeId") Long storeId, @Param("status") HoldStatus status);

	@Query("""
			select new com.swyp.backend.hold.dto.HoldStatusCount(h.status, h.canceledBy, count(h))
			from Hold h
			where h.store.id = :storeId
			group by h.status, h.canceledBy
			""")
	List<HoldStatusCount> countStoreHoldsByStatus(@Param("storeId") Long storeId);

	@Query(value = """
			select h from Hold h
			join fetch h.user
			join fetch h.product
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
			join fetch h.product
			where h.id = :id
			""")
	Optional<Hold> findDetailById(@Param("id") Long id);

	@Query("""
			select h from Hold h
			join fetch h.user
			join fetch h.store
			join fetch h.product
			where h.groupId = :groupId and h.status = :status
			order by h.product.id
			""")
	List<Hold> findGroupByStatus(
			@Param("groupId") Long groupId, @Param("status") HoldStatus status);

	@Query(value = "select nextval('holds_group_id_seq')", nativeQuery = true)
	long nextGroupId();

	@Query("""
			select h.id from Hold h
			where h.groupId = :groupId
				and h.status = com.swyp.backend.hold.entity.HoldStatus.HOLDING
			order by h.id
			""")
	List<Long> findHoldingIdsOfGroup(@Param("groupId") Long groupId);

	@Query("""
			select distinct h.product.id from Hold h
			where h.id in :holdIds
			order by h.product.id
			""")
	List<Long> findProductIdsOfHolds(@Param("holdIds") List<Long> holdIds);

	@Query("""
			select h.id from Hold h
			where h.groupId = (select g.groupId from Hold g where g.id = :holdId)
				and h.status in :statuses
			order by h.id
			""")
	List<Long> findGroupHoldIdsOfHold(
			@Param("holdId") Long holdId, @Param("statuses") List<HoldStatus> statuses);

	@Query("""
			select h.product.id from Hold h
			where h.groupId = (select g.groupId from Hold g where g.id = :holdId)
				and h.status in :statuses
			order by h.product.id
			""")
	List<Long> findGroupProductIdsOfHold(
			@Param("holdId") Long holdId, @Param("statuses") List<HoldStatus> statuses);

	@Query("""
			select new com.swyp.backend.hold.dto.ActiveHoldQty(h.product.id, sum(h.qty))
			from Hold h
			where h.store.id = :storeId
				and h.status = com.swyp.backend.hold.entity.HoldStatus.HOLDING
			group by h.product.id
			""")
	List<ActiveHoldQty> findActiveHoldQtyByStoreId(@Param("storeId") Long storeId);

}
