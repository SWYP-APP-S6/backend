package com.swyp.backend;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AppDataCleaner {

	private static final String TRUNCATE = """
			truncate table
				hold_items, holds, hold_cancel_credits, notifications, user_device_tokens,
				products, stores, user_locations, users
			restart identity cascade
			""";

	@PersistenceContext
	private EntityManager entityManager;

	@Transactional
	public void clear() {
		entityManager.flush();
		entityManager.clear();
		entityManager.createNativeQuery(TRUNCATE).executeUpdate();
	}
}
