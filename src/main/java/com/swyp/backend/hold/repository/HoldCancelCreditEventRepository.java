package com.swyp.backend.hold.repository;

import com.swyp.backend.hold.entity.HoldCancelCreditEvent;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HoldCancelCreditEventRepository
		extends JpaRepository<HoldCancelCreditEvent, Long> {

	@Query("""
			select e.hold.id from HoldCancelCreditEvent e
			where e.hold.id in :holdIds and e.delta < 0
			""")
	List<Long> findChargedHoldIds(@Param("holdIds") Collection<Long> holdIds);

	@Modifying
	@Query("""
			delete from HoldCancelCreditEvent e
			where e.user.id = :userId
				or e.hold.id in (
					select h.id from Hold h
					where h.user.id = :userId
						or h.store.id in (select s.id from Store s where s.owner.id = :userId))
			""")
	int deleteAllInvolving(@Param("userId") Long userId);
}
