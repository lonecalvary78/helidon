/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.codegen;

import java.net.URI;
import java.time.Duration;

import io.helidon.common.Size;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;

/**
 * Validation utilities.
 */
public final class CodegenValidator {
    private CodegenValidator() {
    }

    /**
     * Validate a URI value in an annotation.
     *
     * @param enclosingType  type that owns the element
     * @param element        annotated element
     * @param annotationType type of annotation
     * @param property       property of annotation
     * @param value          actual value read from the annotation property
     * @return the value
     * @throws io.helidon.codegen.CodegenException with correct source element describing the problem
     */
    public static String validateUri(TypeName enclosingType,
                                     TypedElementInfo element,
                                     TypeName annotationType,
                                     String property,
                                     String value) {
        try {
            URI.create(value);
            return value;
        } catch (Exception e) {
            throw new CodegenException("URI expression of annotation " + annotationType.fqName() + "."
                                               + property + "(): "
                                               + "\"" + value + "\" cannot be parsed. Invalid URI.",
                                       e,
                                       element.originatingElementValue());
        }
    }

    /**
     * Validate a duration annotation on a method, field, or constructor.
     *
     * @param enclosingType  type that owns the element
     * @param element        annotated element
     * @param annotationType type of annotation
     * @param property       property of annotation
     * @param value          actual value read from the annotation property
     * @return the value
     * @throws io.helidon.codegen.CodegenException with correct source element describing the problem
     */
    public static String validateDuration(TypeName enclosingType,
                                          TypedElementInfo element,
                                          TypeName annotationType,
                                          String property,
                                          String value) {
        try {
            Duration.parse(value);
            return value;
        } catch (Exception e) {
            throw new CodegenException("Duration expression of annotation " + annotationType.fqName() + "."
                                               + property + "(): "
                                               + "\"" + value + "\" cannot be parsed. Duration expects an"
                                               + " expression such as 'PT1S' (1 second), 'PT0.1S' (tenth of a second)."
                                               + " Please check javadoc of " + Duration.class.getName() + " class.",
                                       e,
                                       element.originatingElementValue());
        }
    }

    /**
     * Validate a {@link io.helidon.common.Size} annotation on a method, field, or constructor.
     *
     * @param enclosingType  type that owns the element
     * @param element        annotated element
     * @param annotationType type of annotation
     * @param property       property of annotation
     * @param value          actual value read from the annotation property
     * @return the value
     * @throws io.helidon.codegen.CodegenException with correct source element describing the problem
     */
    public static String validateSize(TypeName enclosingType,
                                      TypedElementInfo element,
                                      TypeName annotationType,
                                      String property,
                                      String value) {
        try {
            Size.parse(value);
            return value;
        } catch (Exception e) {
            throw new CodegenException("Size expression of annotation " + annotationType.fqName() + "."
                                               + property + "(): "
                                               + "\"" + value + "\" cannot be parsed. Size expects an"
                                               + " expression such as '120 KB' (120 * 1024 * 1024), "
                                               + "'120 kB' (120 * 1000 * 1000), or '120 KiB' (same as KB)"
                                               + " Please check javadoc of " + Size.class.getName() + " class.",
                                       e,
                                       element.originatingElementValue());
        }
    }
}
