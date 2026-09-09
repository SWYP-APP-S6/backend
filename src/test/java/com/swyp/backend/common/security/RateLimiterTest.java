package com.swyp.backend.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.swyp.backend.RedisTestcontainersConfiguration;
import com.swyp.backend.TestcontainersConfiguration;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, RedisTestcontainersConfiguration.class})
class RateLimiterTest {

	private static final Instant MID_WINDOW = Instant.parse("2026-09-08T00:00:20Z");

	@Autowired
	StringRedisTemplate redis;

	@Autowired
	RateLimitProperties properties;

	private static long randomPrincipal() {
		return ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
	}

	@Test
	void allowsUpToTheLimit_thenDeniesForTheRestOfTheWindow() {
		RateLimiter limiter = new RateLimiter(redis, properties, Clock.fixed(MID_WINDOW, ZoneOffset.UTC));
		long principal = randomPrincipal();
		int limit = properties.limitFor(TokenRealm.GUEST);

		for (int i = 0; i < limit; i++) {
			assertThat(limiter.tryAcquire(TokenRealm.GUEST, principal).allowed()).isTrue();
		}
		RateLimiter.Decision denied = limiter.tryAcquire(TokenRealm.GUEST, principal);

		assertThat(denied.allowed()).isFalse();
		assertThat(denied.retryAfterSeconds()).isEqualTo(40);
	}

	@Test
	void countsEachPrincipalAndRealmSeparately() {
		RateLimiter limiter = new RateLimiter(redis, properties, Clock.fixed(MID_WINDOW, ZoneOffset.UTC));
		long principal = randomPrincipal();
		int limit = properties.limitFor(TokenRealm.GUEST);

		for (int i = 0; i <= limit; i++) {
			limiter.tryAcquire(TokenRealm.GUEST, principal);
		}

		assertThat(limiter.tryAcquire(TokenRealm.GUEST, principal).allowed()).isFalse();
		assertThat(limiter.tryAcquire(TokenRealm.GUEST, randomPrincipal()).allowed()).isTrue();
		assertThat(limiter.tryAcquire(TokenRealm.USER, principal).allowed()).isTrue();
	}

	@Test
	void resetsWhenTheMinuteRollsOver() {
		long principal = randomPrincipal();
		int limit = properties.limitFor(TokenRealm.GUEST);
		RateLimiter before = new RateLimiter(redis, properties, Clock.fixed(MID_WINDOW, ZoneOffset.UTC));
		for (int i = 0; i <= limit; i++) {
			before.tryAcquire(TokenRealm.GUEST, principal);
		}
		assertThat(before.tryAcquire(TokenRealm.GUEST, principal).allowed()).isFalse();

		RateLimiter after = new RateLimiter(
			redis, properties, Clock.fixed(MID_WINDOW.plus(Duration.ofMinutes(1)), ZoneOffset.UTC));

		assertThat(after.tryAcquire(TokenRealm.GUEST, principal).allowed()).isTrue();
	}

	@Test
	void failsOpenWhenRedisIsUnreachable() {
		LettuceConnectionFactory factory = new LettuceConnectionFactory(new RedisStandaloneConfiguration("localhost", 1));
		factory.afterPropertiesSet();
		StringRedisTemplate unreachable = new StringRedisTemplate(factory);
		unreachable.afterPropertiesSet();
		try {
			RateLimiter limiter = new RateLimiter(unreachable, properties, Clock.fixed(MID_WINDOW, ZoneOffset.UTC));

			assertThat(limiter.tryAcquire(TokenRealm.GUEST, randomPrincipal()).allowed()).isTrue();
		} finally {
			factory.destroy();
		}
	}
}
