/*
 * Copyright (c) 2023, 2025 Oracle and/or its affiliates.
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

package io.helidon.integrations.oci.sdk.runtime;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.config.MapConfigSource;
import io.helidon.service.registry.ServiceRegistryException;
import io.helidon.service.registry.ServiceRegistryManager;

import com.oracle.bmc.auth.AbstractAuthenticationDetailsProvider;
import com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider;
import com.oracle.bmc.auth.ResourcePrincipalAuthenticationDetailsProvider;
import com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.junit5.OptionalMatcher.optionalValue;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for {@link OciExtension} and {@link OciConfig}.
 */
class OciExtensionTest {
    private ServiceRegistryManager registryManager;

    static Config createTestConfig(MapConfigSource.Builder... builders) {
        return Config.builder(builders)
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();
    }

    static MapConfigSource.Builder ociAuthConfigStrategies(String strategy,
                                                           String... strategies) {
        Map<String, String> map = new HashMap<>();
        if (strategy != null) {
            map.put(OciConfig.CONFIG_KEY + ".auth-strategy", strategy);
        }
        if (strategies != null && strategies.length != 0) {
            map.put(OciConfig.CONFIG_KEY + ".auth-strategies", String.join(",", strategies));
        }
        return ConfigSources.create(map, "config-oci-auth-strategies");
    }

    static MapConfigSource.Builder ociAuthConfigFile(String configPath,
                                                     String profile) {
        Map<String, String> map = new HashMap<>();
        if (configPath != null) {
            map.put(OciConfig.CONFIG_KEY + ".config.path", configPath);
        }
        if (profile != null) {
            map.put(OciConfig.CONFIG_KEY + ".config.profile", String.join(",", profile));
        }
        return ConfigSources.create(map, "config-oci-auth-config");
    }

    static MapConfigSource.Builder ociAuthSimpleConfig(String tenantId,
                                                       String userId,
                                                       String passPhrase,
                                                       String fingerPrint,
                                                       String privateKey,
                                                       String privateKeyPath,
                                                       String region) {
        Map<String, String> map = new HashMap<>();
        if (tenantId != null) {
            map.put(OciConfig.CONFIG_KEY + ".auth.tenant-id", tenantId);
        }
        if (userId != null) {
            map.put(OciConfig.CONFIG_KEY + ".auth.user-id", userId);
        }
        if (passPhrase != null) {
            map.put(OciConfig.CONFIG_KEY + ".auth.passphrase", passPhrase);
        }
        if (fingerPrint != null) {
            map.put(OciConfig.CONFIG_KEY + ".auth.fingerprint", fingerPrint);
        }
        if (privateKey != null) {
            map.put(OciConfig.CONFIG_KEY + ".auth.private-key", privateKey);
        }
        if (privateKeyPath != null) {
            map.put(OciConfig.CONFIG_KEY + ".auth.private-key-path", privateKeyPath);
        }
        if (region != null) {
            map.put(OciConfig.CONFIG_KEY + ".auth.region", region);
        }
        return ConfigSources.create(map, "config-oci-auth-simple");
    }

    @BeforeEach
    void setUp() {
        registryManager = ServiceRegistryManager.create();
    }

    @AfterEach
    void reset() {
        OciExtension.ociConfigFileName(null);
        if (registryManager != null) {
            registryManager.shutdown();
        }
    }

    @Test
    void ociConfig() {
        assertThat(OciExtension.ociConfig(),
                   notNullValue());
        assertThat(OciExtension.ociConfig(),
                   equalTo(OciExtension.ociConfig()));
    }

    @Test
    void potentialAuthStrategies() {
        Config config = createTestConfig(ociAuthConfigStrategies(null))
                .get(OciConfig.CONFIG_KEY);
        OciConfig cfg = OciConfig.create(config);
        assertThat(cfg.potentialAuthStrategies(),
                   contains("config", "config-file", "instance-principals", "resource-principal"));

        config = createTestConfig(ociAuthConfigStrategies("auto"))
                .get(OciConfig.CONFIG_KEY);
        cfg = OciConfig.create(config);
        assertThat(cfg.potentialAuthStrategies(),
                   contains("config", "config-file", "instance-principals", "resource-principal"));

        config = createTestConfig(ociAuthConfigStrategies(null, "instance-principals", "auto"))
                .get(OciConfig.CONFIG_KEY);
        cfg = OciConfig.create(config);
        assertThat(cfg.potentialAuthStrategies(),
                   contains("config", "config-file", "instance-principals", "resource-principal"));

        config = createTestConfig(ociAuthConfigStrategies(null, "instance-principals", "resource-principal"))
                .get(OciConfig.CONFIG_KEY);
        cfg = OciConfig.create(config);
        assertThat(cfg.potentialAuthStrategies(),
                   contains("instance-principals", "resource-principal"));

        config = createTestConfig(ociAuthConfigStrategies("config", "auto"))
                .get(OciConfig.CONFIG_KEY);
        cfg = OciConfig.create(config);
        assertThat(cfg.potentialAuthStrategies(),
                   contains("config"));

        config = createTestConfig(ociAuthConfigStrategies("config", "config", "config-file"))
                .get(OciConfig.CONFIG_KEY);
        cfg = OciConfig.create(config);
        assertThat(cfg.potentialAuthStrategies(),
                   contains("config"));

        config = createTestConfig(ociAuthConfigStrategies("auto", "config", "config-file"))
                .get(OciConfig.CONFIG_KEY);
        cfg = OciConfig.create(config);
        assertThat(cfg.potentialAuthStrategies(),
                   contains("config", "config-file"));

        // this must fail, as empty string is not an allowed strategy
        Config configWithEmpty = createTestConfig(ociAuthConfigStrategies("", ""))
                .get(OciConfig.CONFIG_KEY);
        assertThrows(RuntimeException.class, () -> OciConfig.create(configWithEmpty));
    }

