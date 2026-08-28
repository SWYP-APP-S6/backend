package com.swyp.backend.store.repository;

import com.swyp.backend.store.entity.Store;
import com.swyp.backend.store.entity.StoreStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreRepository extends JpaRepository<Store, Long> {

	Optional<Store> findByOwnerId(Long ownerId);

	// 목록에서 점주를 함께 보여주므로 미리 당겨온다 — 없으면 행마다 조회가 한 번씩 더 나간다.
	@EntityGraph(attributePaths = "owner")
	Page<Store> findAllBy(Pageable pageable);

	@EntityGraph(attributePaths = "owner")
	Page<Store> findByStatus(StoreStatus status, Pageable pageable);
}
