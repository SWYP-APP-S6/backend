package com.swyp.backend.admin.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.swyp.backend.AppDataCleaner;
import com.swyp.backend.RedisTestcontainersConfiguration;
import com.swyp.backend.TestcontainersConfiguration;
import com.swyp.backend.admin.entity.Admin;
import com.swyp.backend.admin.entity.AdminType;
import com.swyp.backend.admin.repository.AdminRepository;
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, RedisTestcontainersConfiguration.class})
class AdminUserDetailControllerTest {

	private static final String EMAIL = "user-detail-admin@swyp.test";
	private static final String PASSWORD = "user-detail-admin-1234";
	private static final String FCM_TOKEN = "fcm-token-abcdefgh-XYZ12345";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	AppDataCleaner appDataCleaner;

	@Autowired
	AdminRepository adminRepository;

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
	HoldCancelCreditRepository creditRepository;

	@Autowired
	HoldCancelCreditEventRepository creditEventRepository;

	@Autowired
	NotificationRepository notificationRepository;

	@Autowired
	UserDeviceTokenRepository deviceTokenRepository;

	@Autowired
	TermsDocumentRepository termsDocumentRepository;

	@Autowired
	UserTermsAgreementRepository agreementRepository;

	@Autowired
	PasswordEncoder passwordEncoder;

	private User consumer;
	private User owner;
	private Store store;
	private Hold canceled;

	@BeforeEach
	void setUp() {
		appDataCleaner.clear();
		if (adminRepository.findByEmail(EMAIL).isEmpty()) {
			adminRepository.save(
					new Admin(EMAIL, "User Detail Admin", AdminType.SUPER, passwordEncoder.encode(PASSWORD)));
		}
		Instant now = Instant.now();
		consumer = userRepository.saveAndFlush(
				new User(UserRole.CONSUMER, "소비자", "01011112222", true, now));
		userLocationRepository.saveAndFlush(new UserLocation(
				consumer, null, "망원동", new BigDecimal("37.556000"), new BigDecimal("126.901000")));
		owner = userRepository.saveAndFlush(new User(UserRole.OWNER, "점주", null, false, now));
		Store created = new Store(
				owner, "청과마을", "04524", "서울 마포구 망원로 12", null, "0212345678",
				new BigDecimal("37.556000"), new BigDecimal("126.901000"),
				LocalTime.of(0, 0), LocalTime.of(23, 59));
		created.replaceCategories(Set.of(StoreCategory.FRUIT));
		created.replaceBusinessDays(EnumSet.allOf(DayOfWeek.class));
		created.approve();
		store = storeRepository.saveAndFlush(created);
		Product peach = productRepository.saveAndFlush(new Product(
				store, "복숭아 4입", ProductCategory.FRUIT, 10, 10_000, 4_000,
				LocalDateTime.now().minusHours(1), LocalDateTime.now().plusHours(5),
				"https://cdn.example.com/p.jpg"));

		Hold completed = holdRepository.saveAndFlush(
				HoldFixture.hold(consumer, peach, 2, now.plusSeconds(600)));
		completed.complete(now);
		holdRepository.saveAndFlush(completed);
		canceled = holdRepository.saveAndFlush(
				HoldFixture.hold(consumer, peach, 1, now.plusSeconds(600)));
		canceled.cancelByUser(now);
		holdRepository.saveAndFlush(canceled);

		creditRepository.saveAndFlush(new HoldCancelCredit(consumer, 2, now));
		creditEventRepository.saveAndFlush(new HoldCancelCreditEvent(
				consumer, canceled, HoldCancelCreditReason.CANCEL, -1, now));

		notificationRepository.saveAndFlush(new Notification(
				consumer, NotificationType.PICKUP_COMPLETED, "수령이 완료됐어요", "청과마을 수령이 완료됐어요.",
				"mangro://holds/1"));
		deviceTokenRepository.saveAndFlush(
				new UserDeviceToken(consumer, DevicePlatform.ANDROID, FCM_TOKEN, now));

		TermsDocument service = termsDocumentRepository.saveAndFlush(new TermsDocument(
				UserRole.CONSUMER, TermsType.SERVICE, 1, "서비스 이용약관", TermsRequirement.REQUIRED,
				"본문", LocalDate.of(2026, 9, 16)));
		agreementRepository.saveAndFlush(new UserTermsAgreement(consumer, service, now));
	}

