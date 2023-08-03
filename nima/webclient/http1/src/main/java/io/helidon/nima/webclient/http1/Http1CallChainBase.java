/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.nima.webclient.http1;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.Bytes;
import io.helidon.common.buffers.DataReader;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.http.ClientRequestHeaders;
import io.helidon.common.http.ClientResponseHeaders;
import io.helidon.common.http.Headers;
import io.helidon.common.http.Http;
import io.helidon.common.http.Http1HeadersParser;
import io.helidon.common.http.WritableHeaders;
import io.helidon.common.socket.HelidonSocket;
import io.helidon.nima.common.tls.Tls;
import io.helidon.nima.http.encoding.ContentDecoder;
import io.helidon.nima.http.encoding.ContentEncodingContext;
import io.helidon.nima.webclient.api.ClientConnection;
import io.helidon.nima.webclient.api.ClientUri;
import io.helidon.nima.webclient.api.HttpClientConfig;
import io.helidon.nima.webclient.api.Proxy;
import io.helidon.nima.webclient.api.WebClient;
import io.helidon.nima.webclient.api.WebClientServiceRequest;
import io.helidon.nima.webclient.api.WebClientServiceResponse;
import io.helidon.nima.webclient.spi.WebClientService;

import static java.lang.System.Logger.Level.TRACE;
import static java.nio.charset.StandardCharsets.US_ASCII;

abstract class Http1CallChainBase implements WebClientService.Chain {
    private static final System.Logger LOGGER = System.getLogger(Http1CallChainBase.class.getName());

    private final BufferData writeBuffer = BufferData.growing(128);
    private final WebClient webClient;
    private final HttpClientConfig clientConfig;
    private final Http1ClientProtocolConfig protocolConfig;
    private final ClientConnection connection;
    private final Tls tls;
    private final Proxy proxy;
    private final boolean keepAlive;
    private final CompletableFuture<WebClientServiceResponse> whenComplete;
    private final Duration timeout;
    private ClientConnection effectiveConnection;

    Http1CallChainBase(WebClient webClient,
                       HttpClientConfig clientConfig,
                       Http1ClientProtocolConfig protocolConfig,
                       Http1ClientRequestImpl clientRequest,
                       CompletableFuture<WebClientServiceResponse> whenComplete) {
        this.webClient = webClient;
        this.clientConfig = clientConfig;
        this.protocolConfig = protocolConfig;
        this.timeout = clientRequest.readTimeout();
        this.connection = clientRequest.connection().orElse(null);
        this.tls = clientRequest.tls();
        this.proxy = clientRequest.proxy();
        this.keepAlive = clientRequest.keepAlive();
        this.whenComplete = whenComplete;
    }

    static void writeHeaders(Headers headers, BufferData bufferData, boolean validate) {
        for (Http.HeaderValue header : headers) {
            if (validate) {
                header.validate();
            }
            header.writeHttp1Header(bufferData);
        }
        bufferData.write(Bytes.CR_BYTE);
        bufferData.write(Bytes.LF_BYTE);
    }

    @Override
    public WebClientServiceResponse proceed(WebClientServiceRequest serviceRequest) {
        // either use the explicit connection, or obtain one (keep alive or one-off)
        effectiveConnection = connection == null ? obtainConnection(serviceRequest) : connection;
        effectiveConnection.readTimeout(this.timeout);

        DataWriter writer = effectiveConnection.writer();
        DataReader reader = effectiveConnection.reader();
        ClientUri uri = serviceRequest.uri();
        ClientRequestHeaders headers = serviceRequest.headers();

        writeBuffer.clear();
        prologue(writeBuffer, serviceRequest, uri);
        headers.setIfAbsent(Http.Header.create(Http.Header.HOST, uri.authority()));

        return doProceed(effectiveConnection, serviceRequest, headers, writer, reader, writeBuffer);
    }

    abstract WebClientServiceResponse doProceed(ClientConnection connection,
                                                WebClientServiceRequest request,
                                                ClientRequestHeaders headers,
                                                DataWriter writer,
                                                DataReader reader,
                                                BufferData writeBuffer);

