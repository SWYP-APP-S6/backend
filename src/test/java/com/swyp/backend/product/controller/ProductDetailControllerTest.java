package com.swyp.backend.product.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.swyp.backend.RedisTestcontainersConfiguration;
import com.swyp.backend.TestcontainersConfiguration;
import com.swyp.backend.common.security.JwtTokenProvider;
import com.swyp.backend.common.security.TokenRealm;
import com.swyp.backend.hold.entity.Hold;
import com.swyp.backend.hold.repository.HoldRepository;
import com.swyp.backend.notification.repository.NotificationRepository;
import com.swyp.backend.product.entity.Product;
import com.swyp.backend.product.entity.ProductCategory;
import com.swyp.backend.product.repository.ProductRepository;
import com.swyp.backend.recipe.entity.Ingredient;
import com.swyp.backend.recipe.entity.Recipe;
import com.swyp.backend.recipe.entity.RecipeIngredient;
import com.swyp.backend.recipe.repository.IngredientRepository;
import com.swyp.backend.recipe.repository.RecipeIngredientRepository;
import com.swyp.backend.recipe.repository.RecipeRepository;
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
class ProductDetailControllerTest {

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
	RecipeRepository recipeRepository;

	@Autowired
	RecipeIngredientRepository recipeIngredientRepository;

	@Autowired
	IngredientRepository ingredientRepository;

	@Autowired
	JwtTokenProvider tokenProvider;

	private Product product;
	private Ingredient peach;

	@BeforeEach
	void setUp() {
		holdRepository.deleteAll();
		notificationRepository.deleteAll();
		productRepository.deleteAll();
		storeRepository.deleteAll();
		userRepository.deleteAll();
		recipeIngredientRepository.deleteAll();
		recipeRepository.deleteAll();
		ingredientRepository.deleteAll();

		peach = ingredientRepository.saveAndFlush(new Ingredient("복숭아", "복숭아", "과일"));
		Ingredient yogurt = ingredientRepository.saveAndFlush(new Ingredient("요거트", "요거트", "유제품"));

		User owner = userRepository.saveAndFlush(
				new User(UserRole.OWNER, "점주", null, false, Instant.now()));
		Store store = new Store(
				owner, "청과마을", "04524", "서울 마포구 망원로 12", "1층", "02-1234-5678",
				new BigDecimal("37.556000"), new BigDecimal("126.901000"),
				LocalTime.of(0, 0), LocalTime.of(23, 59));
		store.replaceCategories(Set.of(StoreCategory.FRUIT));
		store.replaceBusinessDays(EnumSet.allOf(DayOfWeek.class));
		store.approve();
		storeRepository.saveAndFlush(store);

		LocalDateTime pickupEndAt = LocalDateTime.now().plusHours(5);
		product = new Product(
				store, "복숭아 4입", ProductCategory.FRUIT, 3, 10_000, 4_000,
				pickupEndAt.minusHours(1), pickupEndAt, "https://cdn.example.com/peach.jpg");
		product.replaceIngredientIds(List.of(peach.getId()));
		product = productRepository.saveAndFlush(product);

		Ingredient cider = ingredientRepository.saveAndFlush(new Ingredient("사이다", "사이다", "음료"));
		recipeWith("복숭아 화채", List.of(peach, yogurt, cider));
		recipeWith("여름 디저트", List.of(peach));
		recipeWith("과일 샐러드", List.of(peach, yogurt));
	}

	private void recipeWith(String title, List<Ingredient> ingredients) {
		Recipe recipe = new Recipe("mfds", title + "-id", title, "후식", null, (short) 1, "kogl");
		recipe.assignImages("https://cdn.example.com/r.jpg", "https://cdn.example.com/rt.jpg", null);
		recipe.publish();
		recipeRepository.saveAndFlush(recipe);
		short seq = 1;
		for (Ingredient ingredient : ingredients) {
			recipeIngredientRepository.saveAndFlush(
					new RecipeIngredient(recipe, seq++, ingredient, null, null, null, ingredient.getName()));
		}
	}

	private String guest() {
		return "Bearer " + tokenProvider.createAccessToken(TokenRealm.GUEST, 1L, "GUEST");
	}

