package com.swyp.backend.terms.repository;

import com.swyp.backend.terms.entity.UserTermsAgreement;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserTermsAgreementRepository extends JpaRepository<UserTermsAgreement, Long> {
}
