/*
 * Copyright (c) 2020, 2025 Oracle and/or its affiliates.
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
package io.helidon.microprofile.metrics;

import java.lang.reflect.Method;

import io.helidon.microprofile.testing.junit5.AddConfig;
import io.helidon.microprofile.testing.junit5.AddExtension;
import io.helidon.microprofile.testing.junit5.HelidonTest;

import org.eclipse.microprofile.metrics.MetricID;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@HelidonTest
@Disabled // Need to revisit clearing out the repo of REST.request metrics between tests
@AddExtension(VetoCdiExtension.class)
@AddConfig(key = "metrics." + MetricsCdiExtension.REST_ENDPOINTS_METRIC_ENABLED_PROPERTY_NAME, value = "true")
public class TestVetoedResource extends MetricsMpServiceTest {

    @Test
    void testNoAnnotatedMetricForVetoedResource() throws NoSuchMethodException {
        // The metrics CDI extension should ignore the vetoed resource's explicitly-annotated metrics.
        MetricID vetoedID = new MetricID(VetoedResource.COUNTER_NAME);
        assertThat("Metrics CDI extension incorrectly registered a metric on a vetoed resource",
                registry().getCounters()
                        .containsKey(vetoedID), is(false));
    }

    @Test
    void testNoSyntheticTimedMetricForVetoedResource() throws NoSuchMethodException {
        // Makes sure that a vetoed JAX-RS resource with an explicit metric annotation was not registered with a synthetic
        // Timed metric.
        Method method = VetoedResource.class.getMethod("get");
        assertThat(
                "Metrics CDI extension incorrectly registered a synthetic timer on a vetoed resource JAX-RS endpoint "
                        + "method with an explicit metrics annotation",
                syntheticTimerTimerRegistry()
                        .getTimers()
                        .containsKey(MetricsCdiExtension.restEndpointTimerMetricID(VetoedResource.class, method)),
                is(false));
    }

    @Test
    void testNoSyntheticTimedMetricForVetoedResourceWithJaxRsEndpointButOtherwiseUnmeasured() throws NoSuchMethodException {
        // Makes sure that a vetoed JAX-RS resource with no explicit metric annotation was not registered with a synthetic
        // Timed metric.
        Method method = VetoedJaxRsButOtherwiseUnmeasuredResource.class.getMethod("get");
        assertThat(
                "Metrics CDI extension incorrectly registered a synthetic timer on JAX-RS endpoint method with no "
                        + "explicit metrics annotation: "
                        + VetoedJaxRsButOtherwiseUnmeasuredResource.class.getName() + "#" + method.getName(),
                MetricsCdiExtension.getRegistryForSyntheticRestRequestMetrics()
                        .getTimers()
                        .containsKey(MetricsCdiExtension.restEndpointTimerMetricID(VetoedJaxRsButOtherwiseUnmeasuredResource.class,
                                                                                   method)),
                is(false));
    }
}
