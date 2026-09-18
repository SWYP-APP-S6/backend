package com.swyp.backend.terms.controller;

import com.swyp.backend.common.response.ApiResponse;
import com.swyp.backend.common.response.SuccessCode;
import com.swyp.backend.terms.dto.TermsDocumentResponse;
import com.swyp.backend.terms.dto.TermsDocumentsResponse;
import com.swyp.backend.terms.service.TermsService;
import com.swyp.backend.user.entity.UserRole;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Terms", description = "약관")
@RestController
@RequiredArgsConstructor
@RequestMapping("/terms")
public class TermsController {

	private final TermsService termsService;

	@GetMapping
	public ApiResponse<TermsDocumentsResponse> getTermsDocuments(@RequestParam UserRole role) {
		return ApiResponse.of(SuccessCode.OK, termsService.getCurrentDocuments(role));
	}

	@GetMapping("/{termsDocumentId}")
	public ApiResponse<TermsDocumentResponse> getTermsDocument(@PathVariable Long termsDocumentId) {
		return ApiResponse.of(SuccessCode.OK, termsService.getDocument(termsDocumentId));
	}
}
