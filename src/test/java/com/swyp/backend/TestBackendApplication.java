package com.swyp.backend;

import org.springframework.boot.SpringApplication;

/**
 * Local dev entrypoint: runs the app against an ephemeral PostgreSQL (Testcontainers), so a developer
 * can `./gradlew bootTestRun` without installing PostgreSQL. The production `main` in
 * {@link BackendApplication} stays free of test dependencies.
 */
public class TestBackendApplication {

	public static void main(String[] args) {
		SpringApplication.from(BackendApplication::main)
			.with(TestcontainersConfiguration.class)
			.run(args);
	}
}
