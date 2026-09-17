package com.swyp.backend.common.json;

import java.time.LocalDateTime;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.module.SimpleModule;

@Configuration
public class JsonTimeConfig {

	@Bean
	JacksonModule wallClockDateTimeModule() {
		SimpleModule module = new SimpleModule("WallClockDateTime");
		module.addDeserializer(LocalDateTime.class, new WallClockDateTimeDeserializer());
		return module;
	}
}
