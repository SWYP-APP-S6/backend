package com.swyp.backend.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.swyp.backend.AppDataCleaner;
import com.swyp.backend.RedisTestcontainersConfiguration;
import com.swyp.backend.TestcontainersConfiguration;
import com.swyp.backend.analytics.entity.DomainEvent;
import com.swyp.backend.analytics.entity.DomainEventType;
import com.swyp.backend.analytics.repository.DomainEventRepository;
import com.swyp.backend.common.security.JwtTokenProvider;
import com.swyp.backend.common.security.RefreshTokenService;
import com.swyp.backend.common.security.TokenRealm;
import com.swyp.backend.hold.HoldFixture;
import com.swyp.backend.hold.entity.Hold;
import com.swyp.backend.hold.entity.HoldCancelCredit;
import com.swyp.backend.hold.entity.HoldCancelCreditEvent;
import com.swyp.backend.hold.entity.HoldCancelCreditReason;
import com.swyp.backend.hold.repository.HoldCancelCreditEventRepository;
import com.swyp.backend.hold.repository.HoldCancelCreditRepository;
import com.swyp.backend.hold.repository.HoldRepository;
import com.swyp.backend.notification.entity.DevicePlatform;
import com.swyp.backend.notification.entity.Notification;
import com.swyp.backend.notification.entity.NotificationType;
import com.swyp.backend.notification.entity.UserDeviceToken;
import com.swyp.backend.notification.repository.NotificationRepository;
import com.swyp.backend.notification.repository.UserDeviceTokenRepository;
import com.swyp.backend.product.entity.Product;
import com.swyp.backend.product.entity.ProductCategory;
import com.swyp.backend.product.repository.ProductRepository;
import com.swyp.backend.store.entity.Store;
import com.swyp.backend.store.entity.StoreCategory;
import com.swyp.backend.store.repository.StoreRepository;
import com.swyp.backend.terms.entity.TermsDocument;
import com.swyp.backend.terms.entity.TermsRequirement;
import com.swyp.backend.terms.entity.TermsType;
import com.swyp.backend.terms.entity.UserTermsAgreement;
import com.swyp.backend.terms.repository.TermsDocumentRepository;
import com.swyp.backend.terms.repository.UserTermsAgreementRepository;
import com.swyp.backend.user.entity.User;
import com.swyp.backend.user.entity.UserLocation;
import com.swyp.backend.user.entity.UserRole;
import com.swyp.backend.user.repository.UserLocationRepository;
import com.swyp.backend.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, RedisTestcontainersConfiguration.class})
class UserDeletionControllerTest {

	@Autowired
	AppDataCleaner appDataCleaner;

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	JwtTokenProvider tokenProvider;

	@Autowired
	RefreshTokenService refreshTokenService;

	@Autowired
	UserRepository userRepository;

	@Autowired
	UserLocationRepository userLocationRepository;

	@Autowired
	StoreRepository storeRepository;

	@Autowired
	ProductRepository productRepository;

	@Autowired
	HoldRepository holdRepository;

	@Autowired
	HoldCancelCreditRepository holdCancelCreditRepository;

	@Autowired
	HoldCancelCreditEventRepository holdCancelCreditEventRepository;

	@Autowired
	NotificationRepository notificationRepository;

	@Autowired
	UserDeviceTokenRepository userDeviceTokenRepository;

	@Autowired
	TermsDocumentRepository termsDocumentRepository;

	@Autowired
	UserTermsAgreementRepository userTermsAgreementRepository;

	@Autowired
	DomainEventRepository domainEventRepository;

	@BeforeEach
	void setUp() {
		appDataCleaner.clear();
	}

	@AfterEach
	void tearDown() {
		appDataCleaner.clear();
	}

	@Test
	void deleteMe_removesTheConsumerAndEveryRowThatPointsAtThem() throws Exception {
		User consumer = user(UserRole.CONSUMER, "탈퇴할소비자");
		Hold pickedUp = pickedUpHold(consumer, product(store(user(UserRole.OWNER, "남의점주"))));
		attachEverything(consumer, pickedUp);

		mockMvc.perform(delete("/users/me").header("Authorization", bearer(consumer)))
			.andExpect(status().isOk());

		assertThat(userRepository.existsById(consumer.getId())).isFalse();
		assertThat(tablesStillReferencing(consumer.getId())).isEmpty();
		assertThat(holdRepository.existsById(pickedUp.getId())).isFalse();
	}

