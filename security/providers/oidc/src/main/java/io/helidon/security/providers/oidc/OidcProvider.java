/*
 * Copyright (c) 2018, 2025 Oracle and/or its affiliates.
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

package io.helidon.security.providers.oidc;

import java.lang.System.Logger.Level;
import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import io.helidon.common.HelidonServiceLoader;
import io.helidon.common.config.Config;
import io.helidon.common.configurable.LruCache;
import io.helidon.common.parameters.Parameters;
import io.helidon.config.metadata.Configured;
import io.helidon.config.metadata.ConfiguredOption;
import io.helidon.http.Status;
import io.helidon.security.AuthenticationResponse;
import io.helidon.security.EndpointConfig;
import io.helidon.security.OutboundSecurityResponse;
import io.helidon.security.ProviderRequest;
import io.helidon.security.SecurityEnvironment;
import io.helidon.security.SecurityResponse;
import io.helidon.security.Subject;
import io.helidon.security.abac.scope.ScopeValidator;
import io.helidon.security.providers.common.OutboundConfig;
import io.helidon.security.providers.common.OutboundTarget;
import io.helidon.security.providers.common.TokenCredential;
import io.helidon.security.providers.oidc.common.OidcConfig;
import io.helidon.security.providers.oidc.common.Tenant;
import io.helidon.security.providers.oidc.common.TenantConfig;
import io.helidon.security.providers.oidc.common.spi.TenantConfigFinder;
import io.helidon.security.providers.oidc.common.spi.TenantConfigProvider;
import io.helidon.security.providers.oidc.common.spi.TenantIdFinder;
import io.helidon.security.providers.oidc.common.spi.TenantIdProvider;
import io.helidon.security.spi.AuthenticationProvider;
import io.helidon.security.spi.OutboundSecurityProvider;
import io.helidon.security.spi.SecurityProvider;
import io.helidon.security.util.TokenHandler;
import io.helidon.webclient.api.HttpClientRequest;

import jakarta.json.JsonObject;

import static io.helidon.security.providers.oidc.common.spi.TenantConfigFinder.DEFAULT_TENANT_ID;

/**
 * Open ID Connect authentication provider.
 *
 * IDCS specific notes:
 * <ul>
 * <li>If you want to use JWK to validate tokens, you must give access to the endpoint (by default only admin can access it)</li>
 * <li>If you want to use introspect endpoint to validate tokens, you must give rights to the application to do so (Client
 * Configuration/Allowed Operations)</li>
 * <li>If you want to retrieve groups when using IDCS, you must add "Client Credentials" in "Allowed Grant Types" in
 * application configuration, as well as "Grant the client access to Identity Cloud Service Admin APIs." configured to "User
 * Administrator"</li>
 * </ul>
 */
public final class OidcProvider implements AuthenticationProvider, OutboundSecurityProvider {
    private static final System.Logger LOGGER = System.getLogger(OidcProvider.class.getName());

    private final boolean optional;
    private final OidcConfig oidcConfig;
    private final List<TenantIdFinder> tenantIdFinders;
    private final List<TenantConfigFinder> tenantConfigFinders;
    private final boolean propagate;
    private final OidcOutboundConfig outboundConfig;
    private final boolean useJwtGroups;
    private final LruCache<String, TenantAuthenticationHandler> tenantAuthHandlers = LruCache.create();

    private OidcProvider(Builder builder, OidcOutboundConfig oidcOutboundConfig) {
        this.optional = builder.optional;
        this.oidcConfig = builder.oidcConfig;
        this.propagate = builder.propagate && (oidcOutboundConfig.hasOutbound());
        this.useJwtGroups = builder.useJwtGroups;
        this.outboundConfig = oidcOutboundConfig;

        tenantConfigFinders = List.copyOf(builder.tenantConfigFinders);
        tenantIdFinders = List.copyOf(builder.tenantIdFinders);

        tenantConfigFinders.forEach(tenantConfigFinder -> tenantConfigFinder.onChange(tenantAuthHandlers::remove));
    }

    /**
     * Load this provider from configuration.
     *
     * @param config configuration of this provider
     * @return a new provider configured for OIDC
     */
    public static OidcProvider create(Config config) {
        return builder().config(config).build();
    }

