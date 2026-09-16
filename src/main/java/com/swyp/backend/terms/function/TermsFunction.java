package com.swyp.backend.terms.function;

import com.swyp.backend.common.exception.BusinessException;
import com.swyp.backend.terms.entity.TermsDocument;
import com.swyp.backend.terms.entity.TermsType;
import com.swyp.backend.terms.entity.UserTermsAgreement;
import com.swyp.backend.terms.exception.TermsErrorCode;
import com.swyp.backend.terms.repository.TermsDocumentRepository;
import com.swyp.backend.terms.repository.UserTermsAgreementRepository;
import com.swyp.backend.user.entity.User;
import com.swyp.backend.user.entity.UserRole;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TermsFunction {

	private final TermsDocumentRepository termsDocumentRepository;
	private final UserTermsAgreementRepository userTermsAgreementRepository;

	public List<TermsDocument> findCurrentOf(UserRole role) {
		return termsDocumentRepository.findCurrentByRole(role);
	}

	public TermsDocument getById(Long termsDocumentId) {
		return termsDocumentRepository.findById(termsDocumentId)
				.orElseThrow(() -> new BusinessException(TermsErrorCode.TERMS_DOCUMENT_NOT_FOUND));
	}

	public List<UserTermsAgreement> recordAgreements(User user, Set<TermsType> agreedTypes, Instant agreedAt) {
		List<UserTermsAgreement> agreements = findCurrentOf(user.getRole()).stream()
				.filter(document -> agreedTypes.contains(document.getType()))
				.map(document -> new UserTermsAgreement(user, document, agreedAt))
				.toList();
		return userTermsAgreementRepository.saveAll(agreements);
	}

	public void deleteAgreementsOf(Long userId) {
		userTermsAgreementRepository.deleteByUserId(userId);
	}
}
