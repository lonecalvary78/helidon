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

/**
 * Helidon Common Resumable,
 * allows notification of resumable resources before suspend and after resume.
 * Instances of classes implementing {@code Resumable} and registered with
 * {@code ResumableSupport.get().register(resumableResource)} are notified
 * before suspend and after resume by underlying resumable implementation.
 */
module io.helidon.common.resumable {
    requires java.management;

    uses io.helidon.common.resumable.ResumableSupport;

    exports io.helidon.common.resumable to
            io.helidon,
            io.helidon.integrations.crac,
            io.helidon.integrations.jta.cdi,
            io.helidon.microprofile.server,
            io.helidon.webserver,
            io.helidon.webclient.api,
            io.helidon.metrics.systemmeters;

}