    @Test
    void bogusAuthStrategyAttempted() {
        Config config = createTestConfig(ociAuthConfigStrategies("bogus"))
                .get(OciConfig.CONFIG_KEY);
        RuntimeException e = assertThrows(RuntimeException.class, () -> OciConfig.create(config));
        assertThat(e.getMessage(),
                   containsString("Configured: \"bogus\", expected one of:"));
    }

    @Test
    void testBogusAuthStrategies() {
        Config config = createTestConfig(ociAuthConfigStrategies(null, "config", "bogus"))
                .get(OciConfig.CONFIG_KEY);
        RuntimeException e = assertThrows(RuntimeException.class, () -> OciConfig.create(config));
        assertThat(e.getMessage(),
                   containsString("Configured: \"bogus\", expected one of:"));
    }

    @Test
    void fileConfigIsPresent() {
        Config config = createTestConfig(ociAuthConfigFile("path", "profile"))
                .get(OciConfig.CONFIG_KEY);
        OciConfig cfg = OciConfig.create(config);
        assertThat(cfg.fileConfigIsPresent(),
                   is(true));
        assertThat(cfg.configProfile().orElseThrow(),
                   equalTo("profile"));

        config = createTestConfig(ociAuthConfigFile("path", null))
                .get(OciConfig.CONFIG_KEY);
        cfg = OciConfig.create(config);
        assertThat(cfg.fileConfigIsPresent(), is(true));
        assertThat(cfg.configProfile().orElseThrow(),
                   equalTo("DEFAULT"));

        config = createTestConfig(ociAuthConfigFile("", ""))
                .get(OciConfig.CONFIG_KEY);
        cfg = OciConfig.create(config);
        // the ConfigFile type provider always works, since OCI SDK API assumes that too
        assertThat(cfg.fileConfigIsPresent(),
                   is(true));

        config = createTestConfig(ociAuthConfigFile(null, null))
                .get(OciConfig.CONFIG_KEY);
        cfg = OciConfig.create(config);
        // this will be true if there is a ~/.oci/config based configuration, false otherwise
        //        assertThat(cfg.fileConfigIsPresent(),
        //                   is(true));
    }

    @Test
    void simpleConfigIsPresent() {
        Config config = createTestConfig(ociAuthSimpleConfig("tenant", "user", "phrase", "fp", "pk", "pkp", "region"))
                .get(OciConfig.CONFIG_KEY);
        OciConfig cfg = OciConfig.create(config);
        assertThat(cfg.simpleConfigIsPresent(),
                   is(true));

        config = createTestConfig(ociAuthSimpleConfig("tenant", "user", "phrase", "fp", "pk", "pkp", null))
                .get(OciConfig.CONFIG_KEY);
        cfg = OciConfig.create(config);
        assertThat(cfg.simpleConfigIsPresent(),
                   is(true));

        config = createTestConfig(ociAuthSimpleConfig(null, "user", "phrase", "fp", "pk", "pkp", null))
                .get(OciConfig.CONFIG_KEY);
        cfg = OciConfig.create(config);
        assertThat(cfg.simpleConfigIsPresent(),
                   is(false));

        config = createTestConfig(ociAuthSimpleConfig("tenant", null, "phrase", "fp", "pk", "pkp", null))
                .get(OciConfig.CONFIG_KEY);
        cfg = OciConfig.create(config);
        assertThat(cfg.simpleConfigIsPresent(),
                   is(false));

        config = createTestConfig(ociAuthSimpleConfig("tenant", "user", null, "fp", "pk", "pkp", null))
                .get(OciConfig.CONFIG_KEY);
        cfg = OciConfig.create(config);
        assertThat(cfg.simpleConfigIsPresent(),
                   is(false));

        config = createTestConfig(ociAuthSimpleConfig("tenant", "user", "phrase", null, "pk", "pkp", null))
                .get(OciConfig.CONFIG_KEY);
        cfg = OciConfig.create(config);
        assertThat(cfg.simpleConfigIsPresent(),
                   is(false));

        config = createTestConfig(ociAuthSimpleConfig("tenant", "user", "phrase", "fp", null, "pkp", null))
                .get(OciConfig.CONFIG_KEY);
        cfg = OciConfig.create(config);
        assertThat(cfg.simpleConfigIsPresent(),
                   is(true));

        config = createTestConfig(ociAuthSimpleConfig("tenant", "user", "phrase", "fp", "pk", null, "region"))
                .get(OciConfig.CONFIG_KEY);
        cfg = OciConfig.create(config);
        assertThat(cfg.simpleConfigIsPresent(),
                   is(true));

        config = createTestConfig(ociAuthSimpleConfig("tenant", "user", "phrase", "fp", null, null, "region"))
                .get(OciConfig.CONFIG_KEY);
        cfg = OciConfig.create(config);
        assertThat(cfg.simpleConfigIsPresent(),
                   is(false));
    }

