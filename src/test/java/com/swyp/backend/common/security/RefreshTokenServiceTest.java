package com.swyp.backend.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.swyp.backend.RedisTestcontainersConfiguration;
import com.swyp.backend.TestcontainersConfiguration;
import com.swyp.backend.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Exercises refresh rotation against a real Redis (Testcontainers). Full context because the service
 * needs the autoconfigured StringRedisTemplate and the bound JwtProperties; the Postgres container is
 * imported because booting the app wires JPA.
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, RedisTestcontainersConfiguration.class})
class RefreshTokenServiceTest {

	@Autowired
	RefreshTokenService refreshTokenService;

	@Test
	void rotate_mintsNewTokenForSameUser_andConsumesTheOldOne() {
		String issued = refreshTokenService.issue(7L);

		RefreshTokenService.Rotation rotation = refreshTokenService.rotate(issued);

		assertThat(rotation.userId()).isEqualTo(7L);
		assertThat(rotation.token()).isNotEqualTo(issued);
		// old token is single-use: reusing it after rotation is rejected
		assertThatThrownBy(() -> refreshTokenService.rotate(issued)).isInstanceOf(BusinessException.class);
	}

	@Test
	void rotate_rejectsUnknownToken() {
		assertThatThrownBy(() -> refreshTokenService.rotate("nope")).isInstanceOf(BusinessException.class);
	}

	@Test
	void revoke_preventsFurtherRotation() {
		String issued = refreshTokenService.issue(9L);

		refreshTokenService.revoke(issued);

		assertThatThrownBy(() -> refreshTokenService.rotate(issued)).isInstanceOf(BusinessException.class);
	}
}
