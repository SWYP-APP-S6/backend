package com.swyp.backend.common.openapi;

import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverter;
import io.swagger.v3.core.converter.ModelConverterContext;
import io.swagger.v3.core.util.Json31;
import io.swagger.v3.oas.models.media.Schema;
import java.lang.reflect.RecordComponent;
import java.util.Iterator;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
public class RecordRequiredModelConverter implements ModelConverter {

	private static final String REF_PREFIX = "#/components/schemas/";

	@Override
	public Schema<?> resolve(
			AnnotatedType type, ModelConverterContext context, Iterator<ModelConverter> chain) {
		Schema<?> resolved = chain.hasNext() ? chain.next().resolve(type, context, chain) : null;
		if (resolved == null) {
			return null;
		}
		Class<?> raw = rawClass(type);
		if (raw == null || !raw.isRecord()) {
			return resolved;
		}
		Schema<?> target = schemaFor(resolved, context);
		if (target == null || target.getProperties() == null) {
			return resolved;
		}
		for (RecordComponent component : raw.getRecordComponents()) {
			if (isNullable(component) || !target.getProperties().containsKey(component.getName())) {
				continue;
			}
			target.addRequiredItem(component.getName());
		}
		return resolved;
	}

	private static Schema<?> schemaFor(Schema<?> resolved, ModelConverterContext context) {
		String ref = resolved.get$ref();
		if (ref == null) {
			return resolved;
		}
		return context.getDefinedModels().get(ref.substring(REF_PREFIX.length()));
	}

	private static boolean isNullable(RecordComponent component) {
		return component.getType() == Optional.class
				|| component.isAnnotationPresent(Nullable.class)
				|| component.getAnnotatedType().isAnnotationPresent(Nullable.class);
	}

	private static Class<?> rawClass(AnnotatedType type) {
		if (type.getType() == null) {
			return null;
		}
		return Json31.mapper().constructType(type.getType()).getRawClass();
	}
}
