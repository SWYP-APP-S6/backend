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

@SpringBootTest
@Import({TestcontainersConfiguration.class, RedisTestcontainersConfiguration.class})
class RefreshTokenServiceTest {

	@Autowired
	RefreshTokenService refreshTokenService;

	@Test
	void rotate_mintsNewTokenForSamePrincipal_andConsumesTheOldOne() {
		String issued = refreshTokenService.issue(TokenRealm.ADMIN, 7L);

		RefreshTokenService.Rotation rotation = refreshTokenService.rotate(TokenRealm.ADMIN, issued);

		assertThat(rotation.principalId()).isEqualTo(7L);
		assertThat(rotation.token()).isNotEqualTo(issued);
		assertThatThrownBy(() -> refreshTokenService.rotate(TokenRealm.ADMIN, issued))
			.isInstanceOf(BusinessException.class);
	}

	@Test
	void rotate_rejectsUnknownToken() {
		assertThatThrownBy(() -> refreshTokenService.rotate(TokenRealm.ADMIN, "nope"))
			.isInstanceOf(BusinessException.class);
	}

	@Test
	void revoke_preventsFurtherRotation() {
		String issued = refreshTokenService.issue(TokenRealm.ADMIN, 9L);

		refreshTokenService.revoke(TokenRealm.ADMIN, issued);

		assertThatThrownBy(() -> refreshTokenService.rotate(TokenRealm.ADMIN, issued))
			.isInstanceOf(BusinessException.class);
	}

	@Test
	void rotate_rejectsTokenIssuedForAnotherRealm() {
		String userToken = refreshTokenService.issue(TokenRealm.USER, 11L);

		assertThatThrownBy(() -> refreshTokenService.rotate(TokenRealm.ADMIN, userToken))
			.isInstanceOf(BusinessException.class);

		assertThat(refreshTokenService.rotate(TokenRealm.USER, userToken).principalId()).isEqualTo(11L);
	}

	@Test
	void revoke_doesNotReachTheSameTokenInAnotherRealm() {
		String userToken = refreshTokenService.issue(TokenRealm.USER, 13L);

		refreshTokenService.revoke(TokenRealm.ADMIN, userToken);

		assertThat(refreshTokenService.rotate(TokenRealm.USER, userToken).principalId()).isEqualTo(13L);
	}
}
