package com.swyp.backend;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;

/**
 * Redis Testcontainer, imported only by tests that actually hit Redis (kept out of the shared
 * {@link TestcontainersConfiguration} so JPA-slice tests don't pay to start Redis). {@code name =
 * "redis"} lets Spring Boot's RedisContainerConnectionDetailsFactory wire {@code spring.data.redis.*}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class RedisTestcontainersConfiguration {

	@Bean
	@ServiceConnection(name = "redis")
	GenericContainer redisContainer() {
		return new GenericContainer("redis:7").withExposedPorts(6379);
	}
}
