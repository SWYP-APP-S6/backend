package com.swyp.backend.hold.repository;

import com.swyp.backend.hold.entity.HoldCancelCredit;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HoldCancelCreditRepository extends JpaRepository<HoldCancelCredit, Long> {

	Optional<HoldCancelCredit> findByUserId(Long userId);

	@Modifying
	@Query("delete from HoldCancelCredit c where c.user.id = :userId")
	int deleteByUserId(@Param("userId") Long userId);
}
