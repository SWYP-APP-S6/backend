package com.swyp.backend.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.swyp.backend.AppDataCleaner;
import com.swyp.backend.RedisTestcontainersConfiguration;
import com.swyp.backend.TestcontainersConfiguration;
import com.swyp.backend.common.security.JwtTokenProvider;
import com.swyp.backend.common.security.RefreshTokenService;
import com.swyp.backend.common.security.TokenRealm;
import com.swyp.backend.product.entity.Product;
import com.swyp.backend.product.entity.ProductCategory;
import com.swyp.backend.product.repository.ProductRepository;
import com.swyp.backend.store.entity.Store;
import com.swyp.backend.store.entity.StoreCategory;
import com.swyp.backend.store.repository.StoreRepository;
import com.swyp.backend.user.entity.User;
import com.swyp.backend.user.entity.UserRole;
import com.swyp.backend.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
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
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, RedisTestcontainersConfiguration.class})
class TesterIsolationTest {

	private static final String LATITUDE = "37.556000";
	private static final String LONGITUDE = "126.901000";

	@Autowired
	AppDataCleaner appDataCleaner;

	@Autowired
	MockMvc mockMvc;

	@Autowired
	UserRepository userRepository;

	@Autowired
	StoreRepository storeRepository;

	@Autowired
	ProductRepository productRepository;

	@Autowired
	JwtTokenProvider tokenProvider;

	@Autowired
	RefreshTokenService refreshTokenService;

	@Autowired
	JdbcTemplate jdbcTemplate;

	private User consumer;
	private User testerConsumer;
	private User teammate;
	private User ownerAccount;
	private Store realStore;
	private Store testStore;
	private Product realProduct;
	private Product testProduct;

	@BeforeEach
	void setUp() {
		appDataCleaner.clear();
		consumer = user(UserRole.CONSUMER, "소비자", false);
		testerConsumer = user(UserRole.CONSUMER, "팀원", true);
		teammate = kakaoAccount(UserRole.CONSUMER, "팀원 본계정", "kakao-teammate", false);
		ownerAccount = kakaoAccount(UserRole.OWNER, "팀원 점주", "kakao-teammate-owner", true);
		realStore = store(user(UserRole.OWNER, "점주", false), "청과마을");
		testStore = store(userRepository.saveAndFlush(ownerAccount.newTestAccount(Instant.now())), "테스트가게");
		realProduct = product(realStore, "복숭아 4입");
		testProduct = product(testStore, "테스트 배");
	}

	@AfterEach
	void tearDown() {
		appDataCleaner.clear();
	}

