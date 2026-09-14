package com.swyp.backend.terms.service;

import com.swyp.backend.terms.dto.TermsDocumentResponse;
import com.swyp.backend.terms.dto.TermsDocumentSummaryResponse;
import com.swyp.backend.terms.dto.TermsDocumentsResponse;
import com.swyp.backend.terms.entity.TermsDocument;
import com.swyp.backend.terms.function.TermsFunction;
import com.swyp.backend.user.entity.UserRole;
import java.util.Comparator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TermsService {

	private final TermsFunction termsFunction;

	public TermsDocumentsResponse getCurrentDocuments(UserRole role) {
		return new TermsDocumentsResponse(termsFunction.findCurrentOf(role).stream()
				.sorted(Comparator.comparing(TermsDocument::getType))
				.map(TermsDocumentSummaryResponse::from)
				.toList());
	}

	public TermsDocumentResponse getDocument(Long termsDocumentId) {
		return TermsDocumentResponse.from(termsFunction.getById(termsDocumentId));
	}
}
