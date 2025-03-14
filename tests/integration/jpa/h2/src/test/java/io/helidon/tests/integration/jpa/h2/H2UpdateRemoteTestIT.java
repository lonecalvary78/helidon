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
package io.helidon.tests.integration.jpa.h2;

import io.helidon.tests.integration.jpa.common.UpdateTest;

import org.junit.jupiter.api.Test;

/**
 * Invoke {@code /test/update} endpoints.
 */
class H2UpdateRemoteTestIT extends H2RemoteTest implements UpdateTest {

    H2UpdateRemoteTestIT() {
        super("/test/update");
    }

    @Test
    @Override
    public void testUpdateEntity() {
        remoteTest();
    }

    @Test
    @Override
    public void testUpdateJPQL() {
        remoteTest();
    }

    @Test
    @Override
    public void testUpdateCriteria() {
        remoteTest();
    }

    @Test
    @Override
    public void testUpdateCity() {
        remoteTest();
    }
}
