package com.swyp.backend.store.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.swyp.backend.RedisTestcontainersConfiguration;
import com.swyp.backend.TestcontainersConfiguration;
import com.swyp.backend.common.exception.BusinessException;
import com.swyp.backend.common.security.JwtTokenProvider;
import com.swyp.backend.common.security.TokenRealm;
import com.swyp.backend.store.exception.StoreErrorCode;
import com.swyp.backend.store.repository.StoreRepository;
import com.swyp.backend.store.service.GeocodingClient;
import com.swyp.backend.user.entity.User;
import com.swyp.backend.user.entity.UserRole;
import com.swyp.backend.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
import org.springframework.transaction.support.TransactionSynchronizationManager;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, RedisTestcontainersConfiguration.class})
class OwnerStoreControllerTest {

	private static final String ADDRESS = "서울특별시 강남구 역삼로 123";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	UserRepository userRepository;

	@Autowired
	StoreRepository storeRepository;

	@Autowired
	JwtTokenProvider tokenProvider;

	@Autowired
	StubGeocodingClient geocodingClient;

	@BeforeEach
	void resetGeocodingStub() {
		geocodingClient.clear();
	}

	private User createUser(UserRole role) {
		return userRepository.saveAndFlush(new User(role, "테스트유저", null, false, Instant.now()));
	}

	private String tokenFor(User user) {
		return tokenProvider.createAccessToken(TokenRealm.USER, user.getId(), user.getRole().name());
	}

	private static String registerBody() {
		return """
			{"name":"청과왕","address":"%s","addressDetail":"1층","phone":"02-1234-5678",\
			"businessOpenTime":"09:00:00","businessCloseTime":"21:00:00",\
			"businessRegistrationNumber":"123-45-67890","applicationNote":"메모"}""".formatted(ADDRESS);
	}

	@Test
	void registerStore_succeeds_forAnOwnerWithoutAStore() throws Exception {
		User owner = createUser(UserRole.OWNER);
		geocodingClient.register(ADDRESS,
			new GeocodingClient.Coordinates(new BigDecimal("37.500600"), new BigDecimal("127.036500")));

		mockMvc.perform(post("/owner/stores")
				.header("Authorization", "Bearer " + tokenFor(owner))
				.contentType(MediaType.APPLICATION_JSON)
				.content(registerBody()))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.name").value("청과왕"))
			.andExpect(jsonPath("$.data.status").value("PENDING"));

		assertThat(storeRepository.findByOwnerId(owner.getId())).isPresent();
	}

