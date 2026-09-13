package com.swyp.backend;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@SpringBootTest
@Import({TestcontainersConfiguration.class, RedisTestcontainersConfiguration.class})
class ScheduledBatchThreadsTest {

	@Autowired
	ScheduledAnnotationBeanPostProcessor scheduledPostProcessor;

	@Autowired
	ThreadPoolTaskScheduler taskScheduler;

	@Test
	void noBatchWaitsForAnotherToFinish() {
		int batches = scheduledPostProcessor.getScheduledTasks().size();

		assertThat(batches).isPositive();
		assertThat(taskScheduler.getScheduledThreadPoolExecutor().getCorePoolSize())
			.as("Boot's scheduler is one thread by default, so a slow push pass (batch-size × the "
					+ "FCM read timeout) would hold back hold expiry and its stock release -- the "
					+ "very coupling the outbox exists to remove. Adding a @Scheduled means raising "
					+ "spring.task.scheduling.pool.size with it.")
			.isGreaterThanOrEqualTo(batches);
	}
}
