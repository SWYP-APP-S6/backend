package com.swyp.backend.recipe.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.swyp.backend.RedisTestcontainersConfiguration;
import com.swyp.backend.TestcontainersConfiguration;
import com.swyp.backend.common.security.JwtTokenProvider;
import com.swyp.backend.common.security.TokenRealm;
import com.swyp.backend.recipe.entity.Ingredient;
import com.swyp.backend.recipe.repository.IngredientRepository;
import com.swyp.backend.recipe.service.IngredientTagRecommender;
import com.swyp.backend.user.entity.User;
import com.swyp.backend.user.entity.UserRole;
import com.swyp.backend.user.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, RedisTestcontainersConfiguration.class})
class OwnerIngredientRecommendationTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	UserRepository userRepository;

	@Autowired
	IngredientRepository ingredientRepository;

	@Autowired
	StubTagRecommender recommender;

	@Autowired
	JwtTokenProvider tokenProvider;

	private String token;

	@BeforeEach
	void setUp() {
		ingredientRepository.deleteAll();
		userRepository.deleteAll();

		User owner = userRepository.saveAndFlush(
			new User(UserRole.OWNER, "테스트점주", null, false, Instant.now()));
		token = tokenProvider.createAccessToken(TokenRealm.USER, owner.getId(), UserRole.OWNER.name());
	}

	@AfterEach
	void tearDown() {
		ingredientRepository.deleteAll();
		userRepository.deleteAll();
	}

	@Test
	void recommendTags_keepsTheTagTheDictionaryKnows_andWritesTheOneItDoesNot() throws Exception {
		Ingredient peach = ingredientRepository.saveAndFlush(new Ingredient("복숭아", "복숭아", "청과"));
		recommender.answer(List.of("복숭아", "과일"));

		recommend("복숭아 4입")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(2))
			.andExpect(jsonPath("$.data[0].id").value(peach.getId()))
			.andExpect(jsonPath("$.data[0].name").value("복숭아"))
			.andExpect(jsonPath("$.data[1].name").value("과일"));

		assertThat(ingredientRepository.findByNormKey("과일"))
			.as("the dictionary gains the tag the owner can now pick")
			.isPresent();
	}

	@Test
	void recommendTags_twiceForTheSameProduct_doesNotDuplicateTheDictionaryRow() throws Exception {
		recommender.answer(List.of("과일"));

		recommend("복숭아").andExpect(status().isOk());
		recommend("복숭아").andExpect(status().isOk());

		assertThat(ingredientRepository.findAll())
			.as("a repeated recommendation maps onto the row it made the first time")
			.hasSize(1);
	}

	@Test
	void recommendTags_dropsWhatCannotBecomeATag() throws Exception {
		recommender.answer(List.of("과일", "8g", "   ", "복숭아"));

		recommend("복숭아")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(2))
			.andExpect(jsonPath("$.data[0].name").value("과일"))
			.andExpect(jsonPath("$.data[1].name").value("복숭아"));
	}

	@Test
	void recommendTags_stopsAtFive() throws Exception {
		recommender.answer(List.of("과일", "복숭아", "제철", "여름과일", "생과일", "후식"));

		recommend("복숭아")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(5));
	}

	@Test
	void recommendTags_whenTheModelSaysNothing_isEmptyRatherThanAnError() throws Exception {
		recommender.answer(List.of());

		recommend("복숭아")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(0));
	}

	@Test
	void recommendTags_withAConsumerToken_isForbidden() throws Exception {
		User consumer = userRepository.saveAndFlush(
			new User(UserRole.CONSUMER, "소비자", null, false, Instant.now()));
		String consumerToken = tokenProvider.createAccessToken(
			TokenRealm.USER, consumer.getId(), UserRole.CONSUMER.name());

		mockMvc.perform(get("/owner/ingredients/recommendations?name=복숭아")
				.header("Authorization", "Bearer " + consumerToken))
			.andExpect(status().isForbidden());
	}

	private ResultActions recommend(String productName) throws Exception {
		return mockMvc.perform(get("/owner/ingredients/recommendations")
			.param("name", productName)
			.header("Authorization", "Bearer " + token));
	}

	@TestConfiguration
	static class StubTagRecommenderConfiguration {

		@Bean
		@Primary
		StubTagRecommender stubTagRecommender() {
			return new StubTagRecommender();
		}
	}

	static class StubTagRecommender implements IngredientTagRecommender {

		private final AtomicReference<List<String>> answer = new AtomicReference<>(List.of());

		void answer(List<String> tagNames) {
			this.answer.set(tagNames);
		}

		@Override
		public List<String> recommendTagNames(String productName, int limit) {
			return answer.get();
		}
	}
}
