package com.swyp.backend.terms.repository;

import com.swyp.backend.terms.entity.UserTermsAgreement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserTermsAgreementRepository extends JpaRepository<UserTermsAgreement, Long> {

	@Modifying
	@Query("delete from UserTermsAgreement a where a.user.id = :userId")
	int deleteByUserId(@Param("userId") Long userId);
}