	@AfterEach
	void tearDown() {
		appDataCleaner.clear();
	}

	private String accessToken() throws Exception {
		String body = mockMvc.perform(post("/admin/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"%s","password":"%s"}""".formatted(EMAIL, PASSWORD)))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.data.accessToken");
	}

	@Test
	void userDetail_withoutToken_isNotReadable() throws Exception {
		mockMvc.perform(get("/admin/users/{id}", consumer.getId())).andExpect(status().isUnauthorized());
	}

	@Test
	void aConsumerDetailGathersEverythingTheSupportDeskAsksAbout() throws Exception {
		mockMvc.perform(get("/admin/users/{id}", consumer.getId())
						.header("Authorization", "Bearer " + accessToken()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.nickname").value("소비자"))
				.andExpect(jsonPath("$.data.phone").value("01011112222"))
				.andExpect(jsonPath("$.data.location.regionName").value("망원동"))
				.andExpect(jsonPath("$.data.store").doesNotExist())
				.andExpect(jsonPath("$.data.cancelCredits.credits").value(2))
				.andExpect(jsonPath("$.data.cancelCredits.max").value(3))
				.andExpect(jsonPath("$.data.cancelCredits.events[0].reason").value("CANCEL"))
				.andExpect(jsonPath("$.data.cancelCredits.events[0].delta").value(-1))
				.andExpect(jsonPath("$.data.cancelCredits.events[0].holdId").value(canceled.getId()))
				.andExpect(jsonPath("$.data.holds.completed").value(1))
				.andExpect(jsonPath("$.data.holds.canceledByUser").value(1))
				.andExpect(jsonPath("$.data.holds.holding").value(0))
				.andExpect(jsonPath("$.data.notifications.unreadCount").value(1))
				.andExpect(jsonPath("$.data.notifications.recent[0].type").value("PICKUP_COMPLETED"))
				.andExpect(jsonPath("$.data.notifications.recent[0].pushState").value("PENDING"))
				.andExpect(jsonPath("$.data.deviceTokens[0].platform").value("ANDROID"))
				.andExpect(jsonPath("$.data.deviceTokens[0].tokenSuffix").value("…XYZ12345"))
				.andExpect(jsonPath("$.data.termsAgreements[0].type").value("SERVICE"))
				.andExpect(jsonPath("$.data.termsAgreements[0].version").value(1));
	}

	@Test
	void anOwnerDetailCarriesTheStore_andAnUnknownIdIs404() throws Exception {
		String token = accessToken();

		mockMvc.perform(get("/admin/users/{id}", owner.getId()).header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.role").value("OWNER"))
				.andExpect(jsonPath("$.data.store.id").value(store.getId()))
				.andExpect(jsonPath("$.data.store.status").value("APPROVED"))
				.andExpect(jsonPath("$.data.cancelCredits.credits").value(3))
				.andExpect(jsonPath("$.data.cancelCredits.events").isEmpty());

		mockMvc.perform(get("/admin/users/{id}", 999_999).header("Authorization", "Bearer " + token))
				.andExpect(status().isNotFound());
	}

	@Test
	void anAdminCanGiveACreditBack_andItLandsInTheLedger_butNotPastTheCap() throws Exception {
		String token = accessToken();

		mockMvc.perform(post("/admin/users/{id}/cancel-credits", consumer.getId())
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"delta":1}"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.credits").value(3))
				.andExpect(jsonPath("$.data.nextRefillAt").doesNotExist());

		assertThat(creditEventRepository.findAllOfUser(consumer.getId()))
				.hasSize(2)
				.first()
				.satisfies(event -> {
					assertThat(event.getReason()).isEqualTo(HoldCancelCreditReason.ADMIN_ADJUST);
					assertThat(event.getDelta()).isEqualTo((short) 1);
					assertThat(event.getHold()).isNull();
				});

		mockMvc.perform(post("/admin/users/{id}/cancel-credits", consumer.getId())
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"delta":1}"""))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("CREDIT_ADJUST_NO_EFFECT"));

		mockMvc.perform(post("/admin/users/{id}/cancel-credits", consumer.getId())
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"delta":11}"""))
				.andExpect(status().isBadRequest());
	}
}
