package com.swyp.backend.analytics.repository;

import com.swyp.backend.analytics.entity.DomainEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DomainEventRepository extends JpaRepository<DomainEvent, Long> {

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
