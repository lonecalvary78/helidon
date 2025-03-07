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

package io.helidon.webserver.security;

import java.util.Optional;

import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.http.HttpMediaTypes;
import io.helidon.security.Security;
import io.helidon.security.SecurityContext;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpServer;

/**
 * Unit test for {@link SecurityHttpFeature}.
 */
@ServerTest
public class WebSecurityFromConfigTest extends WebSecurityTests {

    WebSecurityFromConfigTest(WebServer server, Http1Client webClient) {
        super(server, webClient);
    }

    @SetUpServer
    public static void setup(WebServerConfig.Builder serverBuilder) {
        UnitTestAuditProvider myAuditProvider = new UnitTestAuditProvider();

        Config config = Config.just(ConfigSources.classpath("security-application.yaml"));

        Security security = Security.builder()
                .config(config.get("security"))
                .addAuditProvider(myAuditProvider)
                .build();
        // needed for other features, such as integration with webserver
        Context context = Contexts.context()
                .orElseGet(Contexts::globalContext);

        context.register(security);
        context.register(myAuditProvider);

        serverBuilder
                .config(config.get("server"))
                .routing(routing -> routing.get("/*", (req, res) -> {
                    Optional<SecurityContext> securityContext = Contexts.context()
                            .flatMap(ctx -> ctx.get(SecurityContext.class));
                    res.headers().contentType(HttpMediaTypes.PLAINTEXT_UTF_8);
                    res.send("Hello, you are: \n" + securityContext
                            .map(ctx -> ctx.user().orElse(SecurityContext.ANONYMOUS).toString())
                            .orElse("Security context is null"));
                }));

    }
}
