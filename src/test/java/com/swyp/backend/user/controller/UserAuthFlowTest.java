package com.swyp.backend.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.swyp.backend.RedisTestcontainersConfiguration;
import com.swyp.backend.TestcontainersConfiguration;
import com.swyp.backend.common.exception.BusinessException;
import com.swyp.backend.user.entity.User;
import com.swyp.backend.user.entity.UserRole;
import com.swyp.backend.user.exception.UserAuthErrorCode;
import com.swyp.backend.user.repository.UserRepository;
import com.swyp.backend.user.service.KakaoOauthClient;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, RedisTestcontainersConfiguration.class})
class UserAuthFlowTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	UserRepository userRepository;

	@Autowired
	StubKakaoOauthClient kakaoOauthClient;

	private ResultActions login(UserRole role, String kakaoToken) throws Exception {
		String path = role == UserRole.CONSUMER ? "/auth/consumer/kakao" : "/auth/owner/kakao";
		return mockMvc.perform(post(path)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"kakaoAccessToken\":\"" + kakaoToken + "\"}"));
	}

	private static String signupBody(String signupToken, boolean allTermsAgreed) {
		return """
			{"signupToken":"%s","serviceTermsAgreed":%b,"privacyTermsAgreed":%b,\
			"locationTermsAgreed":true,"marketingOptIn":false}"""
			.formatted(signupToken, allTermsAgreed, allTermsAgreed);
	}

	private String signupTokenFor(UserRole role, String kakaoToken, String providerId, String nickname)
			throws Exception {
		kakaoOauthClient.register(role, kakaoToken, new KakaoOauthClient.Identity(providerId, nickname));
		String body = login(role, kakaoToken)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.registered").value(false))
			.andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.data.signupToken");
	}

	private String signUp(UserRole role, String kakaoToken, String providerId, String nickname, String jsonPath)
			throws Exception {
		String signupToken = signupTokenFor(role, kakaoToken, providerId, nickname);
		String body = mockMvc.perform(post("/auth/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content(signupBody(signupToken, true)))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, jsonPath);
	}

	private Optional<User> storedUser(String providerId, UserRole role) {
		return userRepository.findByOauthProviderAndOauthProviderIdAndRole("kakao", providerId, role);
	}

	@Test
	void firstKakaoLogin_returnsSignupToken_andCreatesNoAccountYet() throws Exception {
		String signupToken = signupTokenFor(UserRole.CONSUMER, "token-new-consumer", "kakao-1001", "성문");

		assertThat(signupToken).isNotBlank();
		assertThat(storedUser("kakao-1001", UserRole.CONSUMER)).isEmpty();
	}

	@Test
	void signup_createsTheAccount_andTheNextLoginReturnsTokensDirectly() throws Exception {
		String accessToken = signUp(UserRole.CONSUMER, "token-signup", "kakao-1002", "가입자", "$.data.accessToken");

		assertThat(accessToken).isNotBlank();
		assertThat(storedUser("kakao-1002", UserRole.CONSUMER))
			.get()
			.satisfies(user -> {
				assertThat(user.getRole()).isEqualTo(UserRole.CONSUMER);
				assertThat(user.getNickname()).isEqualTo("가입자");
				assertThat(user.getTermsAgreedAt()).isNotNull();
			});

		login(UserRole.CONSUMER, "token-signup")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.registered").value(true))
			.andExpect(jsonPath("$.data.accessToken").exists())
			.andExpect(jsonPath("$.data.signupToken").doesNotExist());
	}

	@Test
	void signup_withoutTheRequiredTerms_isRejected() throws Exception {
		String signupToken = signupTokenFor(UserRole.CONSUMER, "token-no-terms", "kakao-1003", "미동의");

		mockMvc.perform(post("/auth/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content(signupBody(signupToken, false)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

		assertThat(storedUser("kakao-1003", UserRole.CONSUMER)).isEmpty();
	}

	@Test
	void signup_withAConsumedTicket_conflicts() throws Exception {
		String signupToken = signupTokenFor(UserRole.CONSUMER, "token-replay", "kakao-1004", "재사용");
		mockMvc.perform(post("/auth/signup")
			.contentType(MediaType.APPLICATION_JSON)
			.content(signupBody(signupToken, true))).andExpect(status().isCreated());

		mockMvc.perform(post("/auth/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content(signupBody(signupToken, true)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ALREADY_REGISTERED"));
	}

	@Test
	void signup_withAForgedTicket_isRejected() throws Exception {
		mockMvc.perform(post("/auth/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content(signupBody("not-a-real-token", true)))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("INVALID_SIGNUP_TOKEN"));
	}

	@Test
	void kakaoLogin_withAnUnknownToken_isRejected() throws Exception {
		login(UserRole.CONSUMER, "token-kakao-never-issued")
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("INVALID_OAUTH_TOKEN"));
	}

	@Test
	void consumerAppToken_isRejectedOnTheOwnerEndpoint() throws Exception {
		kakaoOauthClient.register(
			UserRole.CONSUMER, "token-consumer-only", new KakaoOauthClient.Identity("kakao-1005", "소비자"));

		login(UserRole.OWNER, "token-consumer-only")
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("INVALID_OAUTH_TOKEN"));
	}

	@Test
	void theSameKakaoNumberInBothApps_becomesTwoIndependentAccounts() throws Exception {
		signUp(UserRole.CONSUMER, "token-shared-consumer", "kakao-777", "소비자쪽", "$.data.accessToken");
		signUp(UserRole.OWNER, "token-shared-owner", "kakao-777", "점주쪽", "$.data.accessToken");

		User consumer = storedUser("kakao-777", UserRole.CONSUMER).orElseThrow();
		User owner = storedUser("kakao-777", UserRole.OWNER).orElseThrow();

		assertThat(consumer.getId()).isNotEqualTo(owner.getId());
		assertThat(consumer.getNickname()).isEqualTo("소비자쪽");
		assertThat(owner.getNickname()).isEqualTo("점주쪽");
	}

	@Test
	void missingNickname_fallsBackToAGeneratedOne() throws Exception {
		signUp(UserRole.CONSUMER, "token-no-nickname", "kakao-1006", null, "$.data.accessToken");

		assertThat(storedUser("kakao-1006", UserRole.CONSUMER))
			.get()
			.satisfies(user -> assertThat(user.getNickname()).isEqualTo("맹그로회원1006"));
	}

	@Test
	void refresh_rotatesTheToken_andLogoutRevokesIt() throws Exception {
		String refreshToken = signUp(UserRole.CONSUMER, "token-refresh", "kakao-1007", "회전", "$.data.refreshToken");

		String rotated = mockMvc.perform(post("/auth/refresh")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"refreshToken\":\"" + refreshToken + "\"}"))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();
		String rotatedRefresh = JsonPath.read(rotated, "$.data.refreshToken");
		assertThat(rotatedRefresh).isNotEqualTo(refreshToken);

		mockMvc.perform(post("/auth/refresh")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"refreshToken\":\"" + refreshToken + "\"}")).andExpect(status().isUnauthorized());

		mockMvc.perform(post("/auth/logout")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"refreshToken\":\"" + rotatedRefresh + "\"}")).andExpect(status().isOk());

		mockMvc.perform(post("/auth/refresh")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"refreshToken\":\"" + rotatedRefresh + "\"}")).andExpect(status().isUnauthorized());
	}

	@Test
	void appUserRefreshToken_cannotBeRotatedOnTheAdminEndpoint() throws Exception {
		String refreshToken = signUp(
			UserRole.CONSUMER, "token-realm-refresh", "kakao-1008", "리얼름", "$.data.refreshToken");

		mockMvc.perform(post("/admin/auth/refresh")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"refreshToken\":\"" + refreshToken + "\"}"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
	}

	@Test
	void appUserAccessToken_cannotReachAdminApi() throws Exception {
		String accessToken = signUp(
			UserRole.CONSUMER, "token-admin-probe", "kakao-1009", "침입자", "$.data.accessToken");

		mockMvc.perform(get("/admin/users").header("Authorization", "Bearer " + accessToken))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("FORBIDDEN"));
	}

	@Test
	void signupToken_isNotAcceptedAsAnAccessToken() throws Exception {
		String signupToken = signupTokenFor(UserRole.CONSUMER, "token-as-access", "kakao-1010", "가짜");

		mockMvc.perform(get("/admin/users").header("Authorization", "Bearer " + signupToken))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	@TestConfiguration
	static class StubKakaoOauthClientConfiguration {

		@Bean
		@Primary
		StubKakaoOauthClient stubKakaoOauthClient() {
			return new StubKakaoOauthClient();
		}
	}

	static class StubKakaoOauthClient implements KakaoOauthClient {

		private final Map<String, Identity> identities = new ConcurrentHashMap<>();

		void register(UserRole role, String kakaoAccessToken, Identity identity) {
			identities.put(key(role, kakaoAccessToken), identity);
		}

		@Override
		public Identity fetchIdentity(UserRole role, String kakaoAccessToken) {
			Identity identity = identities.get(key(role, kakaoAccessToken));
			if (identity == null) {
				throw new BusinessException(UserAuthErrorCode.INVALID_OAUTH_TOKEN);
			}
			return identity;
		}

		private static String key(UserRole role, String kakaoAccessToken) {
			return role.name() + ":" + kakaoAccessToken;
		}
	}
}
