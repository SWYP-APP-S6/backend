package com.swyp.backend.user.function;

import com.swyp.backend.common.exception.BusinessException;
import com.swyp.backend.user.entity.User;
import com.swyp.backend.user.entity.UserRole;
import com.swyp.backend.user.exception.UserAuthErrorCode;
import com.swyp.backend.user.exception.UserAuthErrorCode;
import com.swyp.backend.user.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserFunction {

	private final UserRepository userRepository;

	public User getById(Long id) {
		return userRepository.findById(id)
				.orElseThrow(() -> new BusinessException(UserAuthErrorCode.USER_NOT_FOUND));
	}

	public Optional<User> findByOauthIdentity(String provider, String providerId, UserRole role) {
		return userRepository.findByOauthProviderAndOauthProviderIdAndRole(provider, providerId, role);
	}

	public Page<User> findAllByRole(UserRole role, Pageable pageable) {
		return role == null
				? userRepository.findAll(pageable)
				: userRepository.findByRole(role, pageable);
	}

	public User save(User user) {
		try {
			return userRepository.saveAndFlush(user);
		} catch (DataIntegrityViolationException e) {
			throw new BusinessException(UserAuthErrorCode.ALREADY_REGISTERED);
		}
	}
}
