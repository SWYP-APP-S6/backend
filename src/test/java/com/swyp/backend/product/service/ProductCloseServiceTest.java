package com.swyp.backend.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.swyp.backend.AppDataCleaner;
import com.swyp.backend.RedisTestcontainersConfiguration;
import com.swyp.backend.TestcontainersConfiguration;
import com.swyp.backend.product.entity.Product;
import com.swyp.backend.product.entity.ProductCategory;
import com.swyp.backend.product.entity.ProductStatus;
import com.swyp.backend.product.repository.ProductRepository;
import com.swyp.backend.store.entity.Store;
import com.swyp.backend.store.entity.StoreCategory;
import com.swyp.backend.store.repository.StoreRepository;
import com.swyp.backend.user.entity.User;
import com.swyp.backend.user.entity.UserRole;
import com.swyp.backend.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@Import({TestcontainersConfiguration.class, RedisTestcontainersConfiguration.class})
class ProductCloseServiceTest {

	@Autowired
	AppDataCleaner appDataCleaner;

	@Autowired
	ProductCloseService productCloseService;

	@Autowired
	UserRepository userRepository;

	@Autowired
	StoreRepository storeRepository;

	@Autowired
	ProductRepository productRepository;

	@Autowired
	Clock clock;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	PlatformTransactionManager transactionManager;

	private Store store;

	@BeforeEach
	void setUp() {
		appDataCleaner.clear();

		User owner = userRepository.saveAndFlush(
				new User(UserRole.OWNER, "점주", null, false, Instant.now()));
		store = new Store(
				owner, "청과마을", "04524", "서울 마포구 망원로 12", "1층", "02-1234-5678",
				new BigDecimal("37.556000"), new BigDecimal("126.901000"),
				LocalTime.of(9, 0), LocalTime.of(21, 0));
		store.replaceCategories(Set.of(StoreCategory.FRUIT));
		store.replaceBusinessDays(EnumSet.allOf(DayOfWeek.class));
		store.approve();
		storeRepository.saveAndFlush(store);
	}

	@AfterEach
	void tearDown() {
		appDataCleaner.clear();
	}

	private Product product(String name, LocalDateTime pickupEndAt) {
		return productRepository.saveAndFlush(new Product(
				store, name, ProductCategory.FRUIT, 5, 10_000, 4_000,
				pickupEndAt.minusHours(3), pickupEndAt, "https://cdn.example.com/peach.jpg"));
	}

	private ProductStatus statusOf(Product product) {
		return productRepository.findById(product.getId()).orElseThrow().getStatus();
	}

	@Test
	void everyProductPastItsPickupEndClosesWhetherOnSaleOrSoldOut() {
		LocalDateTime now = LocalDateTime.now(clock);
		Product onSale = product("마감된 복숭아", now.minusMinutes(1));
		Product soldOut = product("다 팔린 자두", now.minusMinutes(1));
		soldOut.restock(0);
		productRepository.saveAndFlush(soldOut);
		Product stillOpen = product("저녁까지 파는 포도", now.plusHours(2));

		assertThat(productCloseService.closeEndedProducts()).isEqualTo(2);

		assertThat(statusOf(onSale)).isEqualTo(ProductStatus.CLOSED);
		assertThat(statusOf(soldOut))
			.as("sold out is a state inside the sale window; once the window ends it is over too")
			.isEqualTo(ProductStatus.CLOSED);
		assertThat(statusOf(stillOpen)).isEqualTo(ProductStatus.ON_SALE);
	}

	@Test
	void aProductAlreadyClosedIsNotCountedAgain() {
		Product ended = product("마감된 복숭아", LocalDateTime.now(clock).minusMinutes(1));

		assertThat(productCloseService.closeEndedProducts()).isEqualTo(1);
		assertThat(productCloseService.closeEndedProducts())
			.as("the scan is idempotent, so a second instance running it at the same time is harmless")
			.isZero();
		assertThat(statusOf(ended)).isEqualTo(ProductStatus.CLOSED);
	}

	@Test
	void closingTouchesOnlyTheStatusAndLeavesTheQuantitiesAsHistory() {
		Product ended = product("마감된 복숭아", LocalDateTime.now(clock).minusMinutes(1));
		ended.hold(2);
		ended.restock(4);
		productRepository.saveAndFlush(ended);
		Instant updatedBefore = productRepository.findById(ended.getId()).orElseThrow().getUpdatedAt();

		productCloseService.closeEndedProducts();

		Product closed = productRepository.findById(ended.getId()).orElseThrow();
		assertThat(closed.getStatus()).isEqualTo(ProductStatus.CLOSED);
		assertThat(closed.getHeldQty()).isEqualTo(2);
		assertThat(closed.getStockQty()).isEqualTo(4);
		assertThat(closed.getAvailableQty()).isEqualTo(2);
		assertThat(closed.getUpdatedAt())
			.as("a bulk update skips JPA auditing, so the query stamps updated_at itself")
			.isAfter(updatedBefore);
	}

	@Test
	void theBatchLocksEndedProductsInIdOrderLikeEveryOtherMultiProductPath() throws Exception {
		LocalDateTime now = LocalDateTime.now(clock);
		Product lowerId = product("먼저 등록한 복숭아", now.minusMinutes(1));
		Product higherId = product("나중 등록한 자두", now.minusMinutes(30));
		lowerId.restock(4);
		productRepository.saveAndFlush(lowerId);

		TransactionTemplate tx = new TransactionTemplate(transactionManager);
		ExecutorService pool = Executors.newFixedThreadPool(2);
		CountDownLatch lowerIdLocked = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		try {
			pool.submit(() -> tx.executeWithoutResult(status -> {
				lockNow(lowerId);
				lowerIdLocked.countDown();
				awaitQuietly(release);
			}));
			assertThat(lowerIdLocked.await(10, TimeUnit.SECONDS)).isTrue();

			Future<Integer> batch = pool.submit(productCloseService::closeEndedProducts);
			awaitBatchBlockedOnALock();

			assertThatCode(() -> tx.executeWithoutResult(status -> lockNow(higherId)))
				.as("an owner confirming a pickup locks the products of a group in id order "
						+ "(OwnerHoldService); a batch that grabbed the higher id first while waiting "
						+ "for the lower one would deadlock with it")
				.doesNotThrowAnyException();

			release.countDown();
			assertThat(batch.get(10, TimeUnit.SECONDS)).isEqualTo(2);
		} finally {
			release.countDown();
			pool.shutdownNow();
		}
	}

	private void lockNow(Product product) {
		jdbcTemplate.queryForObject(
				"select id from products where id = ? for update nowait", Long.class, product.getId());
	}

	private void awaitBatchBlockedOnALock() throws InterruptedException {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
		while (System.nanoTime() < deadline) {
			Integer waiting = jdbcTemplate.queryForObject("""
					select count(*) from pg_stat_activity
					where wait_event_type = 'Lock' and query ilike 'update products%'
					""", Integer.class);
			if (waiting != null && waiting > 0) {
				return;
			}
			Thread.sleep(20);
		}
		throw new AssertionError("the close batch never blocked on the lower id's row lock");
	}

	private static void awaitQuietly(CountDownLatch latch) {
		try {
			latch.await(30, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