    /**
     * Create a new provider based on OIDC configuration.
     *
     * @param config config of OIDC server and client
     * @return a new provider configured for OIDC
     */
    public static OidcProvider create(OidcConfig config) {
        return builder().oidcConfig(config).build();
    }

    /**
     * A fluent API builder to created instances of this provider.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public Collection<Class<? extends Annotation>> supportedAnnotations() {
        return Set.of(ScopeValidator.Scope.class, ScopeValidator.Scopes.class);
    }

    @Override
    public AuthenticationResponse authenticate(ProviderRequest providerRequest) {
        String tenantId = tenantIdFinders.stream()
                .map(tenantIdFinder -> tenantIdFinder.tenantId(providerRequest))
                .flatMap(Optional::stream)
                .findFirst()
                .orElseGet(() -> findTenantIdFromRedirects(providerRequest));

        return authenticateWithTenant(tenantId, providerRequest);
    }

    private AuthenticationResponse authenticateWithTenant(String tenantId, ProviderRequest providerRequest) {
        Optional<TenantAuthenticationHandler> tenantHandler = tenantAuthHandlers.get(tenantId);
        if (tenantHandler.isPresent()) {
            return tenantHandler.get().authenticate(tenantId, providerRequest);
        } else {
            TenantConfig possibleConfig = tenantConfigFinders.stream()
                    .map(tenantConfigFinder -> tenantConfigFinder.config(tenantId))
                    .flatMap(Optional::stream)
                    .findFirst()
                    .orElse(oidcConfig.tenantConfig(tenantId));
            Tenant tenant = Tenant.create(oidcConfig, possibleConfig);
            TenantAuthenticationHandler handler = new TenantAuthenticationHandler(oidcConfig,
                                                                                  tenant,
                                                                                  useJwtGroups,
                                                                                  optional);
            return tenantAuthHandlers.computeValue(tenantId, () -> Optional.of(handler)).get()
                    .authenticate(tenantId, providerRequest);
        }
    }

    private String findTenantIdFromRedirects(ProviderRequest providerRequest) {
        List<String> missingLocations = new LinkedList<>();
        Optional<String> tenantId = Optional.empty();
        missingLocations.add("tenant-id-finder");
        if (oidcConfig.useParam()) {
            tenantId = providerRequest.env().queryParams().first(oidcConfig.tenantParamName()).asOptional();

            if (tenantId.isEmpty()) {
                missingLocations.add("query-param");
            }
        }
        if (oidcConfig.useCookie() && tenantId.isEmpty()) {
            Optional<String> cookie = oidcConfig.tenantCookieHandler()
                    .findCookie(providerRequest.env().headers());

            if (cookie.isPresent()) {
                return cookie.get();
            }
            missingLocations.add("cookie");
        }
        if (tenantId.isPresent()) {
            return tenantId.get();
        } else {
            if (LOGGER.isLoggable(Level.DEBUG)) {
                LOGGER.log(Level.DEBUG,
                           "Missing tenant id, could not find in either of: " + missingLocations
                                   + "Falling back to the default tenant id: " + DEFAULT_TENANT_ID);
            }
            return DEFAULT_TENANT_ID;
        }
    }

    @Override
    public boolean isOutboundSupported(ProviderRequest providerRequest,
                                       SecurityEnvironment outboundEnv,
                                       EndpointConfig outboundConfig) {
        if (!propagate) {
            return false;
        }

        return this.outboundConfig.findTarget(outboundEnv).propagate;
    }

    @Override
    public OutboundSecurityResponse outboundSecurity(ProviderRequest providerRequest,
                                                     SecurityEnvironment outboundEnv,
                                                     EndpointConfig outboundEndpointConfig) {
        return switch (oidcConfig.outboundType()) {
            case USER_JWT -> propagateAccessToken(providerRequest, outboundEnv);
            case CLIENT_CREDENTIALS -> clientCredentials(providerRequest, outboundEnv);
        };
    }

    private OutboundSecurityResponse propagateAccessToken(ProviderRequest providerRequest,
                                                                 SecurityEnvironment outboundEnv) {
        Optional<Subject> user = providerRequest.securityContext().user();

        if (user.isPresent()) {
            // we do have a user, let's see if we can propagate
            Subject subject = user.get();
            Optional<TokenCredential> tokenCredential = subject.publicCredential(TokenCredential.class);
            if (tokenCredential.isPresent()) {
                String tokenContent = tokenCredential.get()
                        .token();

                OidcOutboundTarget target = outboundConfig.findTarget(outboundEnv);
                boolean enabled = target.propagate;

                if (enabled) {
                    Map<String, List<String>> headers = new HashMap<>(outboundEnv.headers());
                    target.tokenHandler.header(headers, tokenContent);
                    return OutboundSecurityResponse.withHeaders(headers);
                }
            }
        }
        return OutboundSecurityResponse.empty();
    }

    private OutboundSecurityResponse clientCredentials(ProviderRequest providerRequest, SecurityEnvironment outboundEnv) {
        OidcOutboundTarget target = outboundConfig.findTarget(outboundEnv);
        boolean enabled = target.propagate;
        if (enabled) {
            Parameters.Builder formBuilder = Parameters.builder("oidc-form-params")
                    .add("grant_type", "client_credentials");

            if (!oidcConfig.baseScopes().isEmpty()) {
                formBuilder.add("scope", oidcConfig.baseScopes());
            }

            HttpClientRequest postRequest = oidcConfig.appWebClient()
                    .post()
                    .uri(oidcConfig.tokenEndpointUri());

            OidcUtil.updateRequest(OidcConfig.RequestType.ID_AND_SECRET_TO_TOKEN, oidcConfig, formBuilder, postRequest);

            try (var response = postRequest.submit(formBuilder.build())) {
                if (response.status().family() == Status.Family.SUCCESSFUL) {
                    JsonObject jsonObject = response.as(JsonObject.class);
                    String accessToken = jsonObject.getString("access_token");

                    Map<String, List<String>> headers = new HashMap<>(outboundEnv.headers());
                    target.tokenHandler.header(headers, accessToken);
                    return OutboundSecurityResponse.withHeaders(headers);
                } else {
                    return OutboundSecurityResponse.builder()
                            .status(SecurityResponse.SecurityStatus.FAILURE)
                            .description("Could not obtain access token from the identity server")
                            .build();
                }
            } catch (Exception e) {
                return OutboundSecurityResponse.builder()
                        .status(SecurityResponse.SecurityStatus.FAILURE)
                        .description("An error occurred while obtaining access token from the identity server")
                        .throwable(e)
                        .build();
            }
        }
        return OutboundSecurityResponse.empty();
    }

    /**
     * Builder for {@link OidcProvider}.
     */
    @Configured(prefix = OidcProviderService.PROVIDER_CONFIG_KEY,
                description = "Open ID Connect security provider",
                provides = {AuthenticationProvider.class, SecurityProvider.class})
    public static final class Builder implements io.helidon.common.Builder<Builder, OidcProvider> {

