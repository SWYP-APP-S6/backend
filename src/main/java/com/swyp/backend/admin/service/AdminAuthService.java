package com.swyp.backend.admin.service;

import com.swyp.backend.admin.dto.TokenResponse;
import com.swyp.backend.admin.entity.Admin;
import com.swyp.backend.admin.repository.AdminRepository;
import com.swyp.backend.common.exception.BusinessException;
import com.swyp.backend.common.security.AuthErrorCode;
import com.swyp.backend.common.security.JwtTokenProvider;
import com.swyp.backend.common.security.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin credential authentication. Issues a JWT access token (subject = admin id, role = AdminType)
 * plus a rotating Redis refresh token. No DB writes — only reads and Redis, so the whole service is
 * read-only for JPA.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminAuthService {

	private final AdminRepository adminRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtTokenProvider tokenProvider;
	private final RefreshTokenService refreshTokenService;

	public TokenResponse login(String email, String rawPassword) {
		Admin admin = adminRepository.findByEmail(email)
				.orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_CREDENTIALS));
		if (!passwordEncoder.matches(rawPassword, admin.getPassword())) {
			throw new BusinessException(AuthErrorCode.INVALID_CREDENTIALS);
		}
		return issueFor(admin);
	}

	public TokenResponse refresh(String refreshToken) {
		RefreshTokenService.Rotation rotation = refreshTokenService.rotate(refreshToken);
		Admin admin = adminRepository.findById(rotation.userId())
				.orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_CREDENTIALS));
		String accessToken = tokenProvider.createAccessToken(admin.getId(), admin.getType().name());
		return new TokenResponse(accessToken, rotation.token());
	}

	public void logout(String refreshToken) {
		refreshTokenService.revoke(refreshToken);
	}

	private TokenResponse issueFor(Admin admin) {
		String accessToken = tokenProvider.createAccessToken(admin.getId(), admin.getType().name());
		String refreshToken = refreshTokenService.issue(admin.getId());
		return new TokenResponse(accessToken, refreshToken);
	}
}