	@Test
	void theDetailCarriesTheProductTheStoreAndTheHoldButtonState() throws Exception {
		mockMvc.perform(get("/products/" + product.getId() + "?lat=37.560000&lng=126.905000")
				.header("Authorization", guest()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.name").value("복숭아 4입"))
			.andExpect(jsonPath("$.data.category").value("FRUIT"))
			.andExpect(jsonPath("$.data.availableQty").value(3))
			.andExpect(jsonPath("$.data.discountRate").value(60))
			.andExpect(jsonPath("$.data.photoUrls.length()").value(1))
			.andExpect(jsonPath("$.data.tags[0]").value("복숭아"))
			.andExpect(jsonPath("$.data.holdButton").value("AVAILABLE"))
			.andExpect(jsonPath("$.data.myHoldId").doesNotExist())
			.andExpect(jsonPath("$.data.store.name").value("청과마을"))
			.andExpect(jsonPath("$.data.store.openNow").value(true))
			.andExpect(jsonPath("$.data.store.distanceMeters").value(
				org.hamcrest.Matchers.both(org.hamcrest.Matchers.greaterThan(500))
					.and(org.hamcrest.Matchers.lessThan(650))))
			.andExpect(jsonPath("$.data.store.walkingMinutes").value(9));
	}

	@Test
	void recipesComeBackTitleMatchFirstThenFewestIngredients() throws Exception {
		mockMvc.perform(get("/products/" + product.getId()).header("Authorization", guest()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.recipes.length()").value(3))
			.andExpect(jsonPath("$.data.recipes[0].title").value("복숭아 화채"))
			.andExpect(jsonPath("$.data.recipes[1].title").value("여름 디저트"))
			.andExpect(jsonPath("$.data.recipes[2].title").value("과일 샐러드"))
			.andExpect(jsonPath("$.data.recipes[0].ingredientNames.length()").value(3));
	}

	@Test
	void aProductWithNoIngredientTagsSimplyHasNoRecipes() throws Exception {
		Product untagged = productRepository.saveAndFlush(new Product(
				product.getStore(), "대파 1단", ProductCategory.VEGETABLE, 2, 5_000, 3_500,
				LocalDateTime.now(), LocalDateTime.now().plusHours(3),
				"https://cdn.example.com/leek.jpg"));

		mockMvc.perform(get("/products/" + untagged.getId()).header("Authorization", guest()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.recipes.length()").value(0));
	}

	@Test
	void withoutAPositionTheDistanceFieldsAreAbsent() throws Exception {
		mockMvc.perform(get("/products/" + product.getId()).header("Authorization", guest()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.store.distanceMeters").doesNotExist())
			.andExpect(jsonPath("$.data.store.walkingMinutes").doesNotExist());
	}

	@Test
	void myOwnHoldTurnsTheButtonIntoAConfirmation() throws Exception {
		User consumer = userRepository.saveAndFlush(
				new User(UserRole.CONSUMER, "소비자", null, false, Instant.now()));
		product.hold(1);
		productRepository.saveAndFlush(product);
		Hold mine = holdRepository.saveAndFlush(
				new Hold(consumer, product, 1, Instant.now().plusSeconds(600)));

		mockMvc.perform(get("/products/" + product.getId())
				.header("Authorization", "Bearer " + tokenProvider.createAccessToken(
					TokenRealm.USER, consumer.getId(), "CONSUMER")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.holdButton").value("ALREADY_HOLDING"))
			.andExpect(jsonPath("$.data.myHoldId").value(mine.getId()));
	}

	@Test
	void aSoldOutProductSaysSoOnTheButton() throws Exception {
		product.hold(3);
		productRepository.saveAndFlush(product);

		mockMvc.perform(get("/products/" + product.getId()).header("Authorization", guest()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.holdButton").value("SOLD_OUT"));
	}

	@Test
	void holdingASoldOutProductStillShowsTheConfirmation() throws Exception {
		User consumer = userRepository.saveAndFlush(
				new User(UserRole.CONSUMER, "소비자", null, false, Instant.now()));
		product.hold(3);
		productRepository.saveAndFlush(product);
		holdRepository.saveAndFlush(new Hold(consumer, product, 3, Instant.now().plusSeconds(600)));

		mockMvc.perform(get("/products/" + product.getId())
				.header("Authorization", "Bearer " + tokenProvider.createAccessToken(
					TokenRealm.USER, consumer.getId(), "CONSUMER")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.holdButton")
				.value("ALREADY_HOLDING"));
	}

	@Test
	void aProductPastItsPickupDeadlineReadsAsClosed() throws Exception {
		Product stale = productRepository.saveAndFlush(new Product(
				product.getStore(), "지난 상품", ProductCategory.FRUIT, 2, 10_000, 4_000,
				LocalDateTime.now().minusHours(2), LocalDateTime.now().minusMinutes(30),
				"https://cdn.example.com/old.jpg"));

		mockMvc.perform(get("/products/" + stale.getId()).header("Authorization", guest()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.holdButton").value("CLOSED"));
	}

	@Test
	void aClosedStoreReportsOpenNowFalse() throws Exception {
		Store closed = new Store(
				userRepository.saveAndFlush(
					new User(UserRole.OWNER, "휴무점주", null, false, Instant.now())),
				"휴무가게", "04524", "서울 마포구 망원로 30", null, "02-2222-2222",
				new BigDecimal("37.556000"), new BigDecimal("126.901000"),
				LocalTime.of(3, 0), LocalTime.of(3, 1));
		closed.replaceCategories(Set.of(StoreCategory.FRUIT));
		closed.replaceBusinessDays(EnumSet.allOf(DayOfWeek.class));
		closed.approve();
		storeRepository.saveAndFlush(closed);
		LocalDateTime pickupEndAt = LocalDateTime.now().plusHours(5);
		Product other = productRepository.saveAndFlush(new Product(
				closed, "새벽 상품", ProductCategory.FRUIT, 1, 10_000, 4_000,
				pickupEndAt.minusHours(1), pickupEndAt, "https://cdn.example.com/n.jpg"));

		mockMvc.perform(get("/products/" + other.getId()).header("Authorization", guest()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.store.openNow")
				.value(java.time.LocalTime.now().isBefore(LocalTime.of(3, 1))
					&& !java.time.LocalTime.now().isBefore(LocalTime.of(3, 0))));
	}

	@Test
	void aProductOfAnUnapprovedStoreIsNotFound() throws Exception {
		Store pending = new Store(
				userRepository.saveAndFlush(
					new User(UserRole.OWNER, "미승인점주", null, false, Instant.now())),
				"미승인가게", "04524", "서울 마포구 망원로 99", null, "02-9999-9999",
				new BigDecimal("37.556000"), new BigDecimal("126.901000"),
				LocalTime.of(9, 0), LocalTime.of(21, 0));
		pending.replaceCategories(Set.of(StoreCategory.FRUIT));
		pending.replaceBusinessDays(EnumSet.allOf(DayOfWeek.class));
		storeRepository.saveAndFlush(pending);
		LocalDateTime pickupEndAt = LocalDateTime.now().plusHours(3);
		Product hidden = productRepository.saveAndFlush(new Product(
				pending, "숨은 상품", ProductCategory.FRUIT, 5, 10_000, 4_000,
				pickupEndAt.minusHours(1), pickupEndAt, "https://cdn.example.com/x.jpg"));

		mockMvc.perform(get("/products/" + hidden.getId()).header("Authorization", guest()))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code")
				.value("PRODUCT_NOT_FOUND"));
	}

	@Test
	void halfAPositionIsRejected() throws Exception {
		mockMvc.perform(get("/products/" + product.getId() + "?lat=37.55")
				.header("Authorization", guest()))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors.positionComplete").exists());
	}

	@Test
	void theDetailIsClosedToOwnersAndToNobody() throws Exception {
		String url = "/products/" + product.getId();
		mockMvc.perform(get(url).header("Authorization",
				"Bearer " + tokenProvider.createAccessToken(TokenRealm.USER, 1L, "OWNER")))
			.andExpect(status().isForbidden());
		mockMvc.perform(get(url)).andExpect(status().isUnauthorized());
	}
}
