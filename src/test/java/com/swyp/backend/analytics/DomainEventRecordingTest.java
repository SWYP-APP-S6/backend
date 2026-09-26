package com.swyp.backend.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.swyp.backend.AppDataCleaner;
import com.swyp.backend.RedisTestcontainersConfiguration;
import com.swyp.backend.TestcontainersConfiguration;
import com.swyp.backend.analytics.entity.DomainEvent;
import com.swyp.backend.analytics.entity.DomainEventType;
import com.swyp.backend.analytics.repository.DomainEventRepository;
import com.swyp.backend.common.exception.BusinessException;
import com.swyp.backend.hold.HoldFixture;
import com.swyp.backend.hold.dto.HoldCreateRequest;
import com.swyp.backend.hold.dto.HoldDetailResponse;
import com.swyp.backend.hold.entity.Hold;
import com.swyp.backend.hold.exception.HoldErrorCode;
import com.swyp.backend.hold.repository.HoldRepository;
import com.swyp.backend.hold.service.HoldExpiryService;
import com.swyp.backend.hold.service.HoldService;
import com.swyp.backend.hold.service.OwnerHoldService;
import com.swyp.backend.product.dto.StockReconfirmRequest;
import com.swyp.backend.product.dto.StockUpdateRequest;
import com.swyp.backend.product.entity.Product;
import com.swyp.backend.product.entity.ProductCategory;
import com.swyp.backend.product.repository.ProductRepository;
import com.swyp.backend.product.service.ProductService;
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
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@Import({TestcontainersConfiguration.class, RedisTestcontainersConfiguration.class})
@TestPropertySource(properties = "hold.expiry-scan-interval=1h")
class DomainEventRecordingTest {

	@Autowired
	AppDataCleaner appDataCleaner;

	@Autowired
	HoldService holdService;

	@Autowired
	OwnerHoldService ownerHoldService;

	@Autowired
	HoldExpiryService holdExpiryService;

	@Autowired
	ProductService productService;

	@Autowired
	UserRepository userRepository;

	@Autowired
	StoreRepository storeRepository;

	@Autowired
	ProductRepository productRepository;

	@Autowired
	HoldRepository holdRepository;

	@Autowired
	DomainEventRepository domainEventRepository;

	private User consumer;
	private User owner;
	private Store store;
	private Product product;

	@BeforeEach
	void setUp() {
		appDataCleaner.clear();
		consumer = userRepository.saveAndFlush(
				new User(UserRole.CONSUMER, "소비자", null, false, Instant.now()));
		owner = userRepository.saveAndFlush(
				new User(UserRole.OWNER, "점주", null, false, Instant.now()));
		Store created = new Store(
				owner, "청과마을", "04524", "서울 마포구 망원로 12", null, "0212345678",
				new BigDecimal("37.556000"), new BigDecimal("126.901000"),
				LocalTime.of(0, 0), LocalTime.of(23, 59));
		created.replaceCategories(Set.of(StoreCategory.FRUIT));
		created.replaceBusinessDays(EnumSet.allOf(DayOfWeek.class));
		created.approve();
		store = storeRepository.saveAndFlush(created);
		product = product("복숭아 4입", 5);
	}

	@AfterEach
	void tearDown() {
		appDataCleaner.clear();
	}

	private Product product(String name, int qty) {
		return productRepository.saveAndFlush(new Product(
				store, name, ProductCategory.FRUIT, qty, 10_000, 4_000,
				LocalDateTime.now().minusHours(1), LocalDateTime.now().plusHours(5),
				"https://cdn.example.com/peach.jpg"));
	}

	private Long hold(Product target, int qty) {
		return hold(consumer, target, qty);
	}

	private Long hold(User who, Product target, int qty) {
		HoldDetailResponse response = holdService.create(
				who.getId(), new HoldCreateRequest(target.getId(), qty));
		return response.items().stream()
				.filter(item -> item.productId().equals(target.getId()))
				.findFirst()
				.orElseThrow()
				.holdId();
	}

	private List<DomainEvent> eventsOf(DomainEventType type) {
		return domainEventRepository.findAll().stream()
				.filter(event -> event.getEventType() == type)
				.toList();
	}

	private static int intOf(DomainEvent event, String key) {
		return ((Number) event.getPayload().get(key)).intValue();
	}

	@Test
	void holdingRecordsTheHold_andTakingTheLastUnitRecordsTheSellOut() {
		Long holdId = hold(product, 3);

		List<DomainEvent> created = eventsOf(DomainEventType.HOLD_CREATE);
		assertThat(created).hasSize(1);
		DomainEvent event = created.getFirst();
		assertThat(event.getUserId()).isEqualTo(consumer.getId());
		assertThat(event.getStoreId()).isEqualTo(store.getId());
		assertThat(event.getProductId()).isEqualTo(product.getId());
		assertThat(event.getHoldId()).isEqualTo(holdId);
		assertThat(event.getPayload())
				.containsEntry("productName", "복숭아 4입")
				.containsEntry("storeName", "청과마을")
				.containsEntry("nickname", "소비자")
				.containsEntry("merged", false);
		assertThat(intOf(event, "qty")).isEqualTo(3);
		assertThat(eventsOf(DomainEventType.PRODUCT_SOLD_OUT)).isEmpty();
		assertThat(eventsOf(DomainEventType.STOCK_RECONFIRM_TRIGGER))
				.singleElement()
				.satisfies(trigger -> {
					assertThat(trigger.getUserId()).isEqualTo(owner.getId());
					assertThat(intOf(trigger, "thresholdQty")).isEqualTo(3);
				});

		User another = userRepository.saveAndFlush(
				new User(UserRole.CONSUMER, "다른소비자", null, false, Instant.now()));
		hold(another, product, 2);

		assertThat(eventsOf(DomainEventType.HOLD_CREATE)).hasSize(2);
		assertThat(eventsOf(DomainEventType.PRODUCT_SOLD_OUT))
				.singleElement()
				.satisfies(soldOut -> {
					assertThat(soldOut.getProductId()).isEqualTo(product.getId());
					assertThat(soldOut.getPayload()).containsEntry("cause", "HOLD");
				});
	}

