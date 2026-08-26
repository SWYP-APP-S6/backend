package com.swyp.backend.notification.repository;

import com.swyp.backend.notification.entity.UserDeviceToken;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserDeviceTokenRepository extends JpaRepository<UserDeviceToken, Long> {

	Optional<UserDeviceToken> findByFcmToken(String fcmToken);

	List<UserDeviceToken> findByUserId(Long userId);
}
