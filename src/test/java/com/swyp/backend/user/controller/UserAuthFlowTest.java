package com.swyp.backend.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.swyp.backend.AppDataCleaner;
import com.swyp.backend.RedisTestcontainersConfiguration;
import com.swyp.backend.TestcontainersConfiguration;
import com.swyp.backend.common.exception.BusinessException;
import com.swyp.backend.terms.entity.TermsDocument;
import com.swyp.backend.terms.entity.TermsRequirement;
import com.swyp.backend.terms.entity.TermsType;
import com.swyp.backend.terms.repository.TermsDocumentRepository;
import com.swyp.backend.user.entity.User;
import com.swyp.backend.user.entity.UserRole;
import com.swyp.backend.user.exception.UserAuthErrorCode;
import com.swyp.backend.user.repository.UserRepository;
import com.swyp.backend.user.service.KakaoOauthClient;
import java.util.List;
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
import org.springframework.jdbc.core.JdbcTemplate;
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

	@Autowired
	AppDataCleaner appDataCleaner;

	@Autowired
	TermsDocumentRepository termsDocumentRepository;

	@Autowired
	JdbcTemplate jdbcTemplate;

	private ResultActions login(UserRole role, String kakaoToken) throws Exception {
		String path = role == UserRole.CONSUMER ? "/auth/consumer/kakao" : "/auth/owner/kakao";
		return mockMvc.perform(post(path)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"kakaoAccessToken\":\"" + kakaoToken + "\"}"));
	}

	private static String signupBody(String signupToken, boolean allTermsAgreed) {
		return """
			{"signupToken":"%s","serviceTermsAgreed":%b,"privacyTermsAgreed":%b,\
			"locationTermsAgreed":true,"thirdPartyTermsAgreed":true,"marketingOptIn":false}"""
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
	void signup_withoutTheThirdPartyConsent_isRejectedWhereThatDocumentIsRequired() throws Exception {
		appDataCleaner.clear();
		publishConsumerTerms();
		String signupToken = signupTokenFor(UserRole.CONSUMER, "token-no-third", "kakao-1007", "제3자미동의");

		mockMvc.perform(post("/auth/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"signupToken":"%s","serviceTermsAgreed":true,"privacyTermsAgreed":true,\
					"locationTermsAgreed":true,"thirdPartyTermsAgreed":false,"marketingOptIn":false}"""
					.formatted(signupToken)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("TERMS_AGREEMENT_REQUIRED"));

		assertThat(storedUser("kakao-1007", UserRole.CONSUMER)).isEmpty();
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
	void kakaoCodeExchange_returnsTheAccessTokenTheCodeWasIssuedFor() throws Exception {
		kakaoOauthClient.registerCode(
			UserRole.CONSUMER, "auth-code-1", "http://localhost:5173/kakao-test", "exchanged-token");

		mockMvc.perform(post("/auth/consumer/kakao/exchange")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"code":"auth-code-1","redirectUri":"http://localhost:5173/kakao-test"}"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.kakaoAccessToken").value("exchanged-token"));
	}

	@Test
	void kakaoCodeExchange_withAnUnknownCode_isRejected() throws Exception {
		mockMvc.perform(post("/auth/consumer/kakao/exchange")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"code":"never-issued","redirectUri":"http://localhost:5173/kakao-test"}"""))
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

	@Test
	void signup_recordsWhichVersionOfEachDocumentWasAgreedTo_atTheMomentOfSignup() throws Exception {
		appDataCleaner.clear();
		publishConsumerTerms();
		publish(UserRole.CONSUMER, TermsType.SERVICE, 2, TermsRequirement.REQUIRED);

		Long userId = signUpAgreeing(UserRole.CONSUMER, "token-terms-1", "kakao-2001", false);

		assertThat(agreedDocuments(userId))
			.as("the privacy policy is shown rather than agreed to, and marketing was declined")
			.containsExactlyInAnyOrder("SERVICE v2", "PRIVACY_COLLECTION v1", "LOCATION v1", "THIRD_PARTY v1");
		assertThat(jdbcTemplate.queryForObject("""
				select count(*) from user_terms_agreements a join users u on u.id = a.user_id
				where a.user_id = ? and a.agreed_at <> u.terms_agreed_at""", Integer.class, userId))
			.as("each agreement carries the same instant as the account's own signup time")
			.isZero();
	}

	@Test
	void signup_recordsTheMarketingConsentOnlyWhenItWasGiven() throws Exception {
		appDataCleaner.clear();
		publishConsumerTerms();

		Long userId = signUpAgreeing(UserRole.CONSUMER, "token-terms-2", "kakao-2002", true);

		assertThat(agreedDocuments(userId)).contains("MARKETING v1");
	}

	@Test
	void ownerSignup_asksOnlyForTheTermsTheOwnerAppShows() throws Exception {
		appDataCleaner.clear();
		publishConsumerTerms();
		publishOwnerTerms();
		String signupToken = signupTokenFor(UserRole.OWNER, "token-terms-3", "kakao-2003", "사장님");

		mockMvc.perform(post("/auth/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"signupToken":"%s","serviceTermsAgreed":true,"privacyTermsAgreed":true,\
					"locationTermsAgreed":false,"thirdPartyTermsAgreed":false,"marketingOptIn":false}"""
					.formatted(signupToken)))
			.andExpect(status().isCreated());

		Long userId = storedUser("kakao-2003", UserRole.OWNER).orElseThrow().getId();
		assertThat(agreedDocuments(userId))
			.as("the owner screen shows neither the location nor the third-party document, and a "
				+ "consumer document never lands on an owner")
			.containsExactlyInAnyOrder("SERVICE v1", "PRIVACY_COLLECTION v1");
	}

	@Test
	void ownerSignup_isRejectedOnceTheOwnerTermsRequireTheLocationConsent() throws Exception {
		appDataCleaner.clear();
		publishOwnerTerms();
		publish(UserRole.OWNER, TermsType.LOCATION, 1, TermsRequirement.REQUIRED);
		String signupToken = signupTokenFor(UserRole.OWNER, "token-terms-5", "kakao-2005", "사장님");

		mockMvc.perform(post("/auth/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"signupToken":"%s","serviceTermsAgreed":true,"privacyTermsAgreed":true,\
					"locationTermsAgreed":false,"thirdPartyTermsAgreed":false,"marketingOptIn":false}"""
					.formatted(signupToken)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("TERMS_AGREEMENT_REQUIRED"));

		assertThat(storedUser("kakao-2005", UserRole.OWNER))
			.as("what a role must agree to follows the published documents, not a fixed list")
			.isEmpty();
	}

	@Test
	void signup_stillCreatesTheAccountWhenNoTermsArePublished() throws Exception {
		appDataCleaner.clear();

		Long userId = signUpAgreeing(UserRole.CONSUMER, "token-terms-4", "kakao-2004", false);

		assertThat(agreedDocuments(userId)).isEmpty();
	}

	private Long signUpAgreeing(UserRole role, String kakaoToken, String providerId, boolean marketingOptIn)
			throws Exception {
		String signupToken = signupTokenFor(role, kakaoToken, providerId, "약관동의자");
		mockMvc.perform(post("/auth/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"signupToken":"%s","serviceTermsAgreed":true,"privacyTermsAgreed":true,\
					"locationTermsAgreed":true,"thirdPartyTermsAgreed":true,"marketingOptIn":%b}"""
					.formatted(signupToken, marketingOptIn)))
			.andExpect(status().isCreated());
		return storedUser(providerId, role).orElseThrow().getId();
	}

	private void publishOwnerTerms() {
		publish(UserRole.OWNER, TermsType.SERVICE, 1, TermsRequirement.REQUIRED);
		publish(UserRole.OWNER, TermsType.PRIVACY_COLLECTION, 1, TermsRequirement.REQUIRED);
		publish(UserRole.OWNER, TermsType.MARKETING, 1, TermsRequirement.OPTIONAL);
		publish(UserRole.OWNER, TermsType.PRIVACY_POLICY, 1, TermsRequirement.NOTICE);
	}

	private void publishConsumerTerms() {
		publish(UserRole.CONSUMER, TermsType.SERVICE, 1, TermsRequirement.REQUIRED);
		publish(UserRole.CONSUMER, TermsType.PRIVACY_COLLECTION, 1, TermsRequirement.REQUIRED);
		publish(UserRole.CONSUMER, TermsType.LOCATION, 1, TermsRequirement.REQUIRED);
		publish(UserRole.CONSUMER, TermsType.THIRD_PARTY, 1, TermsRequirement.REQUIRED);
		publish(UserRole.CONSUMER, TermsType.MARKETING, 1, TermsRequirement.OPTIONAL);
		publish(UserRole.CONSUMER, TermsType.PRIVACY_POLICY, 1, TermsRequirement.NOTICE);
	}

	private void publish(UserRole role, TermsType type, int version, TermsRequirement requirement) {
		termsDocumentRepository.saveAndFlush(
			new TermsDocument(role, type, version, type + " v" + version, requirement, "본문", null));
	}

	private List<String> agreedDocuments(Long userId) {
		return jdbcTemplate.queryForList("""
				select d.type || ' v' || d.version from user_terms_agreements a
				join terms_documents d on d.id = a.terms_document_id
				where a.user_id = ?""", String.class, userId);
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

		private final Map<String, String> authorizationCodes = new ConcurrentHashMap<>();

		@Override
		public Identity fetchIdentity(UserRole role, String kakaoAccessToken) {
			Identity identity = identities.get(key(role, kakaoAccessToken));
			if (identity == null) {
				throw new BusinessException(UserAuthErrorCode.INVALID_OAUTH_TOKEN);
			}
			return identity;
		}

		void registerCode(UserRole role, String code, String redirectUri, String kakaoAccessToken) {
			authorizationCodes.put(codeKey(role, code, redirectUri), kakaoAccessToken);
		}

		@Override
		public String exchangeAuthorizationCode(UserRole role, String code, String redirectUri) {
			String kakaoAccessToken = authorizationCodes.get(codeKey(role, code, redirectUri));
			if (kakaoAccessToken == null) {
				throw new BusinessException(UserAuthErrorCode.INVALID_OAUTH_TOKEN);
			}
			return kakaoAccessToken;
		}

		private static String key(UserRole role, String kakaoAccessToken) {
			return role.name() + ":" + kakaoAccessToken;
		}

		private static String codeKey(UserRole role, String code, String redirectUri) {
			return role.name() + ":" + code + ":" + redirectUri;
		}
	}
}
