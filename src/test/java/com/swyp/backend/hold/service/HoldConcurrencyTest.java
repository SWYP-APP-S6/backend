package com.swyp.backend.hold.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.swyp.backend.RedisTestcontainersConfiguration;
import com.swyp.backend.TestcontainersConfiguration;
import com.swyp.backend.common.security.JwtTokenProvider;
import com.swyp.backend.common.security.TokenRealm;
import com.swyp.backend.hold.entity.Hold;
import com.swyp.backend.hold.entity.HoldStatus;
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
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, RedisTestcontainersConfiguration.class})
@TestPropertySource(properties = "hold.expiry-scan-interval=1h")
class HoldConcurrencyTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	HoldExpiryService holdExpiryService;

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

	private Product product;

	@BeforeEach
	void setUp() {
		clearCommittedRows();

		User owner = userRepository.saveAndFlush(
				new User(UserRole.OWNER, "점주", null, false, Instant.now()));
		Store store = new Store(
				owner, "청과마을", "04524", "서울 마포구 망원로 12", "1층", "02-1234-5678",
				new BigDecimal("37.556000"), new BigDecimal("126.901000"),
				LocalTime.of(9, 0), LocalTime.of(21, 0));
		store.replaceCategories(Set.of(StoreCategory.FRUIT));
		store.replaceBusinessDays(EnumSet.allOf(DayOfWeek.class));
		store.approve();
		storeRepository.saveAndFlush(store);

		LocalDateTime pickupEndAt = LocalDateTime.now().plusHours(5);
		product = productRepository.saveAndFlush(new Product(
				store, "복숭아 4입", ProductCategory.FRUIT, 1, 10_000, 4_000,
				pickupEndAt.minusHours(1), pickupEndAt, "https://cdn.example.com/peach.jpg"));
	}

	private String tokenFor(String nickname) {
		User user = userRepository.saveAndFlush(
				new User(UserRole.CONSUMER, nickname, null, false, Instant.now()));
		return tokenProvider.createAccessToken(TokenRealm.USER, user.getId(), "CONSUMER");
	}

	private static <T> List<T> runTogether(List<Callable<T>> tasks) throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
		try {
			CyclicBarrier barrier = new CyclicBarrier(tasks.size());
			List<Future<T>> futures = new ArrayList<>();
			for (Callable<T> task : tasks) {
				futures.add(executor.submit(() -> {
					barrier.await(5, TimeUnit.SECONDS);
					return task.call();
				}));
			}
			List<T> results = new ArrayList<>();
			for (Future<T> future : futures) {
				results.add(future.get(20, TimeUnit.SECONDS));
			}
			return results;
		} finally {
			executor.shutdownNow();
		}
	}

	@AfterEach
	void tearDown() {
		clearCommittedRows();
	}

	private void clearCommittedRows() {
		holdRepository.deleteAll();
		notificationRepository.deleteAll();
		productRepository.deleteAll();
		storeRepository.deleteAll();
		userRepository.deleteAll();
	}

	private Product reloaded() {
		return productRepository.findById(product.getId()).orElseThrow();
	}

	@Test
	void twoPeopleReachingForTheLastItemLeaveExactlyOneHold() throws Exception {
		String first = tokenFor("소비자1");
		String second = tokenFor("소비자2");
		String body = "{\"productId\":%d,\"qty\":1}".formatted(product.getId());

		List<Integer> statuses = runTogether(List.<Callable<Integer>>of(
				() -> mockMvc.perform(post("/holds").header("Authorization", "Bearer " + first)
						.contentType(MediaType.APPLICATION_JSON).content(body))
					.andReturn().getResponse().getStatus(),
				() -> mockMvc.perform(post("/holds").header("Authorization", "Bearer " + second)
						.contentType(MediaType.APPLICATION_JSON).content(body))
					.andReturn().getResponse().getStatus()));

		assertThat(statuses).as("one 201 and one refusal, never two 201s").containsExactlyInAnyOrder(201, 409);
		assertThat(holdRepository.count()).isEqualTo(1);
		assertThat(reloaded().getAvailableQty())
			.as("overselling the last item is the failure this whole design exists to prevent")
			.isZero();
		assertThat(reloaded().getHeldQty()).isEqualTo(1);
	}

	@Test
	void twoExpiryPassesRunningTogetherReleaseTheQuantityOnlyOnce() throws Exception {
		User user = userRepository.saveAndFlush(
				new User(UserRole.CONSUMER, "만료될소비자", null, false, Instant.now()));
		Product held = reloaded();
		held.hold(1);
		productRepository.saveAndFlush(held);
		Hold overdue = holdRepository.saveAndFlush(
				new Hold(user, held, 1, Instant.now().minusSeconds(60)));

		List<Integer> expired = runTogether(List.<Callable<Integer>>of(
				holdExpiryService::expireOverdueHolds,
				holdExpiryService::expireOverdueHolds));

		assertThat(expired.stream().mapToInt(Integer::intValue).sum())
			.as("the row is expired once; the losing pass must find it already gone")
			.isEqualTo(1);
		assertThat(holdRepository.findById(overdue.getId()).orElseThrow().getStatus())
			.isEqualTo(HoldStatus.EXPIRED);
		assertThat(reloaded().getAvailableQty())
			.as("a double release would report stock that does not exist")
			.isEqualTo(1);
		assertThat(reloaded().getHeldQty()).isZero();
	}

	@Test
	void expiringWhileSomeoneHoldsTheSameProductKeepsTheCountsStraight() throws Exception {
		User user = userRepository.saveAndFlush(
				new User(UserRole.CONSUMER, "만료될소비자", null, false, Instant.now()));
		Product held = reloaded();
		held.hold(1);
		productRepository.saveAndFlush(held);
		holdRepository.saveAndFlush(new Hold(user, held, 1, Instant.now().minusSeconds(60)));

		String buyer = tokenFor("새소비자");
		String body = "{\"productId\":%d,\"qty\":1}".formatted(product.getId());

		List<Integer> results = runTogether(List.<Callable<Integer>>of(
				holdExpiryService::expireOverdueHolds,
				() -> mockMvc.perform(post("/holds").header("Authorization", "Bearer " + buyer)
						.contentType(MediaType.APPLICATION_JSON).content(body))
					.andReturn().getResponse().getStatus()));

		int postStatus = results.get(1);
		Product after = reloaded();
		assertThat(after.getAvailableQty() + after.getHeldQty())
			.as("expiry and a new hold touch the same row from two transactions; the totals must "
					+ "still add up to what the owner registered")
			.isEqualTo(1);

		if (postStatus == 201) {
			assertThat(after.getHeldQty())
				.as("the buyer won the freed item, so it is held -- not merely conserved")
				.isEqualTo(1);
			assertThat(after.getAvailableQty()).isZero();
			assertThat(holdRepository.findAll())
				.anyMatch(hold -> hold.getStatus() == HoldStatus.HOLDING);
		} else {
			assertThat(postStatus)
				.as("the only other outcome is losing the race on stock")
				.isEqualTo(409);
			assertThat(after.getAvailableQty()).isEqualTo(1);
			assertThat(after.getHeldQty()).isZero();
		}
	}
}
