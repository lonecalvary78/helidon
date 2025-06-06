/*
 * Copyright (c) 2021, 2025 Oracle and/or its affiliates.
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

package io.helidon.integrations.common.rest;

import java.io.InputStream;
import java.util.Map;
import java.util.function.Consumer;

import io.helidon.common.socket.SocketOptions;
import io.helidon.config.Config;
import io.helidon.faulttolerance.FaultTolerance;
import io.helidon.faulttolerance.FtHandler;
import io.helidon.http.Method;
import io.helidon.http.media.jsonp.JsonpSupport;
import io.helidon.integrations.common.rest.ApiOptionalResponse.BuilderBase;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.api.WebClientConfig;
import io.helidon.webclient.http1.Http1Client;

import jakarta.json.Json;
import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonObject;
import jakarta.json.JsonReaderFactory;
import jakarta.json.JsonWriterFactory;

/**
 * JSON based REST API operations.
 */
public interface RestApi {
    /**
     * Get with an optional response. In case the call returns {@link io.helidon.http.Status#NOT_FOUND_404}
     * this would return an empty optional entity, rather than fail.
     * This may also be the case for requests that use {@code If-Modified-Since} that return a
     * {@link io.helidon.http.Status#NOT_MODIFIED_304} response code.
     *
     * @param path            path to invoke
     * @param request         request to use
     * @param responseBuilder builder with appropriate response processor
     * @param <R>             type of the optional part of the response
     * @param <T>             type of the response
     * @return response
     */
    default <R, T extends ApiOptionalResponse<R>> T get(String path,
                                                        ApiRequest<?> request,
                                                        BuilderBase<?, T, JsonObject, R> responseBuilder) {

        return invokeOptional(Method.GET, path, request, responseBuilder);
    }

    /**
     * Get with a response consisting of a stream.
     *
     * @param path            path to invoke
     * @param request         request to use
     * @param responseBuilder builder with appropriate response processor
     * @param <R>             type of the optional part of the response
     * @param <T>             type of the response
     * @return response
     */
    default <R, T extends ApiOptionalResponse<R>> T getEntityStream(String path,
                                                                    ApiRequest<?> request,
                                                                    BuilderBase<?, T, InputStream, R> responseBuilder) {
        return invokeEntityResponse(Method.GET, path, request, responseBuilder);
    }

    /**
     * Get bytes with an optional response.
     *
     * @param path            path to invoke
     * @param request         request to use
     * @param responseBuilder builder with appropriate
     *                        {@link BuilderBase#entityProcessor(java.util.function.Function)}
     * @param <R>             type of the optional part of the response
     * @param <T>             type of the response
     * @return response, that may or may not contain an entity (for 404 the entity would be empty)
     */
    default <R, T extends ApiOptionalResponse<R>> T getBytes(String path,
                                                             ApiRequest<?> request,
                                                             BuilderBase<?, T, byte[], R> responseBuilder) {
        return invokeBytesResponse(Method.GET, path, request, responseBuilder);
    }

    /**
     * Post without a response entity.
     *
     * @param path            path to invoke
     * @param request         request to use, should be an instance of {@link ApiJsonRequest} if
     *                        an entity is desired
     * @param responseBuilder builder to construct response from API call
     * @param <T>             type of the response
     * @return response
     */
    default <T extends ApiResponse> T post(String path,
                                           ApiRequest<?> request,
                                           ApiResponse.Builder<?, T> responseBuilder) {
        return invoke(Method.POST, path, request, responseBuilder);
    }

    /**
     * Put without a response entity.
     *
     * @param path            path to invoke
     * @param request         request to use, should be an instance of {@link ApiJsonRequest} if
     *                        an entity is desired
     * @param responseBuilder builder to construct response from API call
     * @param <T>             type of the response
     * @return response
     */
    default <T extends ApiResponse> T put(String path, ApiRequest<?> request,
                                          ApiResponse.Builder<?, T> responseBuilder) {
        return invoke(Method.PUT, path, request, responseBuilder);
    }

    /**
     * Delete without a response entity.
     *
     * @param path            path to invoke
     * @param request         request to use, should be an instance of {@link ApiJsonRequest} if
     *                        an entity is desired
     * @param responseBuilder builder to construct response from API call
     * @param <T>             type of the response
     * @return response
     */
    default <T extends ApiResponse> T delete(String path,
                                             ApiRequest<?> request,
                                             ApiResponse.Builder<?, T> responseBuilder) {
        return invoke(Method.DELETE, path, request, responseBuilder);
    }

