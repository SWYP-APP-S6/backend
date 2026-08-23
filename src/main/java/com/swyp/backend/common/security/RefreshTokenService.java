package com.swyp.backend.common.security;

import com.swyp.backend.common.exception.BusinessException;
import java.time.Duration;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class RefreshTokenService {

	private static final String KEY_PREFIX = "refresh:";

	private final StringRedisTemplate redis;
	private final Duration ttl;

	public RefreshTokenService(StringRedisTemplate redis, JwtProperties properties) {
		this.redis = redis;
		this.ttl = properties.refreshTtl();
	}

	public String issue(Long userId) {
		String token = UUID.randomUUID().toString();
		redis.opsForValue().set(KEY_PREFIX + token, String.valueOf(userId), ttl);
		return token;
	}

	public Rotation rotate(String presentedToken) {
		String userId = redis.opsForValue().getAndDelete(KEY_PREFIX + presentedToken);
		if (userId == null) {
			throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
		}
		return new Rotation(Long.valueOf(userId), issue(Long.valueOf(userId)));
	}

	public void revoke(String token) {
		redis.delete(KEY_PREFIX + token);
	}

	public record Rotation(Long userId, String token) {
	}
}
