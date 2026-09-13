package com.swyp.backend.hold.repository;

import com.swyp.backend.hold.entity.HoldCancelCreditEvent;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HoldCancelCreditEventRepository
		extends JpaRepository<HoldCancelCreditEvent, Long> {

	@Query("""
			select e.hold.id from HoldCancelCreditEvent e
			where e.hold.id in :holdIds and e.delta < 0
			""")
	List<Long> findChargedHoldIds(@Param("holdIds") Collection<Long> holdIds);
}
