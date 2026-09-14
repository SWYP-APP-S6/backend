package com.swyp.backend.terms.dto;

import java.util.List;

public record TermsDocumentsResponse(List<TermsDocumentSummaryResponse> documents) {
}
