package com.swyp.backend.analytics.repository;

import com.swyp.backend.analytics.entity.DomainEvent;
import com.swyp.backend.analytics.entity.DomainEventType;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DomainEventRepository extends JpaRepository<DomainEvent, Long> {

	@Query(value = """
			select e from DomainEvent e
			where (:type is null or e.eventType = :type)
				and (:userId is null or e.userId = :userId)
				and (:storeId is null or e.storeId = :storeId)
				and (:productId is null or e.productId = :productId)
				and (:holdId is null or e.holdId = :holdId)
				and e.createdAt >= :since
				and e.createdAt < :until
			""",
			countQuery = """
			select count(e) from DomainEvent e
			where (:type is null or e.eventType = :type)
				and (:userId is null or e.userId = :userId)
				and (:storeId is null or e.storeId = :storeId)
				and (:productId is null or e.productId = :productId)
				and (:holdId is null or e.holdId = :holdId)
				and e.createdAt >= :since
				and e.createdAt < :until
			""")
	Page<DomainEvent> findMatching(
			@Param("type") DomainEventType type,
			@Param("userId") Long userId,
			@Param("storeId") Long storeId,
			@Param("productId") Long productId,
			@Param("holdId") Long holdId,
			@Param("since") Instant since,
			@Param("until") Instant until,
			Pageable pageable);

	@Modifying
	@Query("""
			delete from DomainEvent e
			where e.userId = :userId
				or e.storeId in (select s.id from Store s where s.owner.id = :userId)
				or e.productId in (select p.id from Product p where p.store.owner.id = :userId)
				or e.holdId in (
					select h.id from Hold h
					where h.user.id = :userId
						or h.store.id in (select s.id from Store s where s.owner.id = :userId))
			""")
	int deleteAllInvolving(@Param("userId") Long userId);
}
