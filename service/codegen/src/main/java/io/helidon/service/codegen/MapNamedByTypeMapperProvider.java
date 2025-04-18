/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.helidon.service.codegen;

import java.util.Collection;
import java.util.Set;

import io.helidon.codegen.CodegenContext;
import io.helidon.codegen.CodegenOptions;
import io.helidon.codegen.spi.AnnotationMapper;
import io.helidon.codegen.spi.AnnotationMapperProvider;
import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.TypeName;

import static io.helidon.service.codegen.ServiceCodegenTypes.SERVICE_ANNOTATION_NAMED;
import static io.helidon.service.codegen.ServiceCodegenTypes.SERVICE_ANNOTATION_NAMED_BY_TYPE;

/**
 * A {@link java.util.ServiceLoader} provider implementation to map class named annotations to named annotations.
 */
@Weight(Weighted.DEFAULT_WEIGHT - 10) // lower weight than JavaxAnnotationMapper
public class MapNamedByTypeMapperProvider implements AnnotationMapperProvider {
    /**
     * Required default constructor.
     *
     * @deprecated only for {@link java.util.ServiceLoader}.
     */
    @Deprecated
    public MapNamedByTypeMapperProvider() {
    }

    @Override
    public Set<TypeName> supportedAnnotations() {
        return Set.of(SERVICE_ANNOTATION_NAMED_BY_TYPE);
    }

    @Override
    public AnnotationMapper create(CodegenOptions options) {
        return new NamedByTypeMapper();
    }

    private static class NamedByTypeMapper implements AnnotationMapper {

        private NamedByTypeMapper() {
        }

        @Override
        public boolean supportsAnnotation(Annotation annotation) {
            return annotation.typeName().equals(SERVICE_ANNOTATION_NAMED_BY_TYPE);
        }

        @Override
        public Collection<Annotation> mapAnnotation(CodegenContext ctx, Annotation original, ElementKind elementKind) {
            return Set.of(Annotation.create(SERVICE_ANNOTATION_NAMED, original.value().orElse("")));
        }
    }
}
