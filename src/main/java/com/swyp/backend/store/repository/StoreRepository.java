package com.swyp.backend.store.repository;

import com.swyp.backend.store.entity.Store;
import com.swyp.backend.store.entity.StoreStatus;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StoreRepository extends JpaRepository<Store, Long> {

	Optional<Store> findByOwnerId(Long ownerId);

	boolean existsByOwnerId(Long ownerId);

	List<Store> findByOwnerIdIn(Collection<Long> ownerIds);

	List<Store> findByStatusAndLatitudeBetweenAndLongitudeBetween(
			StoreStatus status,
			BigDecimal minLatitude,
			BigDecimal maxLatitude,
			BigDecimal minLongitude,
			BigDecimal maxLongitude);

	// 목록에서 점주를 함께 보여주므로 미리 당겨온다 — 없으면 행마다 조회가 한 번씩 더 나간다.
	@EntityGraph(attributePaths = "owner")
	Page<Store> findAllBy(Pageable pageable);

	@EntityGraph(attributePaths = "owner")
	Page<Store> findByStatus(StoreStatus status, Pageable pageable);

	@Modifying
	@Query("delete from Store s where s.owner.id = :ownerId")
	int deleteByOwnerId(@Param("ownerId") Long ownerId);
}
