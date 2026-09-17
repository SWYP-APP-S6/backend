package com.swyp.backend.common.json;

import com.swyp.backend.common.ClockConfig;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

public class ServiceZoneInstantSerializer extends ValueSerializer<Instant> {

	@Override
	public void serialize(Instant value, JsonGenerator generator, SerializationContext context) {
		generator.writeString(DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(
				value.atZone(ClockConfig.SERVICE_ZONE)));
	}
}
