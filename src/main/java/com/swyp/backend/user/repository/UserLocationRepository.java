package com.swyp.backend.user.repository;

import com.swyp.backend.user.entity.UserLocation;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserLocationRepository extends JpaRepository<UserLocation, Long> {

	List<UserLocation> findByUserIdIn(Collection<Long> userIds);

	Optional<UserLocation> findByUserId(Long userId);
}
