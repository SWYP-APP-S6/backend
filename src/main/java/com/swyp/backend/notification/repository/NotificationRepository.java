package com.swyp.backend.notification.repository;

import com.swyp.backend.notification.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

	Page<Notification> findByUserId(Long userId, Pageable pageable);

	long countByUserIdAndReadAtIsNull(Long userId);
}
