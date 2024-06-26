/*
 * Copyright (c) 2021, 2024 Oracle and/or its affiliates.
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

/**
 * Sys operations for Vault.
 *
 * @see io.helidon.integrations.vault.sys.Sys
 */
@Feature(value = "Sys",
        description = "System operations",
        in = {HelidonFlavor.SE, HelidonFlavor.MP},
        path = {"HCP Vault", "Sys"}
)
module io.helidon.integrations.vault.sys {

    requires io.helidon.integrations.common.rest;
    requires io.helidon.integrations.vault.auths.common;

    requires static io.helidon.common.features.api;

    requires transitive io.helidon.integrations.vault;

    exports io.helidon.integrations.vault.sys;

    provides io.helidon.integrations.vault.spi.SysProvider
            with io.helidon.integrations.vault.sys.HcpSysProvider;

    provides io.helidon.integrations.vault.spi.InjectionProvider
            with io.helidon.integrations.vault.sys.HcpSysProvider;

}
