/*
 * Copyright 2022-2026 benelog GmbH & Co. KG
 *
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 */
package io.openepcis.storage.service.runtime;

import io.openepcis.capture.context.CaptureContext;
import io.openepcis.capture.context.CloudStorageResult;
import io.openepcis.capture.context.StorageService;
import io.openepcis.model.dto.CaptureJobStatusMessage;
import io.openepcis.service.config.CloudStorageConfig;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.MutinyEmitter;
import io.smallrye.reactive.messaging.kafka.Record;
import org.apache.commons.io.IOUtils;
import org.eclipse.microprofile.context.ManagedExecutor;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class NoOpStorageServiceImpl implements StorageService {
    final CloudStorageConfig config;
    final ManagedExecutor managedExecutor;
    final MutinyEmitter<Record<String, CaptureJobStatusMessage>> captureJobEmitter;

    NoOpStorageServiceImpl(final CloudStorageConfig config, final ManagedExecutor managedExecutor, final MutinyEmitter<Record<String, CaptureJobStatusMessage>> captureJobEmitter) {
        this.config = config;
        this.managedExecutor = managedExecutor;
        this.captureJobEmitter = captureJobEmitter;
    }

    @Override
    public Uni<CaptureContext> documentUpload(final CaptureContext context, final InputStream inputStream) {
        // Send the initial "Captured" job status
        final Uni<Void> initialStatus = captureJobEmitter.send(Record.of(context.getCaptureID(), context.toCaptureJobStatusMessage()));
        // Consume the stream and trigger subsequent status updates
        return initialStatus.chain(emitterStatus -> {
            managedExecutor.supplyAsync(() -> {
                try {
                    // Ignore the received inputStream as we don't do anything with it and consuming it is blocking
                    IOUtils.consume(inputStream);
                    // trigger the upload complete callback
                    context.uploadDocumentComplete();
                    Log.debug(String.format("Document successfully consumed for captureID in NoOpStorageService = %s", context.getCaptureID()));
                } catch (Exception e) {
                    context.fail(e);
                    throw new RuntimeException(e);
                }
                return context;
            });
            return Uni.createFrom().item(context);
        });
    }

    @Override
    public Uni<CloudStorageResult> documentUpload(final String storagePath, final String captureId, final String contentType, final Optional<Long> contentLength, final Map<String, String> tags, final InputStream inputStream) {
        return Uni.createFrom().item(() -> {
            managedExecutor.supplyAsync(() -> {
                try {
                    IOUtils.consume(inputStream);
                    Log.info(String.format("Document successfully consumed for captureID in NoOpStorageService=%s", captureId));
                } catch (Exception e) {
                    throw new RuntimeException("Error consuming input stream for captureID=" + captureId, e);
                }
                return null;
            });
            return CloudStorageResult.builder().bucketName("").storageKey("").eTag("").versionId("").build();
        });
    }

    @Override
    public void userExtensionSchemaUpload(final String keyToSchema, final Path tempPathToSchema, final Map<String, String> tags, final long contentLength) {
        throw new UnsupportedOperationException("Getting stream from NoOpStorageService is not supported for : " + keyToSchema);
    }

    @Override
    public InputStream get(final String storagePath) {
        throw new UnsupportedOperationException("Getting stream from NoOpStorageService is not supported for : " + storagePath);
    }

    @Override
    public Uni<CloudStorageResult> put(final String storagePath, final String contentType, final Optional<Long> contentLength, final Map<String, String> tags, final InputStream inputStream) {
        throw new UnsupportedOperationException("Updating existing stream from NoOpStorageService is not supported for : " + storagePath);
    }

    @Override
    public Uni<Boolean> addTags(final String storagePath, final Map<String, String> tags) {
        throw new UnsupportedOperationException("Updating of the tags from NoOpStorageService is not supported for : " + storagePath);
    }
}
