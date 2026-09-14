package com.swyp.backend.terms.dto;

import com.swyp.backend.terms.entity.TermsDocument;
import com.swyp.backend.terms.entity.TermsRequirement;
import com.swyp.backend.terms.entity.TermsType;
import com.swyp.backend.user.entity.UserRole;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

public record TermsDocumentResponse(
		Long id,
		UserRole role,
		TermsType type,
		int version,
		String title,
		TermsRequirement requirement,
		@Nullable LocalDate effectiveDate,
		String contentMarkdown) {

	public static TermsDocumentResponse from(TermsDocument document) {
		return new TermsDocumentResponse(
				document.getId(),
				document.getRole(),
				document.getType(),
				document.getVersion(),
				document.getTitle(),
				document.getRequirement(),
				document.getEffectiveDate(),
				document.getContent());
	}
}