        private static final int BUILDER_WEIGHT = 300;
        private static final int DEFAULT_WEIGHT = 100;

        private final HelidonServiceLoader.Builder<TenantConfigProvider> tenantConfigProviders = HelidonServiceLoader
                .builder(ServiceLoader.load(TenantConfigProvider.class))
                .defaultWeight(DEFAULT_WEIGHT);
        private final HelidonServiceLoader.Builder<TenantIdProvider> tenantIdProviders = HelidonServiceLoader
                .builder(ServiceLoader.load(TenantIdProvider.class))
                .defaultWeight(DEFAULT_WEIGHT);
        private boolean optional = false;
        private OidcConfig oidcConfig;
        private List<TenantIdFinder> tenantIdFinders;
        private List<TenantConfigFinder> tenantConfigFinders;
        // identity propagation is disabled by default. In general we should not reuse the same token
        // for outbound calls, unless it is the same audience
        private Boolean propagate;
        private boolean useJwtGroups = true;
        private OutboundConfig outboundConfig;
        private Config config = Config.empty();
        private TokenHandler defaultOutboundHandler = TokenHandler.builder()
                .tokenHeader("Authorization")
                .tokenPrefix("Bearer ")
                .build();

        @Override
        public OidcProvider build() {
            if (null == oidcConfig) {
                throw new IllegalArgumentException("OidcConfig must be configured");
            }
            tenantIdProviders.addService(new DefaultTenantIdProvider());
            tenantConfigFinders = tenantConfigProviders.build().asList().stream()
                    .map(provider -> provider.createTenantConfigFinder(config))
                    .collect(Collectors.toList());
            tenantIdFinders = tenantIdProviders.build().asList().stream()
                    .map(provider -> provider.createTenantIdFinder(config))
                    .collect(Collectors.toList());

            if (outboundConfig == null) {
                outboundConfig = OutboundConfig.builder()
                        .build();
            }
            if (propagate == null) {
                propagate = !outboundConfig.targets().isEmpty();
            }
            return new OidcProvider(this, new OidcOutboundConfig(outboundConfig, defaultOutboundHandler));
        }