	@Test
	void eachAudienceSeesOnlyItsOwnStoresOnTheMap() throws Exception {
		mockMvc.perform(nearbyStores().header("Authorization", bearer(consumer)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.stores[*].storeId").value(contains(realStore.getId().intValue())));

		mockMvc.perform(nearbyStores().header("Authorization", bearer(testerConsumer)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.stores[*].storeId").value(contains(testStore.getId().intValue())));

		mockMvc.perform(nearbyStores().header("Authorization", guest()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.stores[*].storeId").value(contains(realStore.getId().intValue())));
	}

	@Test
	void eachAudienceSeesOnlyItsOwnProductsNearby() throws Exception {
		mockMvc.perform(nearbyProducts().header("Authorization", bearer(consumer)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.stores.content[*].storeId")
				.value(contains(realStore.getId().intValue())));

		mockMvc.perform(nearbyProducts().header("Authorization", bearer(testerConsumer)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.stores.content[*].storeId")
				.value(contains(testStore.getId().intValue())));

		mockMvc.perform(nearbyProducts().header("Authorization", guest()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.stores.content[*].storeId")
				.value(contains(realStore.getId().intValue())));
	}

	@Test
	void aStoreOfTheOtherAudienceIsNotFound() throws Exception {
		mockMvc.perform(get("/stores/" + testStore.getId() + "/products")
				.header("Authorization", bearer(consumer)))
			.andExpect(status().isNotFound());
		mockMvc.perform(get("/stores/" + realStore.getId() + "/products")
				.header("Authorization", bearer(testerConsumer)))
			.andExpect(status().isNotFound());
		mockMvc.perform(get("/stores/" + testStore.getId() + "/products")
				.header("Authorization", guest()))
			.andExpect(status().isNotFound());

		mockMvc.perform(get("/stores/" + testStore.getId() + "/products")
				.header("Authorization", bearer(testerConsumer)))
			.andExpect(status().isOk());
	}

	@Test
	void aProductOfTheOtherAudienceIsNotFound() throws Exception {
		mockMvc.perform(get("/products/" + testProduct.getId()).header("Authorization", bearer(consumer)))
			.andExpect(status().isNotFound());
		mockMvc.perform(get("/products/" + realProduct.getId()).header("Authorization", bearer(testerConsumer)))
			.andExpect(status().isNotFound());
		mockMvc.perform(get("/products/" + testProduct.getId()).header("Authorization", guest()))
			.andExpect(status().isNotFound());

		mockMvc.perform(get("/products/" + testProduct.getId()).header("Authorization", bearer(testerConsumer)))
			.andExpect(status().isOk());
	}

	@Test
	void anAdminTokenIsNotMistakenForTheAppUserWithTheSameId() throws Exception {
		String adminWithATesterId = "Bearer " + tokenProvider.createAccessToken(
				TokenRealm.ADMIN, testerConsumer.getId(), "SUPER");

		mockMvc.perform(nearbyStores().header("Authorization", adminWithATesterId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.stores[*].storeId").value(contains(realStore.getId().intValue())));
	}

	@Test
	void aTesterCannotHoldARealProductAndTheStockStaysUntouched() throws Exception {
		mockMvc.perform(holdOf(realProduct).header("Authorization", bearer(testerConsumer)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("PRODUCT_NOT_SELLABLE"));
		mockMvc.perform(holdOf(testProduct).header("Authorization", bearer(consumer)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("PRODUCT_NOT_SELLABLE"));

		assertThat(productRepository.findById(realProduct.getId()).orElseThrow().getHeldQty()).isZero();

		mockMvc.perform(holdOf(testProduct).header("Authorization", bearer(testerConsumer)))
			.andExpect(status().isCreated());
	}

	@Test
	void anAllowedTeammateSwitchesIntoTheTestAccountAndBack() throws Exception {
		String oldRefresh = refreshOf(teammate);
		MvcResult on = mockMvc.perform(testMode(true, oldRefresh).header("Authorization", bearer(teammate)))
			.andExpect(status().isOk())
			.andReturn();

		mockMvc.perform(refresh(oldRefresh)).andExpect(status().isUnauthorized());
		Integer testAccountId = JsonPath.read(
			mockMvc.perform(get("/users/me").header("Authorization", access(on)))
				.andExpect(jsonPath("$.data.testMode").value(true))
				.andExpect(jsonPath("$.data.testerAllowed").value(true))
				.andReturn().getResponse().getContentAsString(),
			"$.data.id");
		assertThat(testAccountId.longValue()).isNotEqualTo(teammate.getId());
		mockMvc.perform(nearbyStores().header("Authorization", access(on)))
			.andExpect(jsonPath("$.data.stores[*].storeId").value(contains(testStore.getId().intValue())));

		MvcResult off = mockMvc.perform(testMode(false, refreshOf(on)).header("Authorization", access(on)))
			.andExpect(status().isOk())
			.andReturn();

		mockMvc.perform(get("/users/me").header("Authorization", access(off)))
			.andExpect(jsonPath("$.data.id").value(teammate.getId().intValue()))
			.andExpect(jsonPath("$.data.testMode").value(false));
		mockMvc.perform(nearbyStores().header("Authorization", access(off)))
			.andExpect(jsonPath("$.data.stores[*].storeId").value(contains(realStore.getId().intValue())));

		MvcResult again = mockMvc.perform(testMode(true, refreshOf(off)).header("Authorization", access(off)))
			.andExpect(status().isOk())
			.andReturn();
		mockMvc.perform(get("/users/me").header("Authorization", access(again)))
			.andExpect(jsonPath("$.data.id").value(testAccountId));
	}

	@Test
	void whatIsDoneInTestModeStaysInTheTestAccount() throws Exception {
		MvcResult on = mockMvc.perform(testMode(true, refreshOf(teammate)).header("Authorization", bearer(teammate)))
			.andExpect(status().isOk())
			.andReturn();
		mockMvc.perform(holdOf(testProduct).header("Authorization", access(on)))
			.andExpect(status().isCreated());

		MvcResult off = mockMvc.perform(testMode(false, refreshOf(on)).header("Authorization", access(on)))
			.andExpect(status().isOk())
			.andReturn();

		mockMvc.perform(get("/holds/active").header("Authorization", access(off)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.hold").value(nullValue()));
	}

	@Test
	void withoutThePermissionTheSwitchIsRefusedAndTheSessionSurvives() throws Exception {
		String refresh = refreshOf(consumer);

		mockMvc.perform(testMode(true, refresh).header("Authorization", bearer(consumer)))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("TESTER_NOT_ALLOWED"));

		mockMvc.perform(refresh(refresh)).andExpect(status().isOk());
	}

	@Test
	void theSwitchNeedsTheCallersOwnRefreshToken() throws Exception {
		mockMvc.perform(testMode(true, refreshOf(consumer)).header("Authorization", bearer(teammate)))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
	}

	@Test
	void aTestStoreStaysHiddenWhenItsOwnerLeavesTestMode() throws Exception {
		User testOwner = testStore.getOwner();

		mockMvc.perform(testMode(false, refreshOf(testOwner)).header("Authorization", bearer(testOwner)))
			.andExpect(status().isOk());

		mockMvc.perform(nearbyStores().header("Authorization", bearer(consumer)))
			.andExpect(jsonPath("$.data.stores[*].storeId").value(contains(realStore.getId().intValue())));
		assertThat(userRepository.findById(testOwner.getId()).orElseThrow().isTester()).isTrue();
	}

	@Test
	void aTestStoreStaysHiddenWhenItsOwnerLosesThePermission() throws Exception {
		String testOwnerRefresh = refreshOf(testStore.getOwner());

		mockMvc.perform(permission(ownerAccount, false).header("Authorization", admin()))
			.andExpect(status().isOk());

		mockMvc.perform(nearbyStores().header("Authorization", guest()))
			.andExpect(jsonPath("$.data.stores[*].storeId").value(contains(realStore.getId().intValue())));
		mockMvc.perform(refresh(testOwnerRefresh)).andExpect(status().isUnauthorized());
	}

	@Test
	void aRowNeverChangesSides() {
		assertThatThrownBy(() -> jdbcTemplate.update(
				"update users set tester = false where id = ?", testStore.getOwner().getId()))
			.isInstanceOf(DataAccessException.class);
	}

	@Test
	void aTestAccountTakesNoPermission() throws Exception {
		mockMvc.perform(permission(testerConsumer, true).header("Authorization", admin()))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("TESTER_PERMISSION_ON_TEST_ACCOUNT"));
	}

	@Test
	void onlyAnAdminCanGrantThePermission() throws Exception {
		mockMvc.perform(permission(consumer, true).header("Authorization", bearer(consumer)))
			.andExpect(status().isForbidden());

		assertThat(userRepository.findById(consumer.getId()).orElseThrow().isTesterAllowed()).isFalse();
	}

	private MockHttpServletRequestBuilder nearbyStores() {
		return get("/stores/nearby")
			.param("minLat", "37.550000").param("maxLat", "37.560000")
			.param("minLng", "126.895000").param("maxLng", "126.905000");
	}

	private MockHttpServletRequestBuilder nearbyProducts() {
		return get("/products/nearby").param("lat", LATITUDE).param("lng", LONGITUDE);
	}

	private MockHttpServletRequestBuilder holdOf(Product product) {
		return post("/holds")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"productId\":" + product.getId() + ",\"qty\":1}");
	}

	private MockHttpServletRequestBuilder permission(User user, boolean allowed) {
		return patch("/admin/users/" + user.getId() + "/tester-permission")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"allowed\":" + allowed + "}");
	}

	private MockHttpServletRequestBuilder testMode(boolean on, String refreshToken) {
		return patch("/users/me/test-mode")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"on\":" + on + ",\"refreshToken\":\"" + refreshToken + "\"}");
	}

	private MockHttpServletRequestBuilder refresh(String refreshToken) {
		return post("/auth/refresh")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"refreshToken\":\"" + refreshToken + "\"}");
	}

	private String refreshOf(User user) {
		return refreshTokenService.issue(TokenRealm.USER, user.getId());
	}

	private static String refreshOf(MvcResult tokens) throws Exception {
		return JsonPath.read(tokens.getResponse().getContentAsString(), "$.data.refreshToken");
	}

	private static String access(MvcResult tokens) throws Exception {
		return "Bearer " + JsonPath.read(tokens.getResponse().getContentAsString(), "$.data.accessToken");
	}

	private String admin() {
		return "Bearer " + tokenProvider.createAccessToken(TokenRealm.ADMIN, 1L, "SUPER");
	}

	private String bearer(User user) {
		return "Bearer " + tokenProvider.createAccessToken(
				TokenRealm.USER, user.getId(), user.getRole().name());
	}

	private String guest() {
		return "Bearer " + tokenProvider.createAccessToken(TokenRealm.GUEST, consumer.getId(), "GUEST");
	}

	private User user(UserRole role, String nickname, boolean tester) {
		User user = tester
				? User.testAccount(role, nickname, null, false, Instant.now())
				: new User(role, nickname, null, false, Instant.now());
		return userRepository.saveAndFlush(user);
	}

	private User kakaoAccount(UserRole role, String nickname, String kakaoId, boolean inTestMode) {
		User account = new User(role, nickname, null, false, Instant.now());
		account.linkOauthAccount("kakao", kakaoId);
		account.allowTesting();
		if (inTestMode) {
			account.enterTestMode();
		}
		return userRepository.saveAndFlush(account);
	}

	private Store store(User owner, String name) {
		Store store = new Store(
				owner, name, "04524", "서울 마포구 망원로 12", "1층", "02-1234-5678",
				new BigDecimal(LATITUDE), new BigDecimal(LONGITUDE),
				LocalTime.of(0, 0), LocalTime.of(23, 59));
		store.replaceCategories(Set.of(StoreCategory.FRUIT));
		store.replaceBusinessDays(EnumSet.allOf(DayOfWeek.class));
		store.approve();
		return storeRepository.saveAndFlush(store);
	}

	private Product product(Store store, String name) {
		LocalDateTime pickupEndAt = LocalDateTime.now().plusHours(5);
		return productRepository.saveAndFlush(new Product(
				store, name, ProductCategory.FRUIT, 3, 10_000, 4_000,
				pickupEndAt.minusHours(1), pickupEndAt, "https://cdn.example.com/" + name + ".jpg"));
	}
}
