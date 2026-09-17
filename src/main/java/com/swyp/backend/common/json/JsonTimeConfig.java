package com.swyp.backend.common.json;

import java.time.Instant;
import java.time.LocalDateTime;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.module.SimpleModule;

@Configuration
public class JsonTimeConfig {

	@Bean
	JacksonModule serviceZoneTimeModule() {
		SimpleModule module = new SimpleModule("ServiceZoneTime");
		module.addDeserializer(LocalDateTime.class, new WallClockDateTimeDeserializer());
		module.addSerializer(LocalDateTime.class, new WallClockDateTimeSerializer());
		module.addSerializer(Instant.class, new ServiceZoneInstantSerializer());
		return module;
	}
}
