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
 * Vault's Key/value version 2 Secrets Engine support.
 */
@Feature(value = "K/V 2",
        description = "Key/Value Version 2 Secrets Engine",
        in = {HelidonFlavor.SE, HelidonFlavor.MP},
        path = {"HCP Vault", "Secrets", "K/V 2"}
)
module io.helidon.integrations.vault.secrets.kv {

    requires io.helidon.http;
    requires io.helidon.integrations.common.rest;

    requires static io.helidon.common.features.api;

    requires transitive io.helidon.integrations.vault;
    requires transitive io.helidon.security;

    exports io.helidon.integrations.vault.secrets.kv2;

    provides io.helidon.integrations.vault.spi.SecretsEngineProvider
            with io.helidon.integrations.vault.secrets.kv2.Kv2EngineProvider;

    provides io.helidon.security.spi.SecurityProviderService
            with io.helidon.integrations.vault.secrets.kv2.Kv2SecurityService;

    provides io.helidon.integrations.vault.spi.InjectionProvider
            with io.helidon.integrations.vault.secrets.kv2.Kv2EngineProvider;

}