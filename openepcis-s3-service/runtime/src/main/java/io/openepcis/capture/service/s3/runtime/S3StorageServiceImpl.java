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
package io.openepcis.capture.service.s3.runtime;

import io.openepcis.capture.context.CaptureContext;
import io.openepcis.capture.context.CloudStorageResult;
import io.openepcis.capture.context.StorageService;
import io.openepcis.model.dto.CaptureJobStatusMessage;
import io.openepcis.s3.AmazonS3Service;
import io.openepcis.s3.UploadMetadata;
import io.openepcis.s3.UploadResult;
import io.openepcis.service.config.CloudStorageConfig;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.MutinyEmitter;
import io.smallrye.reactive.messaging.kafka.Record;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.eclipse.microprofile.context.ManagedExecutor;

class S3StorageServiceImpl implements StorageService {
  final CloudStorageConfig cloudStorageConfig;
  final AmazonS3Service amazonS3Service;
  final ManagedExecutor managedExecutor;
  final MutinyEmitter<Record<String, CaptureJobStatusMessage>> captureJobEmitter;

  S3StorageServiceImpl(CloudStorageConfig cloudStorageConfig, AmazonS3Service amazonS3Service, ManagedExecutor managedExecutor, MutinyEmitter<Record<String, CaptureJobStatusMessage>> captureJobEmitter) {
    this.cloudStorageConfig = cloudStorageConfig;
    this.amazonS3Service = amazonS3Service;
    this.managedExecutor = managedExecutor;
    this.captureJobEmitter = captureJobEmitter;
  }

  private Uni<Void> prepareCaptureContext(final CaptureContext context) {
    context.setStorageKey(getStoragePath(context, cloudStorageConfig));
    context.setStorageBucket(cloudStorageConfig.bucket());
    context.setStoragePathConfig(cloudStorageConfig.documentKeyPrefixDateFormat());
    return captureJobEmitter.send(Record.of(context.getCaptureID(), context.toCaptureJobStatusMessage()));
  }

  @Override
  public Uni<CaptureContext> documentUpload(final CaptureContext context, final InputStream inputStream) {
    return prepareCaptureContext(context).chain(x -> {
      putAsync(getStoragePath(context, cloudStorageConfig), context.getContentType(), context.getContentLength(), createCaptureTags(context), inputStream).runSubscriptionOn(managedExecutor).subscribe().with(uploadResult -> {
        Log.debug(" ✅ Document successfully uploaded to S3 path : " + uploadResult.getKey());
        // After the successful document upload completion trigger the method
        // uploadDocumentComplete to add documentType
        context.uploadDocumentComplete();
      }, throwable -> {
        Log.error(" ❌ Document upload failed to S3 path : " + throwable.getMessage());
        context.fail(throwable);
      });
      return Uni.createFrom().item(context);
    });
  }

  @Override
  public void userExtensionSchemaUpload(String keyToSchema, Path tempPathToSchema, Map<String, String> tags, long contentLength) {
    try {
      amazonS3Service.putAsync(keyToSchema, Files.newInputStream(tempPathToSchema), UploadMetadata.builder().contentLength(contentLength).tags(tags).build()).subscribe().with(item -> Log.info(String.format("Schema uploaded in cloud storage with key: %s", keyToSchema)));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  // Get the contents from the existing s3 path
  @Override
  public InputStream get(final String storagePath) {
    return amazonS3Service.get(storagePath);
  }

  // Update the existing s3 path with new contents or tags
  @Override
  public Uni<CloudStorageResult> put(final String storagePath, final String contentType, final Optional<Long> contentLength, final Map<String, String> tags, final InputStream inputStream) {
    return 
    // Handle success during modification of the existing storagePath.
    // Build and return the CloudStorageResult from the returned blobUrl.
    putAsync(storagePath, contentType, contentLength, tags, inputStream).runSubscriptionOn(managedExecutor).onItem().transform(uploadResult -> {
      Log.info(String.format("Document/Evebt successfully updated in S3 for S3 key = %s", storagePath));
      return CloudStorageResult.builder().bucketName(uploadResult.getBucketName()).storageKey(uploadResult.getKey()).eTag("").versionId("").build();
    }).onFailure().recoverWithUni(throwable -> {
      // Handle any error encountered during upload.
      Log.error(String.format("Unable to update document in S3. key = %s", storagePath), throwable);
      return Uni.createFrom().failure(new RuntimeException("Update failed", throwable));
    });
  }

  @Override
  public Uni<Boolean> addTags(final String storageKey, final Map<String, String> tags) {
    return Uni.createFrom().item(amazonS3Service.addTags(storageKey, tags));
  }

  @Override
  public Uni<CloudStorageResult> documentUpload(final String storagePath, final String captureId, final String contentType, final Optional<Long> contentLength, final Map<String, String> tags, final InputStream inputStream) {
    return putAsync(storagePath, contentType, contentLength, tags, inputStream).runSubscriptionOn(managedExecutor).onItem().transform(uploadResult -> {
      Log.info(String.format("Document/Event successfully saved to S3. S3Path=%s", storagePath));
      return CloudStorageResult.builder().bucketName(uploadResult.getBucketName()).storageKey(uploadResult.getKey()).eTag("").versionId("").build();
    }).onFailure().recoverWithUni(throwable -> {
      Log.error(String.format("Failed to upload Document/Event to S3. captureID=%s, S3 Path:%s", captureId, storagePath), throwable);
      return Uni.createFrom().failure(new RuntimeException("Upload failed", throwable));
    });
  }

  private Uni<UploadResult> putAsync(final String storagePath, final String contentType, final Optional<Long> contentLength, final Map<String, String> tags, final InputStream inputStream) {
    return Uni.createFrom().emitter(emitter -> {
      final UploadMetadata uploadMetadata = (contentLength.isPresent()) ? UploadMetadata.builder().contentType(contentType).contentLength(contentLength.get()).tags(cleanedUpTags(tags)).build() : UploadMetadata.builder().contentType(contentType).build();
      amazonS3Service.putAsync(storagePath, inputStream, uploadMetadata).runSubscriptionOn(managedExecutor).subscribe().with(response -> {
        Log.debug("Document upload completed to S3 path : " + storagePath);
        emitter.complete(response);
      }, throwable -> {
        Log.error(" Document upload failed to S3 path : " + storagePath + throwable.getMessage());
        emitter.fail(throwable);
      });
    }).map(result -> (UploadResult) result).onFailure().retry().withBackOff(Duration.ofMillis(250)).withJitter(0.2).atMost(1).onFailure().recoverWithUni(throwable -> {
      Log.error("Exception occurred during document upload to S3 for key=" + storagePath, throwable);
      return Uni.createFrom().failure(new RuntimeException("Upload of Document/Event failed to S3 for key : " + storagePath, throwable));
    });
  }

  // Utility method to clean up the tags based on the additional method written in AmazonS3Service
  // before uploading the Document to S3
  private Map<String, String> cleanedUpTags(final Map<String, String> tags) {
    return AmazonS3Service.cleanupTagSet(tags).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }
}
