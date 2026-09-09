package com.swyp.backend.store.controller;

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
class StoreMapControllerTest {

	private static final String BOUNDS = "minLat=37.55&maxLat=37.57&minLng=126.89&maxLng=126.91";

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

	private Store store(String name, String latitude, String longitude, boolean approved) {
		User owner = userRepository.saveAndFlush(
				new User(UserRole.OWNER, name + "사장", null, false, Instant.now()));
		Store store = new Store(
				owner, name, "04524", "서울시 마포구 망원동 1", "1층", "02-1234-5678",
				new BigDecimal(latitude), new BigDecimal(longitude),
				LocalTime.of(9, 0), LocalTime.of(21, 0));
		store.replaceCategories(Set.of(StoreCategory.VEGETABLE));
		store.replaceBusinessDays(EnumSet.allOf(DayOfWeek.class));
		if (approved) {
			store.approve();
		}
		return storeRepository.saveAndFlush(store);
	}

	private void product(Store store, String name, LocalDateTime pickupEndAt) {
		productRepository.saveAndFlush(new Product(
				store, name, ProductCategory.VEGETABLE, 3, 10_000, 4_000,
				pickupEndAt.minusHours(1), pickupEndAt, "https://cdn.example.com/a.jpg"));
	}

	private Store seedTwoSellingStores() {
		Store hydroponics = store("수경야채", "37.566000", "126.908000", true);
		Store fruitVillage = store("청과마을", "37.560000", "126.900000", true);
		product(fruitVillage, "알배기 배추 2통", now.plusHours(3));
		product(fruitVillage, "대파 1단", now.plusHours(4));
		product(hydroponics, "양파 1.5kg", now.plusHours(1));
		return hydroponics;
	}