	@Test
	void deleteMe_ofAnOwner_takesTheStoreItsProductsAndTheHoldsPlacedThere_butNotTheConsumers()
			throws Exception {
		User owner = user(UserRole.OWNER, "탈퇴할점주");
		Store store = store(owner);
		Product product = product(store);
		User consumer = user(UserRole.CONSUMER, "단골손님");
		Hold pickedUp = pickedUpHold(consumer, product);
		holdCancelCreditEventRepository.saveAndFlush(new HoldCancelCreditEvent(
			consumer, pickedUp, HoldCancelCreditReason.CANCEL, -1, Instant.now()));
		domainEventRepository.saveAndFlush(DomainEvent.builder()
			.eventType(DomainEventType.PICKUP_COMPLETE)
			.userId(consumer.getId())
			.storeId(store.getId())
			.productId(product.getId())
			.holdId(pickedUp.getId())
			.build());

		mockMvc.perform(delete("/users/me").header("Authorization", bearer(owner)))
			.andExpect(status().isOk());

		assertThat(userRepository.existsById(owner.getId())).isFalse();
		assertThat(storeRepository.existsById(store.getId())).isFalse();
		assertThat(productRepository.existsById(product.getId())).isFalse();
		assertThat(holdRepository.existsById(pickedUp.getId())).isFalse();
		assertThat(count("select count(*) from store_categories where store_id = ?", store.getId())).isZero();
		assertThat(count("select count(*) from store_business_days where store_id = ?", store.getId())).isZero();
		assertThat(holdCancelCreditEventRepository.count()).isZero();
		assertThat(domainEventRepository.count()).isZero();
		assertThat(userRepository.existsById(consumer.getId())).isTrue();
	}