    void prologue(BufferData nonEntityData, WebClientServiceRequest request, ClientUri uri) {
        // TODO When proxy is implemented, change default value of Http1ClientConfig.relativeUris to false
        //  and below conditional statement to:
        //  proxy == Proxy.create() || proxy.noProxyPredicate().apply(finalUri) || clientConfig.relativeUris
        String schemeHostPort = clientConfig.relativeUris() ? "" : uri.scheme() + "://" + uri.host() + ":" + uri.port();
        nonEntityData.writeAscii(request.method().text()
                                         + " "
                                         + schemeHostPort
                                         + uri.pathWithQueryAndFragment()
                                         + " HTTP/1.1\r\n");
    }

    ClientResponseHeaders readHeaders(DataReader reader) {
        WritableHeaders<?> writable = Http1HeadersParser.readHeaders(reader,
                                                                     protocolConfig.maxHeaderSize(),
                                                                     protocolConfig.validateHeaders());
        return ClientResponseHeaders.create(writable, clientConfig.mediaTypeParserMode());
    }

    HttpClientConfig clientConfig() {
        return clientConfig;
    }

    Http1ClientProtocolConfig protocolConfig() {
        return protocolConfig;
    }

    ClientConnection connection() {
        return effectiveConnection;
    }

    protected WebClientServiceResponse readResponse(WebClientServiceRequest serviceRequest,
                                                    ClientConnection connection,
                                                    DataReader reader) {
        Http.Status responseStatus = Http1StatusParser.readStatus(reader, protocolConfig.maxStatusLineLength());
        connection.helidonSocket().log(LOGGER, TRACE, "client received status %n%s", responseStatus);
        ClientResponseHeaders responseHeaders = readHeaders(reader);
        connection.helidonSocket().log(LOGGER, TRACE, "client received headers %n%s", responseHeaders);

        WebClientServiceResponse.Builder builder = WebClientServiceResponse.builder();
        AtomicReference<WebClientServiceResponse> response = new AtomicReference<>();

        if (mayHaveEntity(responseStatus, responseHeaders)) {
            // this may be an entity (if content length is set to zero, we know there is no entity)
            builder.inputStream(inputStream(connection.helidonSocket(), response, responseHeaders, reader));
        }

        WebClientServiceResponse serviceResponse = builder
                .connection(connection)
                .headers(responseHeaders)
                .status(responseStatus)
                .whenComplete(whenComplete)
                .serviceRequest(serviceRequest)
                .build();

        response.set(serviceResponse);
        return serviceResponse;
    }

    private InputStream inputStream(HelidonSocket helidonSocket,
                                    AtomicReference<WebClientServiceResponse> response,
                                    ClientResponseHeaders responseHeaders,
                                    DataReader reader) {
        ContentEncodingContext encodingSupport = clientConfig.contentEncoding();

        ContentDecoder decoder;

        if (encodingSupport.contentDecodingEnabled() && responseHeaders.contains(Http.Header.CONTENT_ENCODING)) {
            String contentEncoding = responseHeaders.get(Http.Header.CONTENT_ENCODING).value();
            if (encodingSupport.contentDecodingSupported(contentEncoding)) {
                decoder = encodingSupport.decoder(contentEncoding);
            } else {
                throw new IllegalStateException("Unsupported content encoding: \n"
                                                        + BufferData.create(contentEncoding.getBytes(StandardCharsets.UTF_8))
                        .debugDataHex());
            }
        } else {
            decoder = ContentDecoder.NO_OP;
        }
        if (responseHeaders.contains(Http.Header.CONTENT_LENGTH)) {
            long length = responseHeaders.contentLength().getAsLong();
            return decoder.apply(new ContentLengthInputStream(helidonSocket, reader, whenComplete, response, length));
        } else if (responseHeaders.contains(Http.HeaderValues.TRANSFER_ENCODING_CHUNKED)) {
            return new ChunkedInputStream(helidonSocket, reader, whenComplete, response);
        } else {
            // we assume the rest of the connection is entity (valid for HTTP/1.0, HTTP CONNECT method etc.
            return new EverythingInputStream(helidonSocket, reader, whenComplete, response);
        }
    }

