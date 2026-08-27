package com.swyp.backend.store.repository;

import com.swyp.backend.store.entity.Store;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreRepository extends JpaRepository<Store, Long> {

	Optional<Store> findByOwnerId(Long ownerId);
}
