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
	private Ingredient peach;
	private Ingredient egg;

	@BeforeEach
	void setUp() {
		clearAll();
		recommender.reset();

		peach = ingredientRepository.saveAndFlush(Ingredient.tag("복숭아", "복숭아", "과일"));
		egg = ingredientRepository.saveAndFlush(Ingredient.tag("달걀", "달걀", "달걀·유제품"));
		ingredientRepository.saveAndFlush(Ingredient.aliasOf(egg, "계란", "계란"));
		ingredientRepository.saveAndFlush(new Ingredient("소금", "소금", null));

		User owner = userRepository.saveAndFlush(
			new User(UserRole.OWNER, "테스트점주", null, false, Instant.now()));
		token = tokenProvider.createAccessToken(TokenRealm.USER, owner.getId(), UserRole.OWNER.name());
	}

	@AfterEach
	void tearDown() {
		clearAll();
	}

	@Test
	void recommendTags_offersTheModelOnlyTheTagList() throws Exception {
		recommend("복숭아 4입").andExpect(status().isOk());

		assertThat(recommender.offeredTagNames())
			.as("aliases and untagged dictionary rows are not offered")
			.containsExactlyInAnyOrder("복숭아", "달걀");
	}

	@Test
	void recommendTags_keepsTheTagsTheModelPicked_andReachesATagThroughItsAlias() throws Exception {
		recommender.answer(List.of("복숭아", "계란"));

		recommend("복숭아 달걀 샌드위치")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(2))
			.andExpect(jsonPath("$.data[0].id").value(peach.getId()))
			.andExpect(jsonPath("$.data[0].category").value("과일"))
			.andExpect(jsonPath("$.data[1].id").value(egg.getId()))
			.andExpect(jsonPath("$.data[1].name").value("달걀"));
	}

	@Test
	void recommendTags_dropsWhatIsNotATag_withoutWritingToTheDictionary() throws Exception {
		long rowsBefore = ingredientRepository.count();
		recommender.answer(List.of("과일", "소금", "8g", "   ", "복숭아", "복숭아"));

		recommend("복숭아")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(1))
			.andExpect(jsonPath("$.data[0].name").value("복숭아"));

		assertThat(ingredientRepository.count())
			.as("a recommendation never adds a dictionary row")
			.isEqualTo(rowsBefore);
	}

	@Test
	void recommendTags_stopsAtFive() throws Exception {
		List<String> sixTags = List.of("가", "나", "다", "라", "마", "바");
		for (String name : sixTags) {
			ingredientRepository.saveAndFlush(Ingredient.tag(name, name, "채소"));
		}
		recommender.answer(sixTags);

		recommend("모둠 채소")
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
	void recommendTags_forANameLongerThanAProductCanHave_skipsTheModel() throws Exception {
		recommender.answer(List.of("복숭아"));

		recommend("복".repeat(31))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(0));

		assertThat(recommender.offeredTagNames()).isNull();
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

	private void clearAll() {
		ingredientRepository.deleteAll();
		userRepository.deleteAll();
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
		private final AtomicReference<List<String>> offered = new AtomicReference<>();

		void answer(List<String> tagNames) {
			this.answer.set(tagNames);
		}

		void reset() {
			answer.set(List.of());
			offered.set(null);
		}

		List<String> offeredTagNames() {
			return offered.get();
		}

		@Override
		public List<String> recommendTagNames(String productName, List<String> tagNames, int limit) {
			offered.set(tagNames);
			return answer.get();
		}
	}
}
