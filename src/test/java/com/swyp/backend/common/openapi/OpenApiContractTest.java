package com.swyp.backend.common.openapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.swyp.backend.RedisTestcontainersConfiguration;
import com.swyp.backend.TestcontainersConfiguration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, RedisTestcontainersConfiguration.class})
class OpenApiContractTest {

	private static final List<String> SPRING_OWNED_SCHEMAS = List.of("Pageable", "PageableObject");

	private static final List<String> ERROR_STATUSES = List.of("400", "401", "403", "429", "500");

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ObjectMapper objectMapper;

	private static JsonNode appSpec;

	@BeforeAll
	static void resetCache() {
		appSpec = null;
	}

	private JsonNode spec() throws Exception {
		if (appSpec == null) {
			appSpec = objectMapper.readTree(mockMvc.perform(get("/v3/api-docs/app"))
					.andReturn().getResponse().getContentAsString());
		}
		return appSpec;
	}

	private record Endpoint(String path, String method, JsonNode operation) {}

	private List<Endpoint> endpoints() throws Exception {
		List<Endpoint> endpoints = new ArrayList<>();
		JsonNode paths = spec().get("paths");
		paths.propertyNames().forEach(path ->
				paths.get(path).propertyNames().forEach(method ->
						endpoints.add(new Endpoint(path, method, paths.get(path).get(method)))));
		return endpoints;
	}

	@Test
	void theAppSpecCarriesNoBackOfficeEndpoint() throws Exception {
		assertThat(endpoints()).isNotEmpty()
				.noneMatch(endpoint -> endpoint.path().startsWith("/admin"));
	}

	@Test
	void everyOperationIdIsUniqueAndUnsuffixed() throws Exception {
		List<String> operationIds = endpoints().stream()
				.map(endpoint -> endpoint.operation().get("operationId").asString())
				.toList();

		assertThat(operationIds).doesNotHaveDuplicates();
		assertThat(operationIds).allSatisfy(operationId -> assertThat(operationId)
				.as("springdoc appends _1 to a clashing method name, which renames the generated "
						+ "client method without anyone editing the endpoint")
				.doesNotMatch(".*_\\d+$"));
	}

	@Test
	void everyOperationDocumentsTheErrorEnvelope() throws Exception {
		assertThat(endpoints()).allSatisfy(endpoint -> {
			JsonNode responses = endpoint.operation().get("responses");
			assertThat(ERROR_STATUSES).allSatisfy(status -> assertThat(responses.has(status))
					.as("%s %s is missing the %s response", endpoint.method(), endpoint.path(), status)
					.isTrue());
			assertThat(responses.get("500").at("/content/application~1json/schema/$ref").asString())
					.isEqualTo("#/components/schemas/ErrorResponse");
		});
		assertThat(spec().at("/components/schemas/ErrorResponse").isObject()).isTrue();
	}

	@Test
	void everyOperationIsTaggedWithAReadableName() throws Exception {
		assertThat(endpoints()).allSatisfy(endpoint -> {
			JsonNode tags = endpoint.operation().get("tags");
			assertThat(tags).as("%s has no tag", endpoint.path()).isNotNull();
			assertThat(tags.get(0).asString())
					.as("an untagged controller becomes xxx-controller, which the generator turns "
							+ "into a class name the app has to read")
					.doesNotEndWith("-controller");
		});
	}

	@Test
	void responseSchemasDeclareWhichPropertiesAlwaysArrive() throws Exception {
		JsonNode schemas = spec().get("components").get("schemas");
		List<String> withoutRequired = new ArrayList<>();
		schemas.propertyNames().forEach(name -> {
			JsonNode schema = schemas.get(name);
			if (SPRING_OWNED_SCHEMAS.contains(name)) {
				return;
			}
			if (schema.has("properties") && !schema.has("required")) {
				withoutRequired.add(name);
			}
		});

		assertThat(withoutRequired)
				.as("a property outside required generates as nullable, so the app either sprinkles "
						+ "!! or null-checks a field the server always sends")
				.isEmpty();
	}

	@Test
	void aNullablePropertyStaysOutOfRequired() throws Exception {
		List<String> declared = new ArrayList<>();
		spec().at("/components/schemas/StoreProductsResponse/required")
				.forEach(node -> declared.add(node.asString()));

		assertThat(declared)
				.as("the store sheet omits the distance when the caller sends no position")
				.doesNotContain("distanceMeters", "walkingMinutes")
				.contains("storeId", "name", "productCount");
	}
}
