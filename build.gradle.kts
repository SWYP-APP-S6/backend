plugins {
	java
	id("org.springframework.boot") version "4.1.1"
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
