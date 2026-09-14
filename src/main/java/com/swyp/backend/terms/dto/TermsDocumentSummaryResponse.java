package com.swyp.backend.terms.dto;

import com.swyp.backend.terms.entity.TermsDocument;
import com.swyp.backend.terms.entity.TermsRequirement;
import com.swyp.backend.terms.entity.TermsType;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

public record TermsDocumentSummaryResponse(
		Long id,
		TermsType type,
		int version,
		String title,
		TermsRequirement requirement,
		@Nullable LocalDate effectiveDate) {

	public static TermsDocumentSummaryResponse from(TermsDocument document) {
		return new TermsDocumentSummaryResponse(
				document.getId(),
				document.getType(),
				document.getVersion(),
				document.getTitle(),
				document.getRequirement(),
				document.getEffectiveDate());
	}
}
