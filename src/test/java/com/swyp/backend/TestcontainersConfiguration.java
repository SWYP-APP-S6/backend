package com.swyp.backend;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Tests run against a real PostgreSQL started by Testcontainers. {@code @ServiceConnection} wires the
 * container's JDBC url/credentials into Spring's DataSource automatically, so no test datasource
 * config is needed. Requires Docker on the machine running the tests (CI runners have it).
 */
@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer("postgres:17");
	}
}
