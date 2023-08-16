/*
 * Copyright (c) 2021, 2023 Oracle and/or its affiliates.
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

import io.helidon.common.features.api.Feature;
import io.helidon.common.features.api.HelidonFlavor;
import io.helidon.webserver.observe.metrics.MetricsObserveProvider;
import io.helidon.webserver.observe.spi.ObserveProvider;

/**
 * Helidon WebServer Observability Metrics Support.
 */
@Feature(value = "Metrics",
         description = "WebServer Metrics support",
         in = HelidonFlavor.SE)
module io.helidon.webserver.observe.metrics {
    requires transitive io.helidon.webserver.observe;
    requires io.helidon.webserver;
    requires io.helidon.http.media.jsonp;
    requires io.helidon.servicecommon;
    requires static io.helidon.config.metadata;
    requires io.helidon.metrics.api;
    requires io.helidon.metrics.serviceapi;
    requires io.helidon.common.context;
    requires io.helidon.common.features.api;

    exports io.helidon.webserver.observe.metrics;

    provides ObserveProvider with MetricsObserveProvider;
}