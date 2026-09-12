package com.swyp.backend.product.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.swyp.backend.RedisTestcontainersConfiguration;
import com.swyp.backend.TestcontainersConfiguration;
import com.swyp.backend.common.security.JwtTokenProvider;
import com.swyp.backend.common.security.TokenRealm;
import com.swyp.backend.hold.repository.HoldRepository;
import com.swyp.backend.notification.repository.NotificationRepository;
import com.swyp.backend.product.entity.Product;
import com.swyp.backend.product.entity.ProductCategory;
import com.swyp.backend.product.repository.ProductRepository;
import com.swyp.backend.store.entity.Store;
import com.swyp.backend.store.repository.StoreRepository;
import com.swyp.backend.user.entity.User;
import com.swyp.backend.user.entity.UserRole;
import com.swyp.backend.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.EnumSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, RedisTestcontainersConfiguration.class})
@Transactional
class ProductControllerTest {

	private static final String ORIGIN_LATITUDE = "37.556000";
	private static final String ORIGIN_LONGITUDE = "126.901000";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	UserRepository userRepository;

	@Autowired
	StoreRepository storeRepository;

	@Autowired
	ProductRepository productRepository;

	@Autowired
	HoldRepository holdRepository;

	@Autowired
	NotificationRepository notificationRepository;

	@Autowired
	JwtTokenProvider tokenProvider;

	@Autowired
	Clock clock;

	private LocalDateTime now;

	@BeforeEach
	void setUp() {
		holdRepository.deleteAll();
		notificationRepository.deleteAll();
		productRepository.deleteAll();
		storeRepository.deleteAll();
		userRepository.deleteAll();
		now = LocalDateTime.now();
	}

	private String bearer(TokenRealm realm, String role) {
		return "Bearer " + tokenProvider.createAccessToken(realm, 1L, role);
	}

	private Store approvedStore(String name, String latitude, String longitude) {
		return store(name, latitude, longitude, true);
	}

	private Store store(String name, String latitude, String longitude, boolean approved) {
		User owner = userRepository.saveAndFlush(
				new User(UserRole.OWNER, name + "사장", null, false, Instant.now()));
		Store store = new Store(
				owner,
				name,
				"04524",
				"서울시 마포구 망원동 1",
				"1층",
				"02-1234-5678",
				new BigDecimal(latitude),
				new BigDecimal(longitude),
				LocalTime.of(9, 0),
				LocalTime.of(21, 0));
		store.replaceBusinessDays(EnumSet.allOf(DayOfWeek.class));
		if (approved) {
			store.approve();
		}
		return storeRepository.saveAndFlush(store);
	}

	private Store storeClosedToday(String name, String latitude, String longitude) {
		Store store = approvedStore(name, latitude, longitude);
		store.replaceBusinessDays(
				EnumSet.complementOf(EnumSet.of(LocalDate.now(clock).getDayOfWeek())));
		return storeRepository.saveAndFlush(store);
	}

	private Product product(
			Store store, String name, ProductCategory category, LocalDateTime pickupEndAt) {
		return productRepository.saveAndFlush(new Product(
				store, name, category, 3, 10_000, 4_000, pickupEndAt.minusHours(1), pickupEndAt,
				"https://cdn.example.com/" + name + ".jpg"));
	}

	private void seedTwoNearbyStores() {
		Store fruitVillage = approvedStore("청과마을", "37.560492", "126.901000");
		Store hydroponics = approvedStore("수경야채", "37.556000", "126.910064");
		product(fruitVillage, "복숭아 4입", ProductCategory.FRUIT, now.plusHours(3));
		product(fruitVillage, "알배기 배추 2통", ProductCategory.VEGETABLE, now.plusHours(4));
		product(fruitVillage, "대파 1단", ProductCategory.VEGETABLE, now.plusHours(5));
		product(hydroponics, "양파 1.5kg", ProductCategory.VEGETABLE, now.plusHours(1));
	}

	private org.springframework.test.web.servlet.ResultActions browse(String query) throws Exception {
		return mockMvc.perform(get("/products/nearby?lat=" + ORIGIN_LATITUDE
						+ "&lng=" + ORIGIN_LONGITUDE + query)
				.header("Authorization", bearer(TokenRealm.GUEST, "GUEST")));
	}

	@Test
	void aStoreBeyondTheDefaultRadiusShowsUpOnlyWhenTheCallerWidensIt() throws Exception {
		Store farAway = approvedStore("먼가게", "37.583000", "126.901000");
		product(farAway, "고구마 1kg", ProductCategory.VEGETABLE, now.plusHours(3));

		browse("")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.totalProductCount").value(0));

		browse("&radiusMeters=5000")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.totalProductCount").value(1))
			.andExpect(jsonPath("$.data.stores.content[0].storeName").value("먼가게"));
	}

	@Test
	void aRadiusOutsideTheAllowedRangeIsRejected() throws Exception {
		browse("&radiusMeters=99999")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

		browse("&radiusMeters=10")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}