        /**
         * Update this builder with configuration.
         * Only updates information that was not explicitly set.
         *
         * The following configuration options are used:
         *
         * <table class="config">
         * <caption>Optional configuration parameters</caption>
         * <tr>
         *     <th>key</th>
         *     <th>default value</th>
         *     <th>description</th>
         * </tr>
         * <tr>
         *     <td>&nbsp;</td>
         *     <td>&nbsp;</td>
         *     <td>The current config node is used to construct {@link io.helidon.security.providers.oidc.common.OidcConfig}.</td>
         * </tr>
         * <tr>
         *     <td>propagate</td>
         *     <td>false</td>
         *     <td>Whether to propagate token (overall configuration). If set to false, propagation will
         *     not be done at all.</td>
         * </tr>
         * <tr>
         *     <td>outbound</td>
         *     <td>&nbsp;</td>
         *     <td>Configuration of {@link io.helidon.security.providers.common.OutboundConfig}.
         *     In addition you can use {@code propagate} to disable propagation for an outbound target,
         *     and {@code token} to configure outbound {@link io.helidon.security.util.TokenHandler} for an
         *     outbound target. Default token handler uses {@code Authorization} header with a {@code bearer } prefix</td>
         * </tr>
         * </table>
         *
         * @param config OIDC provider configuration
         * @return updated builder instance
         */
        public Builder config(Config config) {
            this.config = config;
            config.get("optional").asBoolean().ifPresent(this::optional);
            if (null == oidcConfig) {
                if (config.get("identity-uri").exists()) {
                    oidcConfig = OidcConfig.create(config);
                }
            }
            config.get("propagate").asBoolean().ifPresent(this::propagate);
            if (null == outboundConfig) {
                // the OutboundConfig.create() expects the provider configuration, not the outbound configuration
                Config outboundConfig = config.get("outbound");
                if (outboundConfig.exists()) {
                    outboundConfig(OutboundConfig.create(config));
                }
            }
            config.get("use-jwt-groups").asBoolean().ifPresent(this::useJwtGroups);
            config.get("discover-tenant-config-providers").asBoolean().ifPresent(this::discoverTenantConfigProviders);
            config.get("discover-tenant-id-providers").asBoolean().ifPresent(this::discoverTenantIdProviders);
            return this;
        }

        /**
         * Whether to propagate identity.
         *
         * @param propagate whether to propagate identity (true) or not (false)
         * @return updated builder instance
         */
        @ConfiguredOption("false")
        public Builder propagate(boolean propagate) {
            this.propagate = propagate;
            return this;
        }

        /**
         * Configuration of outbound rules.
         *
         * @param config outbound configuration
         *
         * @return updated builder instance
         */
        @ConfiguredOption(mergeWithParent = true)
        public Builder outboundConfig(OutboundConfig config) {
            this.outboundConfig = config;
            return this;
        }

        /**
         * Configuration of OIDC (Open ID Connect).
         *
         * @param config OIDC configuration for this provider
         *
         * @return updated builder instance
         */
        @ConfiguredOption(mergeWithParent = true)
        public Builder oidcConfig(OidcConfig config) {
            this.oidcConfig = config;
            return this;
        }

        /**
         * Whether authentication is required.
         * By default, request will fail if the authentication cannot be verified.
         * If set to true, request will process and this provider will abstain.
         *
         * @param optional whether authentication is optional (true) or required (false)
         * @return updated builder instance
         */
        @ConfiguredOption("false")
        public Builder optional(boolean optional) {
            this.optional = optional;
            return this;
        }

        /**
         * Claim {@code groups} from JWT will be used to automatically add
         *  groups to current subject (may be used with {@link jakarta.annotation.security.RolesAllowed} annotation).
         *
         * @param useJwtGroups whether to use {@code groups} claim from JWT to retrieve roles
         * @return updated builder instance
         */
        @ConfiguredOption("true")
        public Builder useJwtGroups(boolean useJwtGroups) {
            this.useJwtGroups = useJwtGroups;
            return this;
        }

