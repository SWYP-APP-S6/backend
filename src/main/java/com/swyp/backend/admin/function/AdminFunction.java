package com.swyp.backend.admin.function;

import com.swyp.backend.admin.entity.Admin;
import com.swyp.backend.admin.repository.AdminRepository;
import com.swyp.backend.common.exception.BusinessException;
import com.swyp.backend.common.security.AuthErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdminFunction {

	private final AdminRepository adminRepository;

	public Admin getByEmail(String email) {
		return adminRepository.findByEmail(email)
				.orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_CREDENTIALS));
	}

	public Admin getById(Long id) {
		return adminRepository.findById(id)
				.orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_CREDENTIALS));
	}
}