    /**
     * Invoke a request that is not expected to yield an entity.
     *
     * @param method          HTTP method to invoke
     * @param path            path to invoke
     * @param request         request to use, should be an instance of
     * {@link io.helidon.integrations.common.rest.ApiJsonRequest} if
     *                        and entity is desired
     * @param responseBuilder builder to construct response from API call
     * @param <T>             type of the response
     * @return response
     */
    <T extends ApiResponse> T invoke(Method method,
                                     String path,
                                     ApiRequest<?> request,
                                     ApiResponse.Builder<?, T> responseBuilder);

    /**
     * Invoke a request that is expected to yield an entity.
     *
     * @param method          HTTP method to invoke
     * @param path            path to invoke
     * @param request         request to use, should be an instance of
     * {@link io.helidon.integrations.common.rest.ApiJsonRequest} if
     *                        and entity is desired
     * @param responseBuilder builder to construct response from API call
     * @param <T>             type of the response
     * @return future with entity and metadata if successful, future with error otherwise
     */
    <T extends ApiEntityResponse> T invokeWithResponse(Method method,
                                                       String path,
                                                       ApiRequest<?> request,
                                                       ApiEntityResponse.Builder<?, T, JsonObject> responseBuilder);

    /**
     * The request media type should be provided in request, falls back to
     * {@link io.helidon.common.media.type.MediaTypes#APPLICATION_OCTET_STREAM}.
     *
     * @param method          method to invoke
     * @param path            path to invoke
     * @param request         request used to configure query parameters and headers
     * @param is              entity bytes
     * @param responseBuilder builder to construct response from API call
     * @param <T>             type of the response
     * @return future with the response or error
     */
    <T extends ApiResponse> T invokeBytesRequest(Method method,
                                                 String path,
                                                 ApiRequest<?> request,
                                                 InputStream is,
                                                 ApiResponse.Builder<?, T> responseBuilder);

    /**
     * Invoke API call that is expected to return bytes as a publisher.
     * <p>
     * The accepted media type must be provided in request, falls back to
     * {@link io.helidon.common.media.type.MediaTypes#APPLICATION_OCTET_STREAM}.
     *
     * @param method          method to invoke
     * @param path            path to invoke
     * @param request         request used to configure query parameters and headers
     * @param responseBuilder builder to construct response from API call with appropriate processor to
     *                        handle the returned publisher
     * @param <R>             type of the optional part of the response
     * @param <T>             type of the response
     * @return future with the response
     */
    <R, T extends ApiOptionalResponse<R>> T invokeEntityResponse(Method method,
                                                                 String path,
                                                                 ApiRequest<?> request,
                                                                 BuilderBase<?, T, InputStream, R> responseBuilder);

    /**
     * Invoke API call that is expected to return bytes.
     * This method collects all bytes in memory, so it cannot be used for large data.
     * See
     * {@link #invokeEntityResponse(io.helidon.http.Method, String, ApiRequest, BuilderBase)}.
     * <p>
     * The accepted media type must be provided in request, falls back to
     * {@link io.helidon.common.media.type.MediaTypes#APPLICATION_OCTET_STREAM}.
     *
     * @param method          method to invoke
     * @param path            path to invoke
     * @param request         request used to configure query parameters and headers
     * @param responseBuilder builder to construct response from API call with appropriate processor to
     *                        handle the returned bytes
     * @param <R>             type of the optional part of the response
     * @param <T>             type of the response
     * @return response
     */
    <R, T extends ApiOptionalResponse<R>> T invokeBytesResponse(Method method,
                                                                String path,
                                                                ApiRequest<?> request,
                                                                BuilderBase<?, T, byte[], R> responseBuilder);

    /**
     * Invoke a request that may yield an entity.
     * The entity is expected to be missing if {@link io.helidon.http.Status#NOT_FOUND_404} is returned by the API call (and for some
     * other cases, such as not modified).
     *
     * @param method          HTTP method to invoke
     * @param path            path to invoke
     * @param request         request to use, should be an instance of {@link ApiJsonRequest} if
     *                        and entity is desired
     * @param responseBuilder response builder with appropriate processor to create the optional part
     * @param <R>             type of the optional part of the response
     * @param <T>             type of the response
     * @return response
     */
    <R, T extends ApiOptionalResponse<R>> T invokeOptional(Method method,
                                                           String path,
                                                           ApiRequest<?> request,
                                                           BuilderBase<?, T, JsonObject, R> responseBuilder);

