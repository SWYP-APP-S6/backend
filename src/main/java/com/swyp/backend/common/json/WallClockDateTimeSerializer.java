package com.swyp.backend.common.json;

import com.swyp.backend.common.ClockConfig;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

public class WallClockDateTimeSerializer extends ValueSerializer<LocalDateTime> {

	@Override
	public void serialize(LocalDateTime value, JsonGenerator generator, SerializationContext context) {
		generator.writeString(DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(
				value.atZone(ClockConfig.SERVICE_ZONE)));
	}
}
