package com.swyp.backend.common.security;

import com.swyp.backend.common.exception.BusinessException;
import java.time.Duration;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Opaque refresh tokens stored in Redis ({@code refresh:{token} -> userId}, with the refresh TTL).
 * Redis is the source of truth — a refresh token is valid iff its key exists, so logout and rotation
 * both work by deleting the key.
 *
 * <p>Rotation is one-time-use: {@link #rotate} atomically consumes (GETDEL) the presented token and
 * mints a new one, so a previously rotated token is rejected on reuse. (Family-wide revocation on
 * reuse detection is a planned enhancement.)
 */
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

	/** Consume the presented token and mint a new one. Missing token (expired/rotated/forged) → reject. */
	public Rotation rotate(String presentedToken) {
		String userId = redis.opsForValue().getAndDelete(KEY_PREFIX + presentedToken);
		if (userId == null) {
			throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
		}
		return new Rotation(Long.valueOf(userId), issue(Long.valueOf(userId)));
	}

	/** Logout: drop the token so it can no longer be rotated. */
	public void revoke(String token) {
		redis.delete(KEY_PREFIX + token);
	}

	public record Rotation(Long userId, String token) {
	}
}