	@Test
	void aStoreThatIsClosedTodayKeepsItsProductsOutOfTheList() throws Exception {
		seedTwoNearbyStores();
		Store restingToday = storeClosedToday("오늘휴무", "37.556500", "126.901500");
		product(restingToday, "감자 1kg", ProductCategory.VEGETABLE, now.plusHours(3));

		browse("")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.totalProductCount").value(4))
			.andExpect(jsonPath("$.data.stores.content.length()").value(2))
			.andExpect(jsonPath("$.data.stores.content[*].storeName")
				.value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("오늘휴무"))));
	}

	@Test
	void nearbyProducts_areGroupedByStoreAndOrderedByDistance() throws Exception {
		seedTwoNearbyStores();

		browse("")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.totalProductCount").value(4))
			.andExpect(jsonPath("$.data.stores.content.length()").value(2))
			.andExpect(jsonPath("$.data.stores.content[0].storeName").value("청과마을"))
			.andExpect(jsonPath("$.data.stores.content[0].productCount").value(3))
			.andExpect(jsonPath("$.data.stores.content[0].hasMoreProducts").value(false))
			.andExpect(jsonPath("$.data.stores.content[0].walkingMinutes").value(8))
			.andExpect(jsonPath("$.data.stores.content[1].storeName").value("수경야채"));
	}

	@Test
	void nearbyProducts_orderStoresByTheirEarliestPickupDeadline() throws Exception {
		seedTwoNearbyStores();

		browse("&sort=PICKUP_DEADLINE")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.stores.content[0].storeName").value("수경야채"))
			.andExpect(jsonPath("$.data.stores.content[1].storeName").value("청과마을"));
	}

	@Test
	void categoryFilter_narrowsProductsAndTheStoresDeadline() throws Exception {
		seedTwoNearbyStores();

		browse("&category=VEGETABLE&sort=PICKUP_DEADLINE")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.totalProductCount").value(3))
			.andExpect(jsonPath("$.data.stores.content[0].storeName").value("수경야채"))
			.andExpect(jsonPath("$.data.stores.content[1].storeName").value("청과마을"))
			.andExpect(jsonPath("$.data.stores.content[1].productCount").value(2))
			.andExpect(jsonPath("$.data.stores.content[1].products[0].name").value("알배기 배추 2통"));
	}

	@Test
	void storesInsideTheBoundingBoxButOutsideTheRadius_areExcluded() throws Exception {
		Store corner = approvedStore("모서리상회", "37.564983", "126.912330");
		product(corner, "감자 1kg", ProductCategory.VEGETABLE, now.plusHours(2));

		browse("")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.totalProductCount").value(0))
			.andExpect(jsonPath("$.data.stores.content.length()").value(0));
	}

	@Test
	void unsellableProductsAndUnapprovedStores_areExcluded() throws Exception {
		Store approved = approvedStore("정상가게", "37.560492", "126.901000");
		Store pending = store("미승인가게", "37.556000", "126.905000", false);
		product(pending, "숨은 상품", ProductCategory.VEGETABLE, now.plusHours(2));
		product(approved, "마감된 상품", ProductCategory.VEGETABLE, now.minusMinutes(1));
		Product soldOut = product(approved, "품절 상품", ProductCategory.VEGETABLE, now.plusHours(2));
		soldOut.adjustAvailableQty(0);
		productRepository.saveAndFlush(soldOut);
		product(approved, "판매중 상품", ProductCategory.VEGETABLE, now.plusHours(2));

		browse("")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.totalProductCount").value(1))
			.andExpect(jsonPath("$.data.stores.content.length()").value(1))
			.andExpect(jsonPath("$.data.stores.content[0].storeName").value("정상가게"))
			.andExpect(jsonPath("$.data.stores.content[0].products[0].name").value("판매중 상품"));
	}

	@Test
	void browsing_withoutCoordinates_failsValidation() throws Exception {
		mockMvc.perform(get("/products/nearby")
				.header("Authorization", bearer(TokenRealm.GUEST, "GUEST")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}

	@Test
	void nearbyProducts_areBrowsableByConsumersAndAdmins_butNotOwners() throws Exception {
		seedTwoNearbyStores();
		String url = "/products/nearby?lat=" + ORIGIN_LATITUDE + "&lng=" + ORIGIN_LONGITUDE;

		mockMvc.perform(get(url).header("Authorization", bearer(TokenRealm.USER, "CONSUMER")))
			.andExpect(status().isOk());
		mockMvc.perform(get(url).header("Authorization", bearer(TokenRealm.ADMIN, "SUPER")))
			.andExpect(status().isOk());
		mockMvc.perform(get(url).header("Authorization", bearer(TokenRealm.USER, "OWNER")))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("FORBIDDEN"));
	}

	@Test
	void browsing_withoutAToken_isUnauthorized() throws Exception {
		mockMvc.perform(get("/products/nearby?lat=" + ORIGIN_LATITUDE + "&lng=" + ORIGIN_LONGITUDE))
			.andExpect(status().isUnauthorized());
	}
}
