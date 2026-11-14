package com.iplytics.common.config;

import com.fasterxml.jackson.databind.JavaType;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverter;
import io.swagger.v3.core.converter.ModelConverterContext;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.media.Schema;
import java.lang.reflect.RecordComponent;
import java.util.Iterator;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Marks the primitive components of response records ({@code int}, {@code long}, {@code boolean}, ...) as required in
 * the OpenAPI document. They can never be null, so generated TypeScript types declare them non-optional. Reference
 * components stay optional because they may be null (an average without dismissals, a match without a winner).
 */
@Component
public class RecordRequiredPropertiesConverter implements ModelConverter {

    @Override
    public Schema<?> resolve(AnnotatedType type, ModelConverterContext context, Iterator<ModelConverter> chain) {
        Schema<?> schema = chain.hasNext() ? chain.next().resolve(type, context, chain) : null;
        if (schema == null || type.getType() == null) {
            return schema;
        }
        JavaType javaType = Json.mapper().constructType(type.getType());
        Class<?> raw = javaType.getRawClass();
        if (!raw.isRecord()) {
            return schema;
        }
        Schema<?> model = schema;
        if (schema.get$ref() != null) {
            String ref = schema.get$ref();
            model = context.getDefinedModels().get(ref.substring(ref.lastIndexOf('/') + 1));
        }
        if (model == null || model.getProperties() == null) {
            return schema;
        }
        for (RecordComponent component : raw.getRecordComponents()) {
            String name = component.getName();
            List<String> required = model.getRequired();
            if (component.getType().isPrimitive() && model.getProperties().containsKey(name)
                    && (required == null || !required.contains(name))) {
                model.addRequiredItem(name);
            }
        }
        return schema;
    }
}