	@Test
	void registerStore_rejectsASecondStoreForTheSameOwner() throws Exception {
		User owner = createUser(UserRole.OWNER);
		geocodingClient.register(ADDRESS,
			new GeocodingClient.Coordinates(new BigDecimal("37.500600"), new BigDecimal("127.036500")));
		String token = tokenFor(owner);

		mockMvc.perform(post("/owner/stores")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(registerBody()))
			.andExpect(status().isCreated());

		mockMvc.perform(post("/owner/stores")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(registerBody()))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("STORE_ALREADY_REGISTERED"));
	}

	@Test
	void registerStore_rejectsAConsumerAccount() throws Exception {
		User consumer = createUser(UserRole.CONSUMER);
		geocodingClient.register(ADDRESS,
			new GeocodingClient.Coordinates(new BigDecimal("37.500600"), new BigDecimal("127.036500")));

		mockMvc.perform(post("/owner/stores")
				.header("Authorization", "Bearer " + tokenFor(consumer))
				.contentType(MediaType.APPLICATION_JSON)
				.content(registerBody()))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("OWNER_ROLE_REQUIRED"));
	}

	@Test
	void registerStore_rejectsAPhoneNumberLongerThanTheColumnLimit() throws Exception {
		User owner = createUser(UserRole.OWNER);
		String tooLongPhone = "0".repeat(21);

		mockMvc.perform(post("/owner/stores")
				.header("Authorization", "Bearer " + tokenFor(owner))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"name":"청과왕","address":"%s","addressDetail":"1층","phone":"%s",\
					"businessOpenTime":"09:00:00","businessCloseTime":"21:00:00"}""".formatted(ADDRESS, tooLongPhone)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}

	@Test
	void registerStore_withAnUngeocodableAddress_isRejected() throws Exception {
		User owner = createUser(UserRole.OWNER);

		mockMvc.perform(post("/owner/stores")
				.header("Authorization", "Bearer " + tokenFor(owner))
				.contentType(MediaType.APPLICATION_JSON)
				.content(registerBody()))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("GEOCODING_FAILED"));
	}

	@Test
	void registerStore_underConcurrentRequestsForTheSameOwner_onlyOneSucceeds() throws Exception {
		User owner = createUser(UserRole.OWNER);
		geocodingClient.register(ADDRESS,
			new GeocodingClient.Coordinates(new BigDecimal("37.500600"), new BigDecimal("127.036500")));
		String token = tokenFor(owner);
		geocodingClient.awaitBothCallsBeforeReturning();

		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			List<Future<Integer>> futures = new ArrayList<>();
			for (int i = 0; i < 2; i++) {
				futures.add(executor.submit(() -> mockMvc.perform(post("/owner/stores")
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(registerBody()))
					.andReturn().getResponse().getStatus()));
			}
			List<Integer> statuses = new ArrayList<>();
			for (Future<Integer> future : futures) {
				statuses.add(future.get(10, TimeUnit.SECONDS));
			}
			assertThat(statuses).containsExactlyInAnyOrder(201, 409);
		} finally {
			executor.shutdownNow();
		}

		assertThat(storeRepository.findByOwnerId(owner.getId())).isPresent();
	}

	@Test
	void getMyStore_withNoStoreRegistered_returnsNotFound() throws Exception {
		User owner = createUser(UserRole.OWNER);

		mockMvc.perform(get("/owner/stores/me").header("Authorization", "Bearer " + tokenFor(owner)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("STORE_NOT_REGISTERED"));
	}

	@Test
	void getMyStore_returnsTheRegisteredStore() throws Exception {
		User owner = createUser(UserRole.OWNER);
		geocodingClient.register(ADDRESS,
			new GeocodingClient.Coordinates(new BigDecimal("37.500600"), new BigDecimal("127.036500")));
		String token = tokenFor(owner);
		mockMvc.perform(post("/owner/stores")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(registerBody()))
			.andExpect(status().isCreated());

		mockMvc.perform(get("/owner/stores/me").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.name").value("청과왕"));
	}

	@TestConfiguration
	static class StubGeocodingClientConfiguration {

		@Bean
		@Primary
		StubGeocodingClient stubGeocodingClient() {
			return new StubGeocodingClient();
		}
	}

	static class StubGeocodingClient implements GeocodingClient {

		private final Map<String, Coordinates> coordinatesByAddress = new ConcurrentHashMap<>();
		private volatile CyclicBarrier barrier;

		void register(String address, Coordinates coordinates) {
			coordinatesByAddress.put(address, coordinates);
		}

		void clear() {
			coordinatesByAddress.clear();
			barrier = null;
		}

		void awaitBothCallsBeforeReturning() {
			barrier = new CyclicBarrier(2);
		}

		@Override
		public Coordinates geocode(String address) {
			if (TransactionSynchronizationManager.isActualTransactionActive()) {
				throw new IllegalStateException(
					"geocode() ran inside an active transaction — it must stay outside one "
						+ "so a slow Kakao call can't hold a pooled DB connection");
			}
			CyclicBarrier currentBarrier = barrier;
			if (currentBarrier != null) {
				try {
					currentBarrier.await(10, TimeUnit.SECONDS);
				} catch (Exception e) {
					throw new IllegalStateException(e);
				}
			}
			Coordinates coordinates = coordinatesByAddress.get(address);
			if (coordinates == null) {
				throw new BusinessException(StoreErrorCode.GEOCODING_FAILED);
			}
			return coordinates;
		}
	}
}
