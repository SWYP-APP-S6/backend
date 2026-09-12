package com.swyp.backend.notification.function;

import com.swyp.backend.common.exception.BusinessException;
import com.swyp.backend.notification.entity.UserDeviceToken;
import com.swyp.backend.notification.exception.NotificationErrorCode;
import com.swyp.backend.notification.repository.UserDeviceTokenRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DeviceTokenFunction {

	private final UserDeviceTokenRepository userDeviceTokenRepository;

	public Optional<UserDeviceToken> findByToken(String fcmToken) {
		return userDeviceTokenRepository.findByFcmToken(fcmToken);
	}

	public List<UserDeviceToken> findTokensOf(Long userId) {
		return userDeviceTokenRepository.findByUserId(userId);
	}

	public UserDeviceToken save(UserDeviceToken deviceToken) {
		try {
			return userDeviceTokenRepository.saveAndFlush(deviceToken);
		} catch (DataIntegrityViolationException e) {
			throw new BusinessException(NotificationErrorCode.DEVICE_TOKEN_CONFLICT);
		}
	}

	public void deleteOwnedBy(Long userId, String fcmToken) {
		userDeviceTokenRepository.findByFcmToken(fcmToken)
				.filter(deviceToken -> deviceToken.getUser().getId().equals(userId))
				.ifPresent(userDeviceTokenRepository::delete);
	}

	public void deleteTokens(List<String> fcmTokens) {
		fcmTokens.forEach(fcmToken ->
				userDeviceTokenRepository.findByFcmToken(fcmToken)
						.ifPresent(userDeviceTokenRepository::delete));
	}
}