    private boolean mayHaveEntity(Http.Status responseStatus, ClientResponseHeaders responseHeaders) {
        if (responseHeaders.contains(Http.HeaderValues.CONTENT_LENGTH_ZERO)) {
            return false;
        }
        if (responseStatus == Http.Status.NO_CONTENT_204) {
            return false;
        }
        if ((
                responseHeaders.contains(Http.Header.UPGRADE)
                        && !responseHeaders.contains(Http.HeaderValues.TRANSFER_ENCODING_CHUNKED))) {
            // this is an upgrade response and there is no entity
            return false;
        }
        // if we decide to support HTTP/1.0, we may have an entity without any headers
        // in HTTP/1.1, we should have a content encoding
        return true;
    }

    private ClientConnection obtainConnection(WebClientServiceRequest request) {
        return Http1ConnectionCache.connection(webClient,
                                               clientConfig,
                                               tls,
                                               proxy,
                                               request.uri(),
                                               request.headers(),
                                               keepAlive);
    }

    static class ContentLengthInputStream extends InputStream {
        private final DataReader reader;
        private final long length;
        private final Runnable entityProcessedRunnable;
        private final HelidonSocket socket;

        private BufferData currentBuffer;
        private boolean finished;
        private long remainingLength;

        ContentLengthInputStream(HelidonSocket socket,
                                 DataReader reader,
                                 CompletableFuture<WebClientServiceResponse> whenComplete,
                                 AtomicReference<WebClientServiceResponse> response,
                                 long length) {
            this.socket = socket;
            this.reader = reader;
            this.length = length;
            this.remainingLength = length;
            // we can only get the response at the time of completion, as the instance is created after this constructor
            // returns
            this.entityProcessedRunnable = () -> whenComplete.complete(response.get());
        }

        @Override
        public int read() {
            if (finished) {
                return -1;
            }
            int maxRemaining = maxRemaining(512);
            ensureBuffer(maxRemaining);
            if (finished || currentBuffer == null) {
                return -1;
            }
            int read = currentBuffer.read();
            if (read != -1) {
                remainingLength--;
            }
            return read;
        }

        @Override
        public int read(byte[] b, int off, int len) {
            if (finished) {
                return -1;
            }
            int maxRemaining = maxRemaining(len);
            ensureBuffer(maxRemaining);
            if (finished || currentBuffer == null) {
                return -1;
            }
            int read = currentBuffer.read(b, off, len);
            remainingLength -= read;
            return read;
        }

        private int maxRemaining(int estimate) {
            return Integer.min(estimate, (int) Long.min(Integer.MAX_VALUE, remainingLength));
        }

        private void ensureBuffer(int estimate) {
            if (remainingLength == 0) {
                entityProcessedRunnable.run();
                // we have fully read the entity
                finished = true;
                currentBuffer = null;
                return;
            }

            if (currentBuffer != null && !currentBuffer.consumed()) {
                return;
            }

            reader.ensureAvailable();
            int toRead = Math.min(reader.available(), estimate);

            // read between 0 and available bytes (or estimate, which is the number of requested bytes)
            currentBuffer = reader.readBuffer(toRead);
            if (currentBuffer == null || currentBuffer == BufferData.empty()) {
                entityProcessedRunnable.run();
                finished = true;
            } else {
                socket.log(LOGGER, TRACE, "client read entity buffer %n%s", currentBuffer.debugDataHex(true));
            }
        }
    }

    static class EverythingInputStream extends InputStream {
        private final HelidonSocket helidonSocket;
        private final DataReader reader;
        private final Runnable entityProcessedRunnable;

        private BufferData currentBuffer;
        private boolean finished;

