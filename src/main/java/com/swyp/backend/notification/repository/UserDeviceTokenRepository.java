package com.swyp.backend.notification.repository;

import com.swyp.backend.notification.entity.UserDeviceToken;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserDeviceTokenRepository extends JpaRepository<UserDeviceToken, Long> {

	Optional<UserDeviceToken> findByFcmToken(String fcmToken);

	List<UserDeviceToken> findByUserId(Long userId);

	@Modifying
	@Query("delete from UserDeviceToken t where t.user.id = :userId")
	int deleteByUserId(@Param("userId") Long userId);
}
