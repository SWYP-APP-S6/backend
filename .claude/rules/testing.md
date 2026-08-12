---
paths:
  - "src/test/**"
---

# 테스트 작성 규칙

- JUnit 5. 통합/영속 테스트는 **Testcontainers로 실제 PostgreSQL**에 대해 실행 —
  `TestcontainersConfiguration`의 `@ServiceConnection` 컨테이너를 부팅 시 띄운다(H2 방언 불일치 회피).
  **Docker 필요.**
- 슬라이스 선택:
  - **영속 계층** → `@DataJpaTest` + `@AutoConfigureTestDatabase(replace = Replace.NONE)` +
    `@Import({TestcontainersConfiguration.class, ...})`. 레퍼런스: `admin/repository/AdminRepositoryTest`.
  - **웹 계층** → `@WebMvcTest(XxxController.class)` + `@AutoConfigureMockMvc(addFilters = false)` +
    실제 협력자 `@Import`. 레퍼런스: `ping/controller/PingControllerTest`.
  - **`@RestControllerAdvice` 단독 검증** → `MockMvcBuilders.standaloneSetup(ctrl).setControllerAdvice(handler)`.
    (`@WebMvcTest(중첩 static 컨트롤러)`는 그 컨트롤러를 빈 등록 못 해 요청이 정적리소스→
    `NoResourceFoundException`으로 오탐되니 쓰지 말 것.)
- **Boot 4.1 슬라이스 애노테이션 패키지(이동됨 — 추측 금지)**:
  - `@WebMvcTest`·`@AutoConfigureMockMvc` = `org.springframework.boot.webmvc.test.autoconfigure.*`
  - `@DataJpaTest` = `org.springframework.boot.data.jpa.test.autoconfigure.*`
  - `@AutoConfigureTestDatabase` = `org.springframework.boot.jdbc.test.autoconfigure.*`
- 완료 판정은 `./gradlew build`(컴파일+테스트) 초록으로.
