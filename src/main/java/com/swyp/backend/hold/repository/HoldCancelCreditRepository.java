package com.swyp.backend.hold.repository;

import com.swyp.backend.hold.entity.HoldCancelCredit;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HoldCancelCreditRepository extends JpaRepository<HoldCancelCredit, Long> {

	Optional<HoldCancelCredit> findByUserId(Long userId);
}
