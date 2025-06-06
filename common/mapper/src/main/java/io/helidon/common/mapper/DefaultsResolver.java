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

package io.helidon.common.mapper;

import java.util.List;
import java.util.Set;

import io.helidon.common.GenericType;
import io.helidon.common.types.Annotation;

/**
 * A service that resolves defaults from annotations.
 */
@SuppressWarnings("deprecation")
public interface DefaultsResolver {
    /**
     * Resolve defaults from the set of annotations.
     * Uses default annotations from {@link io.helidon.common.Default}.
     * In case there is more than one annotation defined, processes the first one in this order:
     * <ul>
     *     <li>{@link io.helidon.common.Default.Value}</li>
     *     <li>{@link io.helidon.common.Default.Int}</li>
     *     <li>{@link io.helidon.common.Default.Double}</li>
     *     <li>{@link io.helidon.common.Default.Boolean}</li>
     *     <li>{@link io.helidon.common.Default.Long}</li>
     * </ul>
     *
     * @param annotations  set of annotations to analyze
     * @param expectedType type we expect to map to
     * @param name         name of the element that has default value annotation
     * @return a list of default values, correctly typed
     * @throws io.helidon.common.mapper.MapperException in case there is a type mismatch
     */
    List<?> resolve(Set<Annotation> annotations, GenericType<?> expectedType, String name);
}
