package com.swyp.backend.analytics.repository;

import com.swyp.backend.analytics.entity.DomainEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DomainEventRepository extends JpaRepository<DomainEvent, Long> {}
