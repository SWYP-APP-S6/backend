package com.swyp.backend.notification.repository;

import com.swyp.backend.notification.dto.PendingPush;
import com.swyp.backend.notification.entity.Notification;
import com.swyp.backend.notification.entity.NotificationPushState;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

	Page<Notification> findByUserId(Long userId, Pageable pageable);

	Optional<Notification> findByIdAndUserId(Long id, Long userId);

	List<Notification> findByUserIdAndReadAtIsNull(Long userId);

	long countByUserIdAndReadAtIsNull(Long userId);

	@Query("""
			select new com.swyp.backend.notification.dto.PendingPush(
				n.id, n.user.id, n.type, n.title, n.body, n.deepLink, n.createdAt)
			from Notification n
			where n.pushState = :pushState
			order by n.id
			""")
	List<PendingPush> findPendingPushes(
			@Param("pushState") NotificationPushState pushState, Pageable pageable);
}
