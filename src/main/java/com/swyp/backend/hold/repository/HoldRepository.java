package com.swyp.backend.hold.repository;

import com.swyp.backend.hold.dto.ActiveHoldQty;
import com.swyp.backend.hold.entity.Hold;
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

	List<Hold> findByUserIdAndStatusOrderByExpiresAtAsc(Long userId, HoldStatus status);

	Page<Hold> findByUserIdAndStatusNot(Long userId, HoldStatus status, Pageable pageable);

	List<Hold> findByStatusAndExpiresAtLessThanEqual(HoldStatus status, Instant expiresAt);

	@Query("""
			select h from Hold h
			join fetch h.user
			join fetch h.product p
			where p.store.id = :storeId and h.status = :status
			order by h.expiresAt asc
			""")
	List<Hold> findStoreHoldsByStatus(
			@Param("storeId") Long storeId, @Param("status") HoldStatus status);

	@Query("""
			select new com.swyp.backend.hold.dto.ActiveHoldQty(h.product.id, sum(h.qty))
			from Hold h
			where h.product.store.id = :storeId
				and h.status = com.swyp.backend.hold.entity.HoldStatus.HOLDING
			group by h.product.id
			""")
	List<ActiveHoldQty> findActiveHoldQtyByStoreId(@Param("storeId") Long storeId);
}
