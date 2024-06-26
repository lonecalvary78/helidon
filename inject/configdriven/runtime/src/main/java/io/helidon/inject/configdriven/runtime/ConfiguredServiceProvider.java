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

package io.helidon.inject.configdriven.runtime;

import io.helidon.inject.api.ServiceProvider;
import io.helidon.inject.configdriven.api.ConfigBeanFactory;
import io.helidon.inject.configdriven.api.NamedInstance;

/**
 * An extension to {@link ServiceProvider} that represents a config-driven service.
 *
 * @param <T>  the type of this service provider manages
 * @param <CB> the type of config beans that this service is configured by
 * @deprecated Helidon inject is deprecated and will be replaced in a future version
 */
@Deprecated(forRemoval = true, since = "4.0.8")
public interface ConfiguredServiceProvider<T, CB> extends ServiceProvider<T>, ConfigBeanFactory<CB> {

    /**
     * Returns the config bean associated with this managed service provider.
     *
     * @return the config bean associated with this managed service provider
     * @throws java.lang.NullPointerException if this is the root provider
     */
    CB configBean();

    /**
     * Register a named config bean as a child of this root provider.
     *
     * @param configBean config bean that drives an instance
     */
    void registerConfigBean(NamedInstance<CB> configBean);
}
