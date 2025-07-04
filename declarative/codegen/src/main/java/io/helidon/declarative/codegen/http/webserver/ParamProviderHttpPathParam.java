/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.declarative.codegen.http.webserver;

import java.util.Optional;

import io.helidon.codegen.CodegenException;
import io.helidon.codegen.classmodel.ContentBuilder;
import io.helidon.common.types.Annotation;
import io.helidon.declarative.codegen.http.webserver.spi.HttpParameterCodegenProvider;

import static io.helidon.declarative.codegen.http.HttpTypes.HTTP_PATH_PARAM_ANNOTATION;

class ParamProviderHttpPathParam extends AbstractParametersProvider implements HttpParameterCodegenProvider {
    @Override
    public boolean codegen(ParameterCodegenContext ctx) {
        Optional<Annotation> first = ctx.annotations().stream()
                .filter(it -> HTTP_PATH_PARAM_ANNOTATION.equals(it.typeName()))
                .findFirst();

        if (first.isEmpty()) {
            return false;
        }

        Annotation pathParam = first.get();
        String pathParamName = pathParam.value()
                .orElseThrow(() -> new CodegenException("@PathParam annotation must have a value."));

        ContentBuilder<?> contentBuilder = ctx.contentBuilder();
        contentBuilder.addContent(ctx.serverRequestParamName())
                .addContent(".path().pathParameters()");

        codegenFromParameters(contentBuilder, ctx.parameterType(), pathParamName, ctx.parameterType().isOptional());
        contentBuilder.addContentLine(";");

        return true;
    }
}