    /**
     * Base builder for REST APIs.
     *
     * @param <B> type of the builder (a subclass of this class)
     * @param <T> type of the built {@link io.helidon.integrations.common.rest.RestApi}
     */
    abstract class Builder<B extends Builder<B, T>, T extends RestApi> implements io.helidon.common.Builder<B, T> {
        private final WebClientConfig.Builder webClientBuilder = WebClient.builder()
                .followRedirects(true)
                .keepAlive(true);
        private FtHandler ftHandler = FaultTolerance.builder().build();
        private JsonBuilderFactory jsonBuilderFactory;
        private JsonReaderFactory jsonReaderFactory;
        private JsonWriterFactory jsonWriterFactory;
        private WebClient webClient;

        /**
         * This method is final, as we need to call the {@link #preBuild()}, {@link #doBuild()} and {@link #postBuild()}
         * methods.
         *
         * @return built instance
         */
        @Override
        public final T build() {
            preBuild();
            T result = doBuild();
            postBuild();

            return result;
        }

        @Override
        public final T get() {
            return build();
        }

        /**
         * Configure this builder from config.
         * Uses {@code webclient} key to configure the {@link Http1Client} builder, and {@code jsonp} key
         * to configure the JSON factories.
         *
         * @param config configuration on the node of this rest API
         * @return updated builder
         */
        public B config(Config config) {

            webClientBuilder.config(config.get("webclient"));
            Map<String, String> jsonConfig = config.get("jsonp")
                    .asMap()
                    .orElseGet(Map::of);

            if (jsonBuilderFactory == null) {
                jsonBuilderFactory = Json.createBuilderFactory(jsonConfig);
            }
            if (jsonReaderFactory == null) {
                jsonReaderFactory = Json.createReaderFactory(jsonConfig);
            }
            if (jsonWriterFactory == null) {
                jsonWriterFactory = Json.createWriterFactory(jsonConfig);
            }

            return me();
        }

        /**
         * Configure the fault tolerance handler to use with client requests.
         *
         * @param ftHandler fault tolerance handler
         * @return updated builder
         */
        public B faultTolerance(FtHandler ftHandler) {
            this.ftHandler = ftHandler;
            return me();
        }

        /**
         * Update web client builder.
         * This can be used to configure
         * {@link WebClientConfig.Builder#socketOptions(SocketOptions)}.
         *
         * @param updater consumer that updates the web client builder
         * @return updated builder instance
         */
        public B webClientBuilder(Consumer<WebClientConfig.Builder> updater) {
            updater.accept(this.webClientBuilder);
            return me();
        }

        /**
         * Allows returning the correct type when subclassing.
         *
         * @return this instance typed as subclass
         */
        @SuppressWarnings("unchecked")
        protected B me() {
            return (B) this;
        }

        /**
         * Pre build method.
         * This implementation builds the web client and sets up JSON factories.
         */
        protected void preBuild() {
            webClient = webClientBuilder.addMediaSupport(JsonpSupport.create()).build();
            if (jsonBuilderFactory == null) {
                jsonBuilderFactory = Json.createBuilderFactory(Map.of());
            }
            if (jsonReaderFactory == null) {
                jsonReaderFactory = Json.createReaderFactory(Map.of());
            }
            if (jsonWriterFactory == null) {
                jsonWriterFactory = Json.createWriterFactory(Map.of());
            }
        }

        /**
         * Post build method.
         * This implementation does nothing
         */
        protected void postBuild() {
        }

        /**
         * Build an instance of {@link io.helidon.integrations.common.rest.RestApi} implementation.
         *
         * @return a new instance
         */
        protected abstract T doBuild();

        /**
         * WebClient instance, available after {@link #postBuild()} is called.
         *
         * @return web client
         */
        protected WebClient webClient() {
            return webClient;
        }

        /**
         * Configured Fault tolerance handler.
         *
         * @return handler
         */
        protected FtHandler ftHandler() {
            return ftHandler;
        }

        /**
         * JSON builder factory.
         *
         * @return builder factory
         */
        protected JsonBuilderFactory jsonBuilderFactory() {
            return jsonBuilderFactory;
        }

        /**
         * JSON reader factory.
         *
         * @return reader factory
         */
        protected JsonReaderFactory jsonReaderFactory() {
            return jsonReaderFactory;
        }

        /**
         * JSON writer factory.
         *
         * @return writer factory
         */
        protected JsonWriterFactory jsonWriterFactory() {
            return jsonWriterFactory;
        }
    }
}
