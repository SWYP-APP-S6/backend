package com.swyp.backend.common.security;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class RateLimiter {

	private static final String KEY_PREFIX = "ratelimit:";
	private static final Duration WINDOW = Duration.ofMinutes(1);
	private static final Duration KEY_TTL = WINDOW.multipliedBy(2);
	private static final RedisScript<Long> INCREMENT_WITH_TTL = new DefaultRedisScript<>("""
			local count = redis.call('INCR', KEYS[1])
			if count == 1 then
				redis.call('EXPIRE', KEYS[1], ARGV[1])
			end
			return count
			""", Long.class);

	private final StringRedisTemplate redis;
	private final RateLimitProperties properties;
	private final Clock clock;

	@Autowired
	public RateLimiter(StringRedisTemplate redis, RateLimitProperties properties) {
		this(redis, properties, Clock.systemUTC());
	}

	RateLimiter(StringRedisTemplate redis, RateLimitProperties properties, Clock clock) {
		this.redis = redis;
		this.properties = properties;
		this.clock = clock;
	}

	public Decision tryAcquire(TokenRealm realm, Long principalId) {
		long nowSeconds = clock.instant().getEpochSecond();
		long window = nowSeconds / WINDOW.toSeconds();
		long retryAfterSeconds = (window + 1) * WINDOW.toSeconds() - nowSeconds;
		int limit = properties.limitFor(realm);
		String key = KEY_PREFIX + realm.name().toLowerCase(Locale.ROOT) + ":" + principalId + ":" + window;
		try {
			Long count = redis.execute(INCREMENT_WITH_TTL, List.of(key), String.valueOf(KEY_TTL.toSeconds()));
			boolean allowed = count == null || count <= limit;
			return new Decision(allowed, retryAfterSeconds);
		} catch (DataAccessException e) {
			log.warn("Rate limiter unavailable, allowing request: {}", e.getMessage());
			return new Decision(true, retryAfterSeconds);
		}
	}

	public record Decision(boolean allowed, long retryAfterSeconds) {
	}
}
