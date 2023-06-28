/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.pico.api;

import io.helidon.builder.api.Prototype;
import io.helidon.config.metadata.ConfiguredOption;

/**
 * Provides optional, contextual tunings to the {@link Injector}.
 *
 * @see Injector
 */
@Prototype.Blueprint(builderInterceptor = InjectorOptionsBlueprint.Interceptor.class)
interface InjectorOptionsBlueprint {
    /**
     * The strategy the injector should apply. The default is {@link io.helidon.pico.api.Injector.Strategy#ANY}.
     *
     * @return the injector strategy to use
     */
    @ConfiguredOption("ANY")
    Injector.Strategy strategy();

    /**
     * Optionally, customized activator options to use for the {@link io.helidon.pico.api.Activator}.
     *
     * @return activator options, or leave blank to use defaults
     */
    ActivationRequest activationRequest();


    /**
     * This will ensure that the activation request is populated.
     */
    class Interceptor implements Prototype.BuilderInterceptor<InjectorOptions.BuilderBase<?, ?>> {
        Interceptor() {
        }

        @Override
        public InjectorOptions.BuilderBase<?, ?> intercept(InjectorOptions.BuilderBase<?, ?> target) {
            if (target.activationRequest() == null) {
                target.activationRequest(PicoServices.createActivationRequestDefault());
            }
            return target;
        }
    }

}