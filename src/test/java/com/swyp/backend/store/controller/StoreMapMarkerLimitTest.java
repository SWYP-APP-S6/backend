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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, RedisTestcontainersConfiguration.class})
@TestPropertySource(properties = "browse.map-marker-limit=1")
@Transactional
class StoreMapMarkerLimitTest {

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

	@BeforeEach
	void setUp() {
		holdRepository.deleteAll();
		notificationRepository.deleteAll();
		productRepository.deleteAll();
		storeRepository.deleteAll();
		userRepository.deleteAll();
	}

	@Test
	void aViewportWithMoreStoresThanTheLimit_isTruncatedToTheClosestToItsCenter() throws Exception {
		sellingStore("중심가게", "37.560000", "126.900000");
		sellingStore("가장자리가게", "37.569000", "126.909000");

		mockMvc.perform(get("/stores/nearby?minLat=37.55&maxLat=37.57&minLng=126.89&maxLng=126.91")
				.header("Authorization",
					"Bearer " + tokenProvider.createAccessToken(TokenRealm.GUEST, 1L, "GUEST")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.totalStoreCount").value(2))
			.andExpect(jsonPath("$.data.truncated").value(true))
			.andExpect(jsonPath("$.data.stores.length()").value(1))
			.andExpect(jsonPath("$.data.stores[0].name").value("중심가게"));
	}

	private void sellingStore(String name, String latitude, String longitude) {
		User owner = userRepository.saveAndFlush(
				new User(UserRole.OWNER, name + "사장", null, false, Instant.now()));
		Store store = new Store(
				owner, name, "04524", "서울시 마포구 망원동 1", "1층", "02-1234-5678",
				new BigDecimal(latitude), new BigDecimal(longitude),
				LocalTime.of(9, 0), LocalTime.of(21, 0));
		store.replaceCategories(Set.of(StoreCategory.VEGETABLE));
		store.replaceBusinessDays(EnumSet.allOf(DayOfWeek.class));
		store.approve();
		storeRepository.saveAndFlush(store);
		productRepository.saveAndFlush(new Product(
				store, "양파 1.5kg", ProductCategory.VEGETABLE, 3, 10_000, 4_000,
				LocalDateTime.now(), LocalDateTime.now().plusHours(2), "https://cdn.example.com/a.jpg"));
	}
}
