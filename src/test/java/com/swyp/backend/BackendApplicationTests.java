package com.swyp.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

// Same import set as RefreshTokenServiceTest so they share one cached context (and one set of
// containers) instead of each spinning its own Postgres — keeps the container count down.
@Import({TestcontainersConfiguration.class, RedisTestcontainersConfiguration.class})
@SpringBootTest
class BackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
