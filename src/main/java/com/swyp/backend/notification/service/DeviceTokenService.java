package com.swyp.backend.notification.service;

import com.swyp.backend.notification.dto.DeviceTokenDeleteRequest;
import com.swyp.backend.notification.dto.DeviceTokenRegisterRequest;
import com.swyp.backend.notification.entity.UserDeviceToken;
import com.swyp.backend.notification.function.DeviceTokenFunction;
import com.swyp.backend.user.entity.User;
import com.swyp.backend.user.function.UserFunction;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DeviceTokenService {

	private final DeviceTokenFunction deviceTokenFunction;
	private final UserFunction userFunction;
	private final Clock clock;

	@Transactional
	public void register(Long userId, DeviceTokenRegisterRequest request) {
		Instant now = Instant.now(clock);
		User user = userFunction.getById(userId);
		deviceTokenFunction.findByToken(request.fcmToken()).ifPresentOrElse(
				deviceToken -> deviceToken.reassignTo(user, request.platform(), now),
				() -> deviceTokenFunction.save(
						new UserDeviceToken(user, request.platform(), request.fcmToken(), now)));
	}

	@Transactional
	public void unregister(Long userId, DeviceTokenDeleteRequest request) {
		deviceTokenFunction.deleteOwnedBy(userId, request.fcmToken());
	}
}
