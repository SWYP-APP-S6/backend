package com.swyp.backend.admin.service;

import com.swyp.backend.admin.dto.TokenResponse;
import com.swyp.backend.admin.entity.Admin;
import com.swyp.backend.admin.function.AdminFunction;
import com.swyp.backend.common.exception.BusinessException;
import com.swyp.backend.common.security.AuthErrorCode;
import com.swyp.backend.common.security.JwtTokenProvider;
import com.swyp.backend.common.security.RefreshTokenService;
import com.swyp.backend.common.security.TokenRealm;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminAuthService {

	private final AdminFunction adminFunction;
	private final PasswordEncoder passwordEncoder;
	private final JwtTokenProvider tokenProvider;
	private final RefreshTokenService refreshTokenService;

	public TokenResponse login(String email, String rawPassword) {
		Admin admin = adminFunction.getByEmail(email);
		if (!passwordEncoder.matches(rawPassword, admin.getPassword())) {
			throw new BusinessException(AuthErrorCode.INVALID_CREDENTIALS);
		}
		return issueFor(admin);
	}

	public TokenResponse refresh(String refreshToken) {
		RefreshTokenService.Rotation rotation = refreshTokenService.rotate(TokenRealm.ADMIN, refreshToken);
		Admin admin = adminFunction.getById(rotation.principalId());
		String accessToken = accessTokenFor(admin);
		return new TokenResponse(accessToken, rotation.token());
	}

	public void logout(String refreshToken) {
		refreshTokenService.revoke(TokenRealm.ADMIN, refreshToken);
	}

	@Transactional
	public void changePassword(Long adminId, String currentPassword, String newPassword) {
		Admin admin = adminFunction.getById(adminId);
		if (!passwordEncoder.matches(currentPassword, admin.getPassword())) {
			throw new BusinessException(AuthErrorCode.INVALID_CREDENTIALS);
		}
		admin.changePassword(passwordEncoder.encode(newPassword));
	}

	private TokenResponse issueFor(Admin admin) {
		return new TokenResponse(accessTokenFor(admin), refreshTokenService.issue(TokenRealm.ADMIN, admin.getId()));
	}

	private String accessTokenFor(Admin admin) {
		return tokenProvider.createAccessToken(TokenRealm.ADMIN, admin.getId(), admin.getType().name());
	}
}
