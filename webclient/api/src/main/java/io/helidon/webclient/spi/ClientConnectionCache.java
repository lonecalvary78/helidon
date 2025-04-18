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

package io.helidon.webclient.spi;

import io.helidon.common.resumable.Resumable;
import io.helidon.webclient.api.ReleasableResource;

import static java.lang.System.Logger.Level;

/**
 * Client connection cache with release shutdown hook to provide graceful shutdown.
 */
public abstract class ClientConnectionCache implements ReleasableResource, Resumable {

    private static final System.Logger LOGGER = System.getLogger(ClientConnectionCache.class.getName());

    protected ClientConnectionCache(boolean shared) {
        if (shared) {
            Runtime.getRuntime().addShutdownHook(new Thread(this::onShutdown));
        }
    }

    @Override
    public void suspend() {
        this.evict();
    }

    @Override
    public void resume() {

    }

    protected abstract void evict();

    private void onShutdown() {
        if (LOGGER.isLoggable(Level.DEBUG)) {
            LOGGER.log(Level.DEBUG, "Gracefully closing connections in client connection cache.");
        }
        this.releaseResource();
    }
}