	@Test
	void nearbyStores_areReturnedAsMarkersWithTheirSellableCount() throws Exception {
		seedTwoSellingStores();

		mockMvc.perform(get("/stores/nearby?" + BOUNDS)
				.header("Authorization", bearer(TokenRealm.GUEST, "GUEST")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.totalStoreCount").value(2))
			.andExpect(jsonPath("$.data.truncated").value(false))
			.andExpect(jsonPath("$.data.stores.length()").value(2))
			.andExpect(jsonPath("$.data.stores[0].name").value("청과마을"))
			.andExpect(jsonPath("$.data.stores[0].sellableProductCount").value(2))
			.andExpect(jsonPath("$.data.stores[0].latitude").value(37.560000))
			.andExpect(jsonPath("$.data.stores[0].longitude").value(126.900000))
			.andExpect(jsonPath("$.data.stores[1].name").value("수경야채"))
			.andExpect(jsonPath("$.data.stores[1].sellableProductCount").value(1))
			.andExpect(jsonPath("$.data.stores[1].latitude").value(37.566000))
			.andExpect(jsonPath("$.data.stores[1].longitude").value(126.908000));
	}

	@Test
	void storesOutsideTheBoundsWithoutStockOrUnapproved_areNotMarkers() throws Exception {
		seedTwoSellingStores();
		store("재고없는가게", "37.556500", "126.901500", true);
		Store faraway = store("먼가게", "37.700000", "127.100000", true);
		product(faraway, "감자 1kg", now.plusHours(2));
		Store pending = store("미승인가게", "37.557000", "126.902000", false);
		product(pending, "숨은 상품", now.plusHours(2));

		mockMvc.perform(get("/stores/nearby?" + BOUNDS)
				.header("Authorization", bearer(TokenRealm.GUEST, "GUEST")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.totalStoreCount").value(2));
	}

	@Test
	void validationMessages_comeFromTheBundle_notTheJvmLocale() throws Exception {
		mockMvc.perform(get("/stores/nearby?minLat=37.55")
				.header("Authorization", bearer(TokenRealm.GUEST, "GUEST")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors.maxLat").value("필수 값입니다."));
	}

	@Test
	void invertedBounds_failValidation() throws Exception {
		mockMvc.perform(get("/stores/nearby?minLat=37.57&maxLat=37.55&minLng=126.89&maxLng=126.91")
				.header("Authorization", bearer(TokenRealm.GUEST, "GUEST")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.fieldErrors.boundsOrdered").exists());
	}

	@Test
	void storeProducts_carryTheWalkingDistanceWhenAPositionIsGiven() throws Exception {
		Store hydroponics = seedTwoSellingStores();

		mockMvc.perform(get("/stores/" + hydroponics.getId() + "/products?lat=37.556&lng=126.901")
				.header("Authorization", bearer(TokenRealm.GUEST, "GUEST")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.name").value("수경야채"))
			.andExpect(jsonPath("$.data.latitude").value(37.566000))
			.andExpect(jsonPath("$.data.longitude").value(126.908000))
			.andExpect(jsonPath("$.data.productCount").value(1))
			.andExpect(jsonPath("$.data.products[0].name").value("양파 1.5kg"))
			.andExpect(jsonPath("$.data.businessCloseTime").value("21:00:00"))
			.andExpect(jsonPath("$.data.distanceMeters")
				.value(org.hamcrest.Matchers.both(
					org.hamcrest.Matchers.greaterThan(1260))
					.and(org.hamcrest.Matchers.lessThan(1285))))
			.andExpect(jsonPath("$.data.walkingMinutes").value(19));
	}

	@Test
	void storeProducts_reportTheEarliestDeadlineAcrossTheStore() throws Exception {
		seedTwoSellingStores();
		Long fruitVillageId = storeRepository.findAll().stream()
			.filter(store -> store.getName().equals("청과마을"))
			.findFirst().orElseThrow().getId();

		mockMvc.perform(get("/stores/" + fruitVillageId + "/products")
				.header("Authorization", bearer(TokenRealm.GUEST, "GUEST")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.productCount").value(2))
			.andExpect(jsonPath("$.data.earliestPickupEndAt").value(org.hamcrest.Matchers.startsWith(
				now.plusHours(3).truncatedTo(java.time.temporal.ChronoUnit.MINUTES)
					.toString().substring(0, 16))));
	}

	@Test
	void aPositionOutOfRangeOrHalfGiven_failsValidation() throws Exception {
		Store hydroponics = seedTwoSellingStores();
		String base = "/stores/" + hydroponics.getId() + "/products";

		mockMvc.perform(get(base + "?lat=37.556")
				.header("Authorization", bearer(TokenRealm.GUEST, "GUEST")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors.positionComplete").exists());
		mockMvc.perform(get(base + "?lat=1e999&lng=1e999")
				.header("Authorization", bearer(TokenRealm.GUEST, "GUEST")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}

	@Test
	void aCoordinateWithAnAbsurdScale_isRejectedNotServerError() throws Exception {
		mockMvc.perform(get("/stores/nearby?minLat=1E-2000000000&maxLat=37.57"
					+ "&minLng=126.89&maxLng=126.91")
				.header("Authorization", bearer(TokenRealm.GUEST, "GUEST")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}

	@Test
	void aViewportWiderThanTheLimit_isRejected() throws Exception {
		mockMvc.perform(get("/stores/nearby?minLat=37.0&maxLat=38.0&minLng=126.89&maxLng=126.91")
				.header("Authorization", bearer(TokenRealm.GUEST, "GUEST")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VIEWPORT_TOO_LARGE"));
	}

	@Test
	void storeProducts_omitTheDistanceWithoutAPosition() throws Exception {
		Store hydroponics = seedTwoSellingStores();

		mockMvc.perform(get("/stores/" + hydroponics.getId() + "/products")
				.header("Authorization", bearer(TokenRealm.GUEST, "GUEST")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.distanceMeters").doesNotExist())
			.andExpect(jsonPath("$.data.walkingMinutes").doesNotExist());
	}

	@Test
	void storeProducts_onAnUnapprovedStore_areNotFound() throws Exception {
		Store pending = store("미승인가게", "37.557000", "126.902000", false);

		mockMvc.perform(get("/stores/" + pending.getId() + "/products")
				.header("Authorization", bearer(TokenRealm.GUEST, "GUEST")))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("STORE_NOT_FOUND"));
	}

	@Test
	void mapEndpoints_areOpenToConsumersAndAdmins_butNotOwners() throws Exception {
		Store hydroponics = seedTwoSellingStores();
		for (String url : new String[] {
				"/stores/nearby?" + BOUNDS,
				"/stores/" + hydroponics.getId() + "/products"}) {
			mockMvc.perform(get(url).header("Authorization", bearer(TokenRealm.USER, "CONSUMER")))
				.andExpect(status().isOk());
			mockMvc.perform(get(url).header("Authorization", bearer(TokenRealm.ADMIN, "SUPER")))
				.andExpect(status().isOk());
			mockMvc.perform(get(url).header("Authorization", bearer(TokenRealm.USER, "OWNER")))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("FORBIDDEN"));
			mockMvc.perform(get(url))
				.andExpect(status().isUnauthorized());
		}
	}
}