        /**
         * Whether to allow {@link TenantConfigProvider} service loader discovery.
         * Default value is {@code true}.
         *
         * @param discoverConfigProviders whether to use service loader
         * @return updated builder instance
         */
        public Builder discoverTenantConfigProviders(boolean discoverConfigProviders) {
            tenantConfigProviders.useSystemServiceLoader(discoverConfigProviders);
            return this;
        }

        /**
         * Whether to allow {@link TenantIdFinder} service loader discovery.
         * Default value is {@code true}.
         *
         * @param discoverIdProviders whether to use service loader
         * @return updated builder instance
         */
        public Builder discoverTenantIdProviders(boolean discoverIdProviders) {
            tenantIdProviders.useSystemServiceLoader(discoverIdProviders);
            return this;
        }


        /**
         * Add specific {@link TenantConfigFinder} implementation.
         * Priority {@link #BUILDER_WEIGHT} is used.
         *
         * @param configFinder config finder implementation
         * @return updated builder instance
         */
        public Builder addTenantConfigFinder(TenantConfigFinder configFinder) {
            return addTenantConfigFinder(configFinder, BUILDER_WEIGHT);
        }

        /**
         * Add specific {@link TenantConfigFinder} implementation with specific priority.
         *
         * @param configFinder config finder implementation
         * @param priority finder priority
         * @return updated builder instance
         */
        public Builder addTenantConfigFinder(TenantConfigFinder configFinder, int priority) {
            tenantConfigProviders.addService(config -> configFinder, priority);
            return this;
        }

        /**
         * Add specific {@link TenantIdFinder} implementation.
         * Priority {@link #BUILDER_WEIGHT} is used.
         *
         * @param idFinder id finder implementation
         * @return updated builder instance
         */
        public Builder addTenantConfigFinder(TenantIdFinder idFinder) {
            return addTenantConfigFinder(idFinder, BUILDER_WEIGHT);
        }

        /**
         * Add specific {@link TenantIdFinder} implementation with specific priority.
         *
         * @param idFinder id finder implementation
         * @param priority finder priority
         * @return updated builder instance
         */
        public Builder addTenantConfigFinder(TenantIdFinder idFinder, int priority) {
            tenantIdProviders.addService(config -> idFinder, priority);
            return this;
        }

    }

    private static final class OidcOutboundConfig {
        private final ReentrantLock targetCacheLock = new ReentrantLock();
        private final Map<OutboundTarget, OidcOutboundTarget> targetCache = new HashMap<>();
        private final OutboundConfig outboundConfig;
        private final TokenHandler defaultTokenHandler;
        private final OidcOutboundTarget defaultTarget;

        private OidcOutboundConfig(OutboundConfig outboundConfig, TokenHandler defaultTokenHandler) {
            this.outboundConfig = outboundConfig;
            this.defaultTokenHandler = defaultTokenHandler;

            this.defaultTarget = new OidcOutboundTarget(true, defaultTokenHandler);
        }

        private boolean hasOutbound() {
            return !outboundConfig.targets().isEmpty();
        }

        private OidcOutboundTarget findTarget(SecurityEnvironment env) {
            return outboundConfig.findTarget(env)
                    .map(value -> {
                        try {
                            targetCacheLock.lock();
                            return targetCache.computeIfAbsent(value, outboundTarget -> {
                                boolean propagate = outboundTarget.getConfig()
                                        .flatMap(cfg -> cfg.get("propagate").asBoolean().asOptional())
                                        .orElse(true);
                                TokenHandler handler = outboundTarget.getConfig()
                                        .flatMap(cfg -> cfg.get("outbound-token")
                                                .map(TokenHandler::create)
                                                .asOptional())
                                        .orElse(defaultTokenHandler);
                                return new OidcOutboundTarget(propagate, handler);
                            });
                        } finally {
                            targetCacheLock.unlock();
                        }
                    }).orElse(defaultTarget);
        }
    }

    private static final class OidcOutboundTarget {
        private final boolean propagate;
        private final TokenHandler tokenHandler;

        private OidcOutboundTarget(boolean propagate, TokenHandler handler) {
            this.propagate = propagate;
            tokenHandler = handler;
        }
    }
}

