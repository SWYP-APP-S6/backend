package com.swyp.backend.notification.repository;

import com.swyp.backend.notification.dto.PendingPush;
import java.time.Instant;
import com.swyp.backend.notification.entity.NotificationType;
import com.swyp.backend.notification.dto.PushStateCount;
import com.swyp.backend.notification.entity.Notification;
import com.swyp.backend.notification.entity.NotificationPushState;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

	@Query("""
			select new com.swyp.backend.notification.dto.PushStateCount(n.pushState, count(n))
			from Notification n
			where n.createdAt >= :since
			group by n.pushState
			""")
	List<PushStateCount> countByPushStateSince(@Param("since") Instant since);

	@Query("""
			select min(n.createdAt) from Notification n
			where n.pushState = com.swyp.backend.notification.entity.NotificationPushState.PENDING
			""")
	Optional<Instant> findOldestPendingCreatedAt();

	@Query(value = """
			select n from Notification n
			join fetch n.user
			where (:pushState is null or n.pushState = :pushState)
				and (:userId is null or n.user.id = :userId)
				and (:type is null or n.type = :type)
			""",
			countQuery = """
			select count(n) from Notification n
			where (:pushState is null or n.pushState = :pushState)
				and (:userId is null or n.user.id = :userId)
				and (:type is null or n.type = :type)
			""")
	Page<Notification> findForAdmin(
			@Param("pushState") NotificationPushState pushState,
			@Param("userId") Long userId,
			@Param("type") NotificationType type,
			Pageable pageable);

	@Modifying
	@Query("delete from Notification n where n.user.id = :userId")
	int deleteByUserId(@Param("userId") Long userId);
}
