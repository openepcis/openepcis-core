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
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.commons.io.IOUtils;
import org.eclipse.microprofile.context.ManagedExecutor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.*;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class LocalStorageServiceImpl implements StorageService {
  private final Path baseStorageDir;
  private final ManagedExecutor managedExecutor;
  private final CloudStorageConfig config;
  private final MutinyEmitter<Record<String, CaptureJobStatusMessage>> captureJobEmitter;

  public LocalStorageServiceImpl(final CloudStorageConfig config, final ManagedExecutor managedExecutor, final MutinyEmitter<Record<String, CaptureJobStatusMessage>> captureJobEmitter) {
    this.config = config;
    this.managedExecutor = managedExecutor;
    this.captureJobEmitter = captureJobEmitter;
    this.baseStorageDir = Path.of(config.localPath().orElse("storage"));
    try {
      Files.createDirectories(baseStorageDir);
      Log.info(String.format("using %s as baseStorageDir", baseStorageDir.toString()));
    } catch (Exception e) {
      throw new RuntimeException("Failed to initialize local storage base directory", e);
    }
  }

  @Override
  public Uni<CaptureContext> documentUpload(final CaptureContext context, final InputStream inputStream) {
    final Uni<Void> initialStatus = captureJobEmitter.send(Record.of(context.getCaptureID(), context.toCaptureJobStatusMessage()));
    return initialStatus.chain(emitterStatus -> {
      managedExecutor.supplyAsync(() -> {
        try {
          final String fileExtension = getFileExtensionFromContentType(context.getContentType());
          final String filename = context.getCaptureID() + fileExtension;
          final Path targetPath = baseStorageDir.resolve(filename);
          Files.createDirectories(targetPath.getParent());
          try (OutputStream out = new FileOutputStream(targetPath.toFile())) {
            inputStream.transferTo(out);
          }
          context.setStorageKey(filename);
          context.setStorageBucket(baseStorageDir.toString());
          context.uploadDocumentComplete();
          Log.infof("Document saved locally for captureID=%s at %s", context.getCaptureID(), targetPath);
          return context;
        } catch (Exception e) {
          context.fail(e);
          throw new RuntimeException("Failed to store file locally", e);
        }
      });
      return Uni.createFrom().item(context);
    });
  }

  @Override
  public Uni<CloudStorageResult> documentUpload(final String storagePath, final String captureId, final String contentType, final Optional<Long> contentLength, final Map<String, String> tags, final InputStream inputStream) {
    Uni.createFrom().completionStage(managedExecutor.supplyAsync(() -> {
      try {
        final Path targetPath = baseStorageDir.resolve(storagePath);
        Files.createDirectories(targetPath.getParent());
        try (OutputStream out = new FileOutputStream(targetPath.toFile())) {
          inputStream.transferTo(out);
        }
        Log.infof("Document saved locally for captureID=%s at %s", captureId, targetPath);
        return CloudStorageResult.builder().bucketName(baseStorageDir.toString()).storageKey(storagePath).eTag(UUID.randomUUID().toString()).versionId("").build();
      } catch (Exception e) {
        throw new RuntimeException("Error writing file to local storage for captureID=" + captureId, e);
      }
    }));
    return Uni.createFrom().item(() -> {
      final Path targetPath = baseStorageDir.resolve(storagePath);
      managedExecutor.supplyAsync(() -> {
        try {
          Files.createDirectories(targetPath.getParent());
          try (OutputStream out = new FileOutputStream(targetPath.toFile())) {
            inputStream.transferTo(out);
          }
        } catch (Exception e) {
          throw new RuntimeException("Error consuming input stream for captureID=" + captureId, e);
        }
        return null;
      });
      Log.debug(String.format("Document saved locally for captureID=%s at %s", captureId, targetPath));
      return CloudStorageResult.builder().bucketName(baseStorageDir.toString()).storageKey(storagePath).eTag(UUID.randomUUID().toString()).versionId("").build();
    });
  }

  @Override
  public void userExtensionSchemaUpload(final String keyToSchema, final Path tempPathToSchema, final Map<String, String> tags, final long contentLength) {
    try {
      final Path targetPath = baseStorageDir.resolve(keyToSchema);
      Files.createDirectories(targetPath.getParent());
      Files.copy(tempPathToSchema, targetPath, StandardCopyOption.REPLACE_EXISTING);
      Log.infof("User extension schema uploaded to local path: %s", targetPath);
    } catch (Exception e) {
      throw new RuntimeException("Failed to store user schema locally", e);
    }
  }

  @Override
  public InputStream get(final String storagePath) {
    try {
      final Path targetPath = baseStorageDir.resolve(storagePath);
      return new FileInputStream(targetPath.toFile());
    } catch (Exception e) {
      throw new RuntimeException("Failed to read file from local storage: " + storagePath, e);
    }
  }

  @Override
  public Uni<CloudStorageResult> put(final String storagePath, final String contentType, final Optional<Long> contentLength, final Map<String, String> tags, final InputStream inputStream) {
    return documentUpload(storagePath, "unknown", contentType, contentLength, tags, inputStream);
  }

  @Override
  public Uni<Boolean> addTags(final String storagePath, final Map<String, String> tags) {
    Log.warn("LocalStorage does not support tag updates. Ignoring.");
    return Uni.createFrom().item(true);
  }

  private String getFileExtensionFromContentType(final String contentType) {
    if (contentType == null) return ".bin";
    return switch (contentType.toLowerCase()) {
      case "application/json" -> ".json";
      case "application/xml", "text/xml" -> ".xml";
      case "text/csv" -> ".csv";
      case "text/plain" -> ".txt";
      case "application/zip" -> ".zip";
      case "application/pdf" -> ".pdf";
      case "image/png" -> ".png";
      case "image/jpeg", "image/jpg" -> ".jpg";
      case "image/gif" -> ".gif";
      default -> ".bin";
    };
  }
}
