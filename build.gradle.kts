plugins {
	java
	id("org.springframework.boot") version "4.1.0"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "com.swyp"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(25)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-flyway")
	implementation("org.flywaydb:flyway-database-postgresql")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("org.springframework.boot:spring-boot-starter-restclient")
	// FCM HTTP v1 의 서비스 계정 JWT 서명 → 액세스 토큰 교환과 그 캐싱·갱신만 맡는다.
	// 발송 자체는 RestClient 로 직접 친다(firebase-admin 은 v1 에 배치 엔드포인트가 없어져
	// 재시도·에러코드 매핑 외에 남는 런타임 동작이 없고, guava·gRPC 를 함께 끌고 온다).
	implementation("com.google.auth:google-auth-library-oauth2-http:1.48.0")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.0")
	implementation("io.jsonwebtoken:jjwt-api:0.13.0")
	runtimeOnly("io.jsonwebtoken:jjwt-impl:0.13.0")
	runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.13.0")
	implementation("org.springframework.boot:spring-boot-starter-data-redis")
	compileOnly("org.projectlombok:lombok")
	annotationProcessor("org.projectlombok:lombok")
	testCompileOnly("org.projectlombok:lombok")
	testAnnotationProcessor("org.projectlombok:lombok")
	runtimeOnly("org.postgresql:postgresql")
	developmentOnly("org.springframework.boot:spring-boot-docker-compose")
	testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
	testImplementation("org.springframework.boot:spring-boot-starter-security-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testImplementation("org.springframework.boot:spring-boot-testcontainers")
	testImplementation("org.testcontainers:testcontainers-postgresql")
	testImplementation("com.tngtech.archunit:archunit-junit5:1.5.0")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
	useJUnitPlatform()
	// jwt.secret 에는 기본값이 없다(운영에서 조용히 공개 키로 떨어지지 않게 하려고).
	// 테스트 환경은 여기서 자기 키를 넘긴다 — CI 에도 `.env` 없이 그대로 적용된다.
	environment("JWT_SECRET", "test-only-secret-that-is-at-least-32-bytes-long")
	// 벽시계(LocalDateTime) 비교가 CI(UTC)와 로컬(KST)에서 갈리지 않도록 런타임과 같은 존으로 고정한다.
	systemProperty("user.timezone", "Asia/Seoul")
	// 푸시 아웃박스 배치는 운영 주기(3초)로 두면 테스트가 알림을 넣는 동안 끼어들어 같은 행의
	// push_state 를 바꾼다. 스케줄러는 사실상 꺼 두고, 배치를 검증하는 테스트가 직접 호출한다.
	systemProperty("notification.push.scan-interval", "1h")
}

// db/data 의 시드 SQL 은 psql 로 직접 넣는 운영 산출물이라 클래스패스에 올릴 이유가 없다.
// build/resources 단계에서 걷어내 jar 와 테스트 클래스패스 양쪽에서 제외한다(약 5MB).
tasks.processResources {
	exclude("db/data/**")
}

// `.env` 는 docker compose 가 읽지만 Spring Boot 는 네이티브로 읽지 않는다. bootRun 도 같은 파일을
// 쓰도록 여기서 환경변수로 넘긴다 — 키를 두 곳에서 관리하면 반드시 어긋난다.
tasks.named<JavaExec>("bootRun") {
	val dotenv = rootProject.file(".env")
	if (dotenv.exists()) {
		dotenv.readLines()
			.map(String::trim)
			.filter { it.isNotEmpty() && !it.startsWith("#") && it.contains("=") }
			.forEach {
				val (key, value) = it.split("=", limit = 2)
				environment(key.trim(), value.trim().removeSurrounding("\""))
			}
	}
}