        EverythingInputStream(HelidonSocket helidonSocket,
                              DataReader reader,
                              CompletableFuture<WebClientServiceResponse> whenComplete,
                              AtomicReference<WebClientServiceResponse> response) {
            this.helidonSocket = helidonSocket;
            this.reader = reader;
            // we can only get the response at the time of completion, as the instance is created after this constructor
            // returns
            this.entityProcessedRunnable = () -> whenComplete.complete(response.get());
        }

        @Override
        public int read() {
            if (finished) {
                return -1;
            }
            ensureBuffer(512);
            if (finished || currentBuffer == null) {
                return -1;
            }
            return currentBuffer.read();
        }

        @Override
        public int read(byte[] b, int off, int len) {
            if (finished) {
                return -1;
            }
            ensureBuffer(len);
            if (finished || currentBuffer == null) {
                return -1;
            }
            return currentBuffer.read(b, off, len);
        }

        private void ensureBuffer(int estimate) {
            if (currentBuffer != null && currentBuffer.available() > 0) {
                // we did not read the previous buffer fully
                return;
            }

            reader.ensureAvailable();
            int toRead = Math.min(reader.available(), estimate);

            // read between 0 and available bytes (or estimate, which is the number of requested bytes)
            currentBuffer = reader.readBuffer(toRead);
            if (currentBuffer == null || currentBuffer == BufferData.empty()) {
                entityProcessedRunnable.run();
                finished = true;
            } else {
                helidonSocket.log(LOGGER, TRACE, "client read entity buffer %n%s", currentBuffer.debugDataHex(true));
            }
        }
    }

    static class ChunkedInputStream extends InputStream {
        private final HelidonSocket helidonSocket;
        private final DataReader reader;
        private final Runnable entityProcessedRunnable;

        private BufferData currentBuffer;
        private boolean finished;

        ChunkedInputStream(HelidonSocket helidonSocket,
                           DataReader reader,
                           CompletableFuture<WebClientServiceResponse> whenComplete,
                           AtomicReference<WebClientServiceResponse> response) {
            this.helidonSocket = helidonSocket;
            this.reader = reader;
            // we can only get the response at the time of completion, as the instance is created after this constructor
            // returns
            this.entityProcessedRunnable = () -> whenComplete.complete(response.get());
        }

        @Override
        public int read() {
            if (finished) {
                return -1;
            }
            ensureBuffer();
            if (finished || currentBuffer == null) {
                return -1;
            }
            return currentBuffer.read();
        }

        @Override
        public int read(byte[] b, int off, int len) {
            if (finished) {
                return -1;
            }
            ensureBuffer();
            if (finished || currentBuffer == null) {
                return -1;
            }
            return currentBuffer.read(b, off, len);
        }

        private void ensureBuffer() {
            if (currentBuffer != null && currentBuffer.available() > 0) {
                // we did not read the previous buffer fully
                return;
            }
            // chunked encoding - I will just read each chunk fully into memory, as that is how the protocol is designed
            int endOfChunkSize = reader.findNewLine(256);
            if (endOfChunkSize == 256) {
                entityProcessedRunnable.run();
                throw new IllegalStateException("Cannot read chunked entity, end of line not found within 256 bytes:\n"
                                                        + reader.readBuffer(Math.min(reader.available(), 256)));
            }
            String hex = reader.readAsciiString(endOfChunkSize);
            reader.skip(2); // CRLF
            int length;
            try {
                length = Integer.parseUnsignedInt(hex, 16);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Chunk size is not a number:\n"
                                                           + BufferData.create(hex.getBytes(US_ASCII)).debugDataHex());
            }
            if (length == 0) {
                reader.skip(2); // second CRLF finishing the entity

                helidonSocket.log(LOGGER, TRACE, "read last (empty) chunk");
                finished = true;
                currentBuffer = null;
                entityProcessedRunnable.run();
                return;
            }

            BufferData chunk = reader.readBuffer(length);

            if (LOGGER.isLoggable(TRACE)) {
                helidonSocket.log(LOGGER, TRACE, "client read chunk %s", chunk.debugDataHex(true));
            }

            reader.skip(2); // trailing CRLF after each chunk
            this.currentBuffer = chunk;
        }
    }
}