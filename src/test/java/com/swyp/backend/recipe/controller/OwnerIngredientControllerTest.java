package com.swyp.backend.recipe.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.swyp.backend.RedisTestcontainersConfiguration;
import com.swyp.backend.TestcontainersConfiguration;
import com.swyp.backend.common.security.JwtTokenProvider;
import com.swyp.backend.common.security.TokenRealm;
import com.swyp.backend.product.PhotoFixture;
import com.swyp.backend.product.repository.ProductRepository;
import com.swyp.backend.recipe.entity.Ingredient;
import com.swyp.backend.recipe.repository.IngredientRepository;
import com.swyp.backend.store.entity.Store;
import com.swyp.backend.store.repository.StoreRepository;
import com.swyp.backend.user.entity.User;
import com.swyp.backend.user.entity.UserRole;
import com.swyp.backend.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, RedisTestcontainersConfiguration.class})
class OwnerIngredientControllerTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	UserRepository userRepository;

	@Autowired
	StoreRepository storeRepository;

	@Autowired
	ProductRepository productRepository;

	@Autowired
	IngredientRepository ingredientRepository;

	@Autowired
	JwtTokenProvider tokenProvider;

	private String token;
	private String photoUrl;

	@BeforeEach
	void setUp() throws Exception {
		clearAll();

		User owner = userRepository.saveAndFlush(
			new User(UserRole.OWNER, "테스트점주", null, false, Instant.now()));
		storeRepository.saveAndFlush(new Store(
			owner, "테스트가게", "04524", "서울특별시 강남구 역삼로 1", null, "0212345678",
			new BigDecimal("37.500000"), new BigDecimal("127.030000"),
			LocalTime.of(9, 0), LocalTime.of(21, 0)));
		token = tokenProvider.createAccessToken(TokenRealm.USER, owner.getId(), UserRole.OWNER.name());
		photoUrl = PhotoFixture.uploadedPhotoUrl(mockMvc, token);
	}

	@AfterEach
	void tearDown() {
		clearAll();
	}

	@Test
	void searchIngredients_findsThePartOfTheNameTheOwnerTyped_shortestFirst() throws Exception {
		ingredient("복숭아", "청과");
		ingredient("백도 복숭아", "청과");
		ingredient("사과", "청과");

		search("복숭")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(2))
			.andExpect(jsonPath("$.data[0].name").value("복숭아"))
			.andExpect(jsonPath("$.data[0].category").value("청과"))
			.andExpect(jsonPath("$.data[0].id").isNumber())
			.andExpect(jsonPath("$.data[1].name").value("백도 복숭아"));
	}

	@Test
	void searchIngredients_stopsAtTheRequestedSize() throws Exception {
		ingredient("복숭아", "청과");
		ingredient("백도 복숭아", "청과");

		mockMvc.perform(get("/owner/ingredients?query=복숭&size=1")
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(1))
			.andExpect(jsonPath("$.data[0].name").value("복숭아"));
	}

	@Test
	void searchIngredients_withNothingTypedYet_isEmptyRatherThanAnError() throws Exception {
		ingredient("복숭아", "청과");

		search("   ")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(0));
	}

	@Test
	void searchIngredients_matchingNothing_isEmpty() throws Exception {
		ingredient("복숭아", "청과");

		search("고등어")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(0));
	}

	@Test
	void searchIngredients_withAConsumerToken_isForbidden() throws Exception {
		User consumer = userRepository.saveAndFlush(
			new User(UserRole.CONSUMER, "소비자", null, false, Instant.now()));
		String consumerToken = tokenProvider.createAccessToken(
			TokenRealm.USER, consumer.getId(), UserRole.CONSUMER.name());

		mockMvc.perform(get("/owner/ingredients?query=복숭")
				.header("Authorization", "Bearer " + consumerToken))
			.andExpect(status().isForbidden());
	}

	@Test
	void productDetail_showsTheTagNamesInsteadOfBareIds() throws Exception {
		Ingredient peach = ingredient("복숭아", "청과");
		Ingredient apple = ingredient("사과", null);

		String body = """
			{"name":"복숭아 4입","category":"FRUIT","initialQty":10,"originalPrice":10000,\
			"salePrice":4000,"photoUrl":"%s","ingredientTags":[%d,%d]}"""
			.formatted(photoUrl, peach.getId(), apple.getId());
		String created = mockMvc.perform(post("/owner/products")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.ingredientTags.length()").value(2))
			.andReturn().getResponse().getContentAsString();
		Number productId = com.jayway.jsonpath.JsonPath.read(created, "$.data.id");

		mockMvc.perform(get("/owner/products/" + productId.longValue())
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.ingredientTags.length()").value(2))
			.andExpect(jsonPath("$.data.ingredientTags[0].id").value(peach.getId()))
			.andExpect(jsonPath("$.data.ingredientTags[0].name").value("복숭아"))
			.andExpect(jsonPath("$.data.ingredientTags[0].category").value("청과"))
			.andExpect(jsonPath("$.data.ingredientTags[1].name").value("사과"));
	}

	private ResultActions search(String query) throws Exception {
		return mockMvc.perform(get("/owner/ingredients")
			.param("query", query)
			.header("Authorization", "Bearer " + token));
	}

	private Ingredient ingredient(String name, String category) {
		return ingredientRepository.saveAndFlush(
			Ingredient.tag(name, name.replace(" ", "").toLowerCase(), category));
	}

	private void clearAll() {
		productRepository.deleteAll();
		storeRepository.deleteAll();
		userRepository.deleteAll();
		ingredientRepository.deleteAll();
	}
}
