package com.swyp.backend.terms.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.swyp.backend.AppDataCleaner;
import com.swyp.backend.RedisTestcontainersConfiguration;
import com.swyp.backend.TestcontainersConfiguration;
import com.swyp.backend.terms.entity.TermsDocument;
import com.swyp.backend.terms.entity.TermsRequirement;
import com.swyp.backend.terms.entity.TermsType;
import com.swyp.backend.terms.repository.TermsDocumentRepository;
import com.swyp.backend.user.entity.UserRole;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, RedisTestcontainersConfiguration.class})
class TermsControllerTest {

	@Autowired
	AppDataCleaner appDataCleaner;

	@Autowired
	MockMvc mockMvc;

	@Autowired
	TermsDocumentRepository termsDocumentRepository;

	@BeforeEach
	void setUp() {
		appDataCleaner.clear();
	}

	@AfterEach
	void tearDown() {
		appDataCleaner.clear();
	}

	@Test
	void theSignupScreenGetsThatAppsDocumentsInTheOrderItShowsThem() throws Exception {
		publish(UserRole.CONSUMER, TermsType.PRIVACY_POLICY, 1, TermsRequirement.NOTICE);
		publish(UserRole.CONSUMER, TermsType.MARKETING, 1, TermsRequirement.OPTIONAL);
		publish(UserRole.CONSUMER, TermsType.SERVICE, 1, TermsRequirement.REQUIRED);
		publish(UserRole.OWNER, TermsType.SERVICE, 1, TermsRequirement.REQUIRED);

		mockMvc.perform(get("/terms").param("role", "CONSUMER"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.documents.length()").value(3))
			.andExpect(jsonPath("$.data.documents[0].type").value("SERVICE"))
			.andExpect(jsonPath("$.data.documents[0].requirement").value("REQUIRED"))
			.andExpect(jsonPath("$.data.documents[1].type").value("MARKETING"))
			.andExpect(jsonPath("$.data.documents[1].requirement").value("OPTIONAL"))
			.andExpect(jsonPath("$.data.documents[2].type").value("PRIVACY_POLICY"))
			.andExpect(jsonPath("$.data.documents[2].requirement").value("NOTICE"));
	}

	@Test
	void theListLeavesTheFullTextForTheDocumentView() throws Exception {
		publish(UserRole.CONSUMER, TermsType.SERVICE, 1, TermsRequirement.REQUIRED);

		mockMvc.perform(get("/terms").param("role", "CONSUMER"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.documents[0].contentMarkdown").doesNotExist());
	}

	@Test
	void onlyTheLatestVersionOfEachDocumentIsShownForAgreement() throws Exception {
		publish(UserRole.CONSUMER, TermsType.SERVICE, 1, TermsRequirement.REQUIRED);
		TermsDocument revised = publish(UserRole.CONSUMER, TermsType.SERVICE, 2, TermsRequirement.REQUIRED);

		mockMvc.perform(get("/terms").param("role", "CONSUMER"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.documents.length()").value(1))
			.andExpect(jsonPath("$.data.documents[0].id").value(revised.getId()))
			.andExpect(jsonPath("$.data.documents[0].version").value(2));
	}

	@Test
	void aVersionSomeoneAgreedToStaysReadableAfterARevision() throws Exception {
		TermsDocument original = publish(UserRole.CONSUMER, TermsType.SERVICE, 1, TermsRequirement.REQUIRED);
		publish(UserRole.CONSUMER, TermsType.SERVICE, 2, TermsRequirement.REQUIRED);

		mockMvc.perform(get("/terms/" + original.getId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.version").value(1))
			.andExpect(jsonPath("$.data.contentMarkdown").value(original.getContent()));
	}

	@Test
	void theDocumentViewCarriesTheFullText() throws Exception {
		TermsDocument document = termsDocumentRepository.saveAndFlush(new TermsDocument(
			UserRole.OWNER, TermsType.PRIVACY_POLICY, 1, "맹그로 개인정보처리방침", TermsRequirement.NOTICE,
			"#### 제1조 (목적)\n\n본 방침은 ...", LocalDate.of(2026, 10, 1)));

		mockMvc.perform(get("/terms/" + document.getId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.role").value("OWNER"))
			.andExpect(jsonPath("$.data.type").value("PRIVACY_POLICY"))
			.andExpect(jsonPath("$.data.title").value("맹그로 개인정보처리방침"))
			.andExpect(jsonPath("$.data.effectiveDate").value("2026-10-01"))
			.andExpect(jsonPath("$.data.contentMarkdown").value("#### 제1조 (목적)\n\n본 방침은 ..."));
	}

	@Test
	void anAppWithNothingPublishedGetsAnEmptyListRatherThanAnError() throws Exception {
		mockMvc.perform(get("/terms").param("role", "OWNER"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.documents.length()").value(0));
	}

	@Test
	void anUnknownDocumentIsNotFound() throws Exception {
		mockMvc.perform(get("/terms/999999"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("TERMS_DOCUMENT_NOT_FOUND"));
	}

	@Test
	void theListHasToKnowWhichAppIsAsking() throws Exception {
		mockMvc.perform(get("/terms"))
			.andExpect(status().isBadRequest());
	}

	private TermsDocument publish(UserRole role, TermsType type, int version, TermsRequirement requirement) {
		return termsDocumentRepository.saveAndFlush(new TermsDocument(
			role, type, version, type + " v" + version, requirement, "## " + type + " v" + version, null));
	}
}
