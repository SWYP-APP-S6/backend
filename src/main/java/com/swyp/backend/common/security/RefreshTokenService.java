package com.swyp.backend.common.security;

import com.swyp.backend.common.exception.BusinessException;
import java.time.Duration;
import java.util.Locale;
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

	public String issue(TokenRealm realm, Long principalId) {
		String token = UUID.randomUUID().toString();
		redis.opsForValue().set(key(realm, token), String.valueOf(principalId), ttl);
		return token;
	}

	public Rotation rotate(TokenRealm realm, String presentedToken) {
		String principalId = redis.opsForValue().getAndDelete(key(realm, presentedToken));
		if (principalId == null) {
			throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
		}
		return new Rotation(Long.valueOf(principalId), issue(realm, Long.valueOf(principalId)));
	}

	public void revoke(TokenRealm realm, String token) {
		redis.delete(key(realm, token));
	}

	private String key(TokenRealm realm, String token) {
		return KEY_PREFIX + realm.name().toLowerCase(Locale.ROOT) + ":" + token;
	}

	public record Rotation(Long principalId, String token) {
	}
}