	@Test
	void deleteMe_isRefused_whileTheConsumerStillHoldsSomething() throws Exception {
		User consumer = user(UserRole.CONSUMER, "찜중인소비자");
		Hold holding = holding(consumer, product(store(user(UserRole.OWNER, "남의점주"))));

		mockMvc.perform(delete("/users/me").header("Authorization", bearer(consumer)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("HOLDING_HOLDS_REMAIN"));

		assertThat(userRepository.existsById(consumer.getId())).isTrue();
		assertThat(holdRepository.existsById(holding.getId())).isTrue();
	}

	@Test
	void deleteMe_isRefused_whileTheOwnersStoreHasAHoldToHandle() throws Exception {
		User owner = user(UserRole.OWNER, "찜받은점주");
		Store store = store(owner);
		holding(user(UserRole.CONSUMER, "기다리는손님"), product(store));

		mockMvc.perform(delete("/users/me").header("Authorization", bearer(owner)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("STORE_HOLDING_HOLDS_REMAIN"));

		assertThat(userRepository.existsById(owner.getId())).isTrue();
		assertThat(storeRepository.existsById(store.getId())).isTrue();
	}

	@Test
	void deletedUser_cannotRefreshTheTokenTheyStillHold() throws Exception {
		User consumer = user(UserRole.CONSUMER, "토큰남은소비자");
		String refreshToken = refreshTokenService.issue(TokenRealm.USER, consumer.getId());

		mockMvc.perform(delete("/users/me").header("Authorization", bearer(consumer)))
			.andExpect(status().isOk());

		mockMvc.perform(post("/auth/refresh")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"refreshToken":"%s"}""".formatted(refreshToken)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
	}

	@Test
	void adminDeleteUser_removesTheUser() throws Exception {
		User owner = user(UserRole.OWNER, "관리자가지울점주");
		store(owner);

		mockMvc.perform(delete("/admin/users/{id}", owner.getId()).header("Authorization", adminBearer()))
			.andExpect(status().isOk());

		assertThat(userRepository.existsById(owner.getId())).isFalse();
		assertThat(storeRepository.count()).isZero();
	}

	@Test
	void adminDeleteUser_ofAnUnknownId_isNotFound() throws Exception {
		mockMvc.perform(delete("/admin/users/{id}", 999_999L).header("Authorization", adminBearer()))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
	}

	@Test
	void adminDeleteUser_isNotReachableWithAnAppUserToken() throws Exception {
		User consumer = user(UserRole.CONSUMER, "남을지우려는소비자");
		User victim = user(UserRole.CONSUMER, "피해자");

		mockMvc.perform(delete("/admin/users/{id}", victim.getId()).header("Authorization", bearer(consumer)))
			.andExpect(status().isForbidden());

		assertThat(userRepository.existsById(victim.getId())).isTrue();
	}

	private void attachEverything(User user, Hold hold) {
		Instant now = Instant.now();
		userLocationRepository.saveAndFlush(new UserLocation(
			user, null, "역삼동", new BigDecimal("37.500000"), new BigDecimal("127.030000")));
		notificationRepository.saveAndFlush(
			new Notification(user, NotificationType.HOLD_CREATED, "제목", "본문", null));
		userDeviceTokenRepository.saveAndFlush(
			new UserDeviceToken(user, DevicePlatform.ANDROID, "fcm-" + user.getId(), now));
		holdCancelCreditRepository.saveAndFlush(new HoldCancelCredit(user, 3, now));
		holdCancelCreditEventRepository.saveAndFlush(
			new HoldCancelCreditEvent(user, hold, HoldCancelCreditReason.CANCEL, -1, now));
		TermsDocument document = termsDocumentRepository.saveAndFlush(new TermsDocument(
			user.getRole(), TermsType.SERVICE, 1, "서비스 이용약관", TermsRequirement.REQUIRED,
			"본문", LocalDate.of(2026, 9, 16)));
		userTermsAgreementRepository.saveAndFlush(new UserTermsAgreement(user, document, now));
		domainEventRepository.saveAndFlush(DomainEvent.builder()
			.eventType(DomainEventType.APP_OPEN)
			.userId(user.getId())
			.payload(Map.of())
			.build());
	}

	private List<String> tablesStillReferencing(Long userId) {
		List<String> tables = List.of(
			"user_locations", "notifications", "user_device_tokens", "hold_cancel_credits",
			"hold_cancel_credit_events", "user_terms_agreements", "domain_events", "holds",
			"recipe_feedback");
		return tables.stream()
			.filter(table -> count("select count(*) from " + table + " where user_id = ?", userId) > 0)
			.toList();
	}

	private long count(String sql, Long id) {
		return jdbcTemplate.queryForObject(sql, Long.class, id);
	}

	private User user(UserRole role, String nickname) {
		return userRepository.saveAndFlush(new User(role, nickname, null, false, Instant.now()));
	}

	private Store store(User owner) {
		Store store = new Store(
			owner, owner.getNickname() + "네 가게", "04524", "서울특별시 강남구 역삼로 1", null, "021234567",
			new BigDecimal("37.500000"), new BigDecimal("127.030000"),
			LocalTime.of(9, 0), LocalTime.of(21, 0));
		store.replaceCategories(List.of(StoreCategory.FRUIT));
		store.replaceBusinessDays(List.of(DayOfWeek.MONDAY));
		store.approve();
		return storeRepository.saveAndFlush(store);
	}

	private Product product(Store store) {
		return productRepository.saveAndFlush(new Product(
			store, "복숭아 4입", ProductCategory.FRUIT, 10, 1000, 800,
			LocalDateTime.now(), LocalDateTime.now().plusHours(1), "https://example.com/a.jpg"));
	}

	private Hold holding(User consumer, Product product) {
		return holdRepository.saveAndFlush(
			HoldFixture.hold(consumer, product, 1, Instant.now().plus(Duration.ofMinutes(15))));
	}

	private Hold pickedUpHold(User consumer, Product product) {
		Hold hold = HoldFixture.hold(consumer, product, 1, Instant.now().plus(Duration.ofMinutes(15)));
		hold.complete(Instant.now());
		return holdRepository.saveAndFlush(hold);
	}

	private String bearer(User user) {
		return "Bearer " + tokenProvider.createAccessToken(
			TokenRealm.USER, user.getId(), user.getRole().name());
	}

	private String adminBearer() {
		return "Bearer " + tokenProvider.createAccessToken(TokenRealm.ADMIN, 1L, "SUPER");
	}
}
