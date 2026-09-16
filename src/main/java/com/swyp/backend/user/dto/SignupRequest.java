package com.swyp.backend.user.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;

public record SignupRequest(
		@NotBlank String signupToken,
		@AssertTrue(message = "서비스 이용약관 동의가 필요합니다.") boolean serviceTermsAgreed,
		@AssertTrue(message = "개인정보 수집·이용 동의가 필요합니다.") boolean privacyTermsAgreed,
		boolean locationTermsAgreed,
		boolean thirdPartyTermsAgreed,
		boolean marketingOptIn) {
}
