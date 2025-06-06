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

package io.helidon.logging.common;

import java.util.Map;
import java.util.function.Supplier;

import io.helidon.common.context.spi.DataPropagationProvider;

/**
 * Data propagator for key/supplier MDC data.
 */
public class MdcSupplierPropagator implements DataPropagationProvider<Map<String, Supplier<String>>> {

    /**
     * For service loading.
     */
    public MdcSupplierPropagator() {
    }

    @Override
    public Map<String, Supplier<String>> data() {
        return HelidonMdc.suppliers();
    }

    @Override
    public void propagateData(Map<String, Supplier<String>> data) {
        HelidonMdc.suppliers(data);
    }

    @Override
    public void clearData(Map<String, Supplier<String>> data) {
        HelidonMdc.clear();
    }
}