	@Test
	void aRefusedHoldLeavesAFailureEvent_butNoHoldRow() {
		Product scarce = product("딸기 1팩", 2);

		assertThatThrownBy(() -> holdService.create(
				consumer.getId(), new HoldCreateRequest(scarce.getId(), 3)))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getCode())
				.isEqualTo(HoldErrorCode.INSUFFICIENT_QTY);

		assertThat(holdRepository.count()).isZero();
		assertThat(eventsOf(DomainEventType.HOLD_CREATE)).isEmpty();
		assertThat(eventsOf(DomainEventType.HOLD_FAIL))
				.singleElement()
				.satisfies(failure -> {
					assertThat(failure.getUserId()).isEqualTo(consumer.getId());
					assertThat(failure.getStoreId()).isEqualTo(store.getId());
					assertThat(failure.getProductId()).isEqualTo(scarce.getId());
					assertThat(failure.getHoldId()).isNull();
					assertThat(failure.getPayload())
							.containsEntry("code", "INSUFFICIENT_QTY")
							.containsEntry("productName", "딸기 1팩");
					assertThat(intOf(failure, "qty")).isEqualTo(3);
					assertThat(intOf(failure, "availableQty")).isEqualTo(2);
				});
	}

	@Test
	void cancelingRecordsWhoCanceled_andWhetherItCostACredit() {
		Long holdId = hold(product, 1);

		holdService.cancel(consumer.getId(), holdId);

		assertThat(eventsOf(DomainEventType.HOLD_CANCEL))
				.singleElement()
				.satisfies(canceled -> {
					assertThat(canceled.getHoldId()).isEqualTo(holdId);
					assertThat(canceled.getPayload())
							.containsEntry("canceledBy", "USER")
							.containsEntry("charged", false);
				});
	}

	@Test
	void theExpiryScanRecordsEachHoldItExpires() {
		product.hold(2);
		productRepository.saveAndFlush(product);
		Hold overdue = holdRepository.saveAndFlush(
				HoldFixture.hold(consumer, product, 2, Instant.now().minusSeconds(60)));

		holdExpiryService.expireOverdueHolds();

		assertThat(eventsOf(DomainEventType.HOLD_EXPIRE))
				.singleElement()
				.satisfies(expired -> {
					assertThat(expired.getHoldId()).isEqualTo(overdue.getId());
					assertThat(expired.getUserId()).isEqualTo(consumer.getId());
					assertThat(expired.getPayload()).containsEntry("via", "SCAN");
					assertThat(intOf(expired, "qty")).isEqualTo(2);
				});
	}

	@Test
	void completingAPickupRecordsWhatWasPaidFor() {
		Long holdId = hold(product, 2);

		ownerHoldService.completePickup(owner.getId(), holdId);

		assertThat(eventsOf(DomainEventType.PICKUP_COMPLETE))
				.singleElement()
				.satisfies(pickup -> {
					assertThat(pickup.getHoldId()).isEqualTo(holdId);
					assertThat(pickup.getUserId()).isEqualTo(consumer.getId());
					assertThat(pickup.getPayload()).containsEntry("fromExpired", false);
					assertThat(intOf(pickup, "amount")).isEqualTo(8_000);
				});
	}

	@Test
	void restockingBelowTheHeldQuantityRecordsTheAdjustment_theOversell_andTheSellOut() {
		hold(product, 2);

		productService.updateStock(owner.getId(), product.getId(), new StockUpdateRequest(1));

		assertThat(eventsOf(DomainEventType.STOCK_ADJUST))
				.singleElement()
				.satisfies(adjust -> {
					assertThat(adjust.getUserId()).isEqualTo(owner.getId());
					assertThat(adjust.getProductId()).isEqualTo(product.getId());
					assertThat(intOf(adjust, "stockBefore")).isEqualTo(5);
					assertThat(intOf(adjust, "stockAfter")).isEqualTo(1);
				});
		assertThat(eventsOf(DomainEventType.OVERSELL_DETECTED))
				.singleElement()
				.satisfies(oversell -> assertThat(intOf(oversell, "shortfallQty")).isEqualTo(1));
		assertThat(eventsOf(DomainEventType.PRODUCT_SOLD_OUT))
				.singleElement()
				.satisfies(soldOut -> assertThat(soldOut.getPayload()).containsEntry("cause", "RESTOCK"));
	}

	@Test
	void answeringTheStockReconfirmRecordsTheAnswer() {
		hold(product, 3);

		productService.answerStockReconfirm(
				owner.getId(), product.getId(), new StockReconfirmRequest(true));

		assertThat(eventsOf(DomainEventType.STOCK_RECONFIRM_YES))
				.singleElement()
				.satisfies(answer -> {
					assertThat(answer.getUserId()).isEqualTo(owner.getId());
					assertThat(intOf(answer, "heldQty")).isEqualTo(3);
				});
		assertThat(eventsOf(DomainEventType.STOCK_RECONFIRM_NO)).isEmpty();
	}
}