    @Test
    void defaultOciConfigAttributes() {
        assertThat(OciExtension.ociConfig().authKeyFile(),
                   equalTo("oci_api_key.pem"));
        assertThat(OciExtension.ociConfig().imdsHostName(),
                   equalTo(OciConfig.IMDS_HOSTNAME));
        assertThat(OciExtension.ociConfig().imdsTimeout().toMillis(),
                   equalTo(100L));
    }

    @Test
    void ociYamlConfigFile() {
        // setup (tear down happens after each run)
        OciExtension.ociConfigFileName("test-oci-resource-principal.yaml");

        OciConfig ociConfig = OciExtension.ociConfig();
        assertThat(ociConfig.authStrategy(),
                   optionalValue(equalTo("resource-principal")));

        // note that we can't actually instantiate these when there is no auth provider configured in the environment
        IllegalArgumentException e = (IllegalArgumentException) assertThrows(ServiceRegistryException.class,
                                                                             () -> OciExtension.ociAuthenticationProvider()
                                                                                     .get()).getCause();
        assertThat(e.getMessage(),
                   equalTo(OciAuthenticationDetailsProvider.TAG_RESOURCE_PRINCIPAL_VERSION + " environment variable missing"));
        assertThat(OciExtension.configuredAuthenticationDetailsProvider(false),
                   equalTo(ResourcePrincipalAuthenticationDetailsProvider.class));

        OciExtension.ociConfigFileName("test-oci-config-file.yaml");
        Supplier<? extends AbstractAuthenticationDetailsProvider> authProvider = OciExtension.ociAuthenticationProvider();

        try {
            // in the case where there is actually an oci configuration in this environment
            AbstractAuthenticationDetailsProvider auth = authProvider.get();
            assertThat(auth,
                       instanceOf(ConfigFileAuthenticationDetailsProvider.class));
            assertThat(OciExtension.configuredAuthenticationDetailsProvider(true),
                       equalTo(ConfigFileAuthenticationDetailsProvider.class));
            assertThat(OciExtension.configuredAuthenticationDetailsProvider(false),
                       equalTo(ConfigFileAuthenticationDetailsProvider.class));
        } catch (ServiceRegistryException ispe) {
            assertThat(ispe.getMessage(),
                       equalTo("Failed to list instances in InjectionPointProvider: "
                                       + "io.helidon.integrations.oci.sdk.runtime.OciAuthenticationDetailsProvider"));
            assertThat(ispe.getCause().getMessage(),
                       equalTo("No instances of com.oracle.bmc.auth.AbstractAuthenticationDetailsProvider available for use. "
                                       + "Verify your configuration named: oci"));
            assertThat(OciExtension.configuredAuthenticationDetailsProvider(false),
                       equalTo(ConfigFileAuthenticationDetailsProvider.class));
        }
    }

    @Test
    void ociRawConfigShouldBeCached() {
        assertSame(Objects.requireNonNull(OciExtension.configSupplier()),
                   OciExtension.configSupplier(),
                   "The oci configuration from the config source should be cached");
    }

    @Test
    void fallbackConfigSupplier() {
        Config fallbackCfg = Config.just(
                ConfigSources.create(
                        Map.of("oci.auth", "config"),
                        "test-fallback-cfg"));
        OciExtension.fallbackConfigSupplier(() -> fallbackCfg);

        assertThat("when there is no oci.yaml present then we should be looking at the fallback config",
                   OciExtension.configuredAuthenticationDetailsProvider(false),
                   equalTo(SimpleAuthenticationDetailsProvider.class));

        OciExtension.ociConfigFileName("test-oci-resource-principal.yaml");
        OciExtension.fallbackConfigSupplier(() -> fallbackCfg);
        assertThat("when there is an oci.yaml present then we should NOT be looking at the fallback config",
                   OciExtension.configuredAuthenticationDetailsProvider(false),
                   equalTo(ResourcePrincipalAuthenticationDetailsProvider.class));
    }

}
