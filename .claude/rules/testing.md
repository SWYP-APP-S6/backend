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
  - `TestEntityManager` = `org.springframework.boot.jpa.test.autoconfigure.*` (note: different module/package
    root than `@DataJpaTest` above — not `.data.jpa.test.`)
- **`@Transactional` 테스트 안에서 `POST /holds`·`HoldService.create`를 부르지 않는다.** 생성은
  `NOT_SUPPORTED`로 테스트 트랜잭션을 밀어내고 자기 트랜잭션을 열기 때문에 미커밋 픽스처가 보이지 않아 404가
  난다. 그런 테스트는 `AppDataCleaner.clear()`를 `@BeforeEach`/`@AfterEach`에 두고 커밋된 데이터로 돈다.
  이때 `LocalTime.MAX`는 PostgreSQL `time`에서 24:00:00으로 올림돼 영업시간 판정이 틀어지므로 `23:59`를 쓴다.
- 완료 판정은 `./gradlew build`(컴파일+테스트) 초록으로.
