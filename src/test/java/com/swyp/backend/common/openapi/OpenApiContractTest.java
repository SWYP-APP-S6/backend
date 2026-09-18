package com.swyp.backend.common.openapi;

import com.swyp.backend.RedisTestcontainersConfiguration;
import com.swyp.backend.TestcontainersConfiguration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, RedisTestcontainersConfiguration.class})
class OpenApiContractTest {

	private static final List<String> SPRING_OWNED_SCHEMAS = List.of("Pageable", "PageableObject");

	private static final List<String> WRAPPERS_OF_ONE_OPTIONAL_VALUE =
			List.of("ActiveHoldResponse", "MyLocationResponse");

	private static final String MULTIPART_FORM_DATA = "multipart/form-data";

	private static final List<String> ERROR_STATUSES = List.of("400", "401", "403", "429", "500");

	private static final List<String> NON_APP_PATH_PREFIXES = List.of("/admin/", "/dev/");

	@Autowired
	MockMvc mockMvc;

	@Autowired
	@Qualifier("requestMappingHandlerMapping")
	RequestMappingHandlerMapping handlerMapping;

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
			assertThat(tags.get(0).asString())
					.as("the generator strips a tag down to a class name, so a non-ASCII tag leaves "
							+ "nothing and every operation collapses into one DefaultApi")
					.matches("[A-Za-z][A-Za-z0-9]*");
		});
	}

	@Test
	void responseSchemasDeclareWhichPropertiesAlwaysArrive() throws Exception {
		JsonNode schemas = spec().get("components").get("schemas");
		List<String> withoutRequired = new ArrayList<>();
		schemas.propertyNames().forEach(name -> {
			JsonNode schema = schemas.get(name);
			if (SPRING_OWNED_SCHEMAS.contains(name)
					|| WRAPPERS_OF_ONE_OPTIONAL_VALUE.contains(name)) {
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
	void aFileUploadIsDeclaredAsMultipart() throws Exception {
		List<String> misdeclared = new ArrayList<>();
		for (Endpoint endpoint : endpoints()) {
			JsonNode content = endpoint.operation().at("/requestBody/content");
			if (!content.isObject()) {
				continue;
			}
			content.propertyNames().forEach(mediaType -> {
				if (!MULTIPART_FORM_DATA.equals(mediaType) && carriesBinary(content.get(mediaType))) {
					misdeclared.add(endpoint.method() + " " + endpoint.path() + " -> " + mediaType);
				}
			});
		}

		assertThat(misdeclared)
				.as("springdoc falls back to application/json unless the mapping declares consumes, "
						+ "and the generator then builds a @Body call the endpoint answers with 415")
				.isEmpty();
	}

	@Test
	void aPagedListDeclaresPageAndSizeButNeitherPageableNorSort() throws Exception {
		List<String> checked = new ArrayList<>();
		List<String> misdeclared = new ArrayList<>();
		for (Map.Entry<RequestMappingInfo, HandlerMethod> handler
				: handlerMapping.getHandlerMethods().entrySet()) {
			if (!takesPageable(handler.getValue())) {
				continue;
			}
			for (String path : handler.getKey().getPatternValues()) {
				if (NON_APP_PATH_PREFIXES.stream().anyMatch(path::startsWith)) {
					continue;
				}
				for (RequestMethod method : handler.getKey().getMethodsCondition().getMethods()) {
					checked.add(method + " " + path);
					List<String> names = parameterNames(
							spec().path("paths").path(path).path(method.name().toLowerCase(Locale.ROOT)));
					if (!names.containsAll(List.of("page", "size"))
							|| names.contains("pageable") || names.contains("sort")) {
						misdeclared.add(method + " " + path + " " + names);
					}
				}
			}
		}

		assertThat(checked).isNotEmpty();
		assertThat(misdeclared)
				.as("a bare Pageable documents one ?pageable=<object> the server never reads, a "
						+ "@ParameterObject one advertises a sort the server overrides, and a hidden one "
						+ "without @PageQueryParams leaves the generated client no way to page")
				.isEmpty();
	}

	private static boolean takesPageable(HandlerMethod handler) {
		return Arrays.stream(handler.getMethodParameters())
				.anyMatch(parameter -> Pageable.class.isAssignableFrom(parameter.getParameterType()));
	}

	private static List<String> parameterNames(JsonNode operation) {
		List<String> names = new ArrayList<>();
		operation.path("parameters").forEach(parameter -> names.add(parameter.get("name").asString()));
		return names;
	}

	private static boolean carriesBinary(JsonNode media) {
		JsonNode properties = media.at("/schema/properties");
		if (!properties.isObject()) {
			return false;
		}
		List<String> binary = new ArrayList<>();
		properties.propertyNames().forEach(property -> {
			JsonNode format = properties.get(property).get("format");
			if (format != null && "binary".equals(format.asString())) {
				binary.add(property);
			}
		});
		return !binary.isEmpty();
	}

	@Test
	void anOperationDeclaresTheBusinessFailuresItAnswersWith() throws Exception {
		JsonNode cancel = spec().at("/paths/~1owner~1holds~1cancel/post/responses");

		assertThat(cancel.path("409").at("/content/application~1json/schema/$ref").asString())
				.isEqualTo("#/components/schemas/ErrorResponse");
		assertThat(cancel.path("409").path("description").asString())
				.as("the app branches on the code, so a conflict the operation can answer with has to "
						+ "reach the spec it generates from")
				.contains("PRODUCT_NOT_SHORT_OF_STOCK", "HOLD_ALREADY_RESOLVED", "HOLD_ALREADY_EXPIRED")
				.contains("이미 처리된 찜입니다.");
		assertThat(cancel.path("404").path("description").asString())
				.contains("HOLD_NOT_FOUND", "STORE_NOT_REGISTERED", "PRODUCT_NOT_FOUND");
	}

	@Test
	void everyDeclaredErrorCodeNamesOneThatExists() {
		List<String> unknown = new ArrayList<>();
		handlerMapping.getHandlerMethods().values().forEach(handler ->
				AnnotatedElementUtils
						.findMergedRepeatableAnnotations(handler.getMethod(), ApiErrorCodes.class)
						.forEach(declared -> Arrays.stream(declared.codes())
								.filter(name -> Arrays.stream(declared.in().getEnumConstants())
										.noneMatch(code -> code.name().equals(name)))
								.forEach(name -> unknown.add(handler.getMethod().getName() + " -> "
										+ declared.in().getSimpleName() + "." + name))));

		assertThat(unknown)
				.as("a renamed code would drop out of the spec without anyone noticing")
				.isEmpty();
	}

	@Test
	void aFieldThatOnlyOneBranchOfTheResponseCarriesStaysOutOfRequired() throws Exception {
		List<String> declared = new ArrayList<>();
		spec().at("/components/schemas/KakaoLoginResponse/required")
				.forEach(node -> declared.add(node.asString()));

		assertThat(declared)
				.as("a login answers with tokens or with a signup token, never both, so a required "
						+ "token generates as non-null and the app dies parsing the other branch")
				.containsExactly("registered");
	}

	@Test
	void anOperationThatAnswersCreatedDocumentsThatStatus() throws Exception {
		List<String> misdeclared = new ArrayList<>();
		for (Map.Entry<RequestMappingInfo, HandlerMethod> handler
				: handlerMapping.getHandlerMethods().entrySet()) {
			ResponseStatus status = AnnotatedElementUtils.findMergedAnnotation(
					handler.getValue().getMethod(), ResponseStatus.class);
			if (status == null || status.value() != HttpStatus.CREATED) {
				continue;
			}
			for (String path : handler.getKey().getPatternValues()) {
				for (RequestMethod method : handler.getKey().getMethodsCondition().getMethods()) {
					JsonNode responses = spec()
							.path("paths").path(path).path(method.name().toLowerCase(Locale.ROOT))
							.path("responses");
					if (responses.isMissingNode() || responses.has("200") || !responses.has("201")) {
						misdeclared.add(method + " " + path);
					}
				}
			}
		}

		assertThat(misdeclared)
				.as("an endpoint documented as 200 while it answers 201 lies to every client that "
						+ "branches on the status, and to everyone reading the spec")
				.isEmpty();
	}

	@Test
	void aConsumerHoldDeclaresTheBusinessFailuresItAnswersWith() throws Exception {
		JsonNode create = spec().at("/paths/~1holds/post/responses");

		assertThat(create.path("409").path("description").asString())
				.as("the hold sheet branches on why a hold was refused")
				.contains("INSUFFICIENT_QTY", "PRODUCT_NOT_SELLABLE", "STORE_CLOSED_TODAY",
						"OTHER_STORE_HOLD_ACTIVE", "CANCEL_LIMIT_EXCEEDED");
		assertThat(create.path("404").path("description").asString()).contains("PRODUCT_NOT_FOUND");
		assertThat(create.path("400").path("description").asString())
				.as("a business 400 joins the envelope description instead of replacing it")
				.contains("HOLD_LIMIT_EXCEEDED", "VALIDATION_FAILED");

		JsonNode cancel = spec().at("/paths/~1holds~1{holdId}~1cancel/post/responses");
		assertThat(cancel.path("409").path("description").asString())
				.contains("HOLD_ALREADY_RESOLVED", "HOLD_ALREADY_EXPIRED");
		assertThat(cancel.path("404").path("description").asString()).contains("HOLD_NOT_FOUND");
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
