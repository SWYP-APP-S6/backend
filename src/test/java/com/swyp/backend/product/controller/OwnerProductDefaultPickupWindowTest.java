package com.swyp.backend.product.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.swyp.backend.RedisTestcontainersConfiguration;
import com.swyp.backend.TestcontainersConfiguration;
import com.swyp.backend.common.security.JwtTokenProvider;
import com.swyp.backend.common.security.TokenRealm;
import com.swyp.backend.hold.repository.HoldRepository;
import com.swyp.backend.product.entity.Product;
import com.swyp.backend.product.repository.ProductRepository;
import com.swyp.backend.store.entity.Store;
import com.swyp.backend.store.repository.StoreRepository;
import com.swyp.backend.user.entity.User;
import com.swyp.backend.user.entity.UserRole;
import com.swyp.backend.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
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

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, RedisTestcontainersConfiguration.class})
class OwnerProductDefaultPickupWindowTest {

	private static final Instant FIXED_INSTANT = Instant.parse("2026-03-01T15:00:00Z");
	private static final LocalTime CLOSE_TIME = LocalTime.of(21, 0);

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
	JwtTokenProvider tokenProvider;

	private String token;

	@BeforeEach
	void setUp() {
		holdRepository.deleteAll();
		productRepository.deleteAll();
		storeRepository.deleteAll();
		userRepository.deleteAll();

		User owner = userRepository.saveAndFlush(new User(UserRole.OWNER, "테스트점주", null, false, Instant.now()));
		storeRepository.saveAndFlush(new Store(
			owner, "테스트가게", "04524", "서울특별시 강남구 역삼로 1", null, "0212345678",
			new BigDecimal("37.500000"), new BigDecimal("127.030000"),
			LocalTime.of(9, 0), CLOSE_TIME));
		token = tokenProvider.createAccessToken(TokenRealm.USER, owner.getId(), owner.getRole().name());
	}

	@Test
	void registerProduct_withoutAPickupEndAt_fallsBackToTheStoreCloseTimeInSeoul() throws Exception {
		String body = mockMvc.perform(post("/owner/products")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"name":"당근","category":"VEGETABLE","initialQty":10,"originalPrice":1000,"salePrice":800,\
					"photoUrl":"https://example.com/a.jpg"}"""))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();

		long productId = ((Number) JsonPath.read(body, "$.data.id")).longValue();
		Product product = productRepository.findById(productId).orElseThrow();
		assertThat(product.getPickupStartAt()).isEqualTo(LocalDateTime.of(2026, 3, 2, 0, 0));
		assertThat(product.getPickupEndAt()).isEqualTo(LocalDateTime.of(2026, 3, 2, 21, 0));
	}

	@TestConfiguration
	static class FixedClockConfiguration {

		@Bean
		@Primary
		Clock fixedClock() {
			return Clock.fixed(FIXED_INSTANT, ZoneId.of("Asia/Seoul"));
		}
	}
}
