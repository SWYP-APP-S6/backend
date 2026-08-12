package com.swyp.backend.common;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Turns on the auditing that populates {@link BaseTimeEntity} timestamps. Kept as a dedicated config
 * (not on the main application class) so slice tests can opt in explicitly with {@code @Import}.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
