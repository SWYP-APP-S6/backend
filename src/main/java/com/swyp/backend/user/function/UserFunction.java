package com.swyp.backend.user.function;

import com.swyp.backend.common.exception.BusinessException;
import com.swyp.backend.user.entity.User;
import com.swyp.backend.user.entity.UserRole;
import com.swyp.backend.user.exception.UserAuthErrorCode;
import com.swyp.backend.user.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
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

	public User getByIdForUpdate(Long id) {
		return userRepository.findByIdForUpdate(id)
				.orElseThrow(() -> new BusinessException(UserAuthErrorCode.USER_NOT_FOUND));
	}

	public boolean isTester(@Nullable Long userId) {
		return userId != null && userRepository.existsByIdAndTesterTrue(userId);
	}

	public Optional<User> findAccount(String provider, String providerId, UserRole role) {
		return userRepository.findByOauthProviderAndOauthProviderIdAndRoleAndTester(
				provider, providerId, role, false);
	}

	public Optional<User> findTestAccount(String provider, String providerId, UserRole role) {
		return userRepository.findByOauthProviderAndOauthProviderIdAndRoleAndTester(
				provider, providerId, role, true);
	}

	public Optional<User> findAccountOf(User testAccount) {
		if (testAccount.getOauthProviderId() == null) {
			return Optional.empty();
		}
		return findAccount(testAccount.getOauthProvider(), testAccount.getOauthProviderId(), testAccount.getRole());
	}

	public User getOrCreateTestAccountOf(User account, Instant now) {
		if (account.getOauthProviderId() == null) {
			throw new BusinessException(UserAuthErrorCode.TESTER_NEEDS_KAKAO_ACCOUNT);
		}
		return findTestAccountOf(account).orElseGet(() -> userRepository.save(account.newTestAccount(now)));
	}

	public Optional<User> findTestAccountOf(User account) {
		if (account.getOauthProviderId() == null) {
			return Optional.empty();
		}
		return findTestAccount(account.getOauthProvider(), account.getOauthProviderId(), account.getRole());
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

	public void delete(User user) {
		userRepository.delete(user);
	}
}
