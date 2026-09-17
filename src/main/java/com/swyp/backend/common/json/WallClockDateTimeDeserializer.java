package com.swyp.backend.common.json;

import com.swyp.backend.common.ClockConfig;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

public class WallClockDateTimeDeserializer extends ValueDeserializer<LocalDateTime> {

	@Override
	public LocalDateTime deserialize(JsonParser parser, DeserializationContext context) {
		String written = parser.getString().strip();
		try {
			return OffsetDateTime.parse(written)
					.atZoneSameInstant(ClockConfig.SERVICE_ZONE)
					.toLocalDateTime();
		} catch (DateTimeParseException notAnInstant) {
			return LocalDateTime.parse(written);
		}
	}
}
