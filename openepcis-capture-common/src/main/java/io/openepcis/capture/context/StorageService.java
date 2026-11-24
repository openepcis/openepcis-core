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
package io.openepcis.capture.context;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.utils.StringUtils;
import io.openepcis.constants.EPCISFormat;
import io.openepcis.model.epcis.exception.CaptureValidationException;
import io.openepcis.reactive.publisher.ObjectNodePublisher;
import io.openepcis.repository.model.CapturedByES;
import io.openepcis.service.config.CloudStorageConfig;
import io.openepcis.service.util.Constants;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.unchecked.Unchecked;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.eclipse.microprofile.context.ManagedExecutor;

public interface StorageService {

  Pattern VALID_TAG_VALUE_PATTERN = Pattern.compile("^([\\p{L}\\p{Z}\\p{N}_.:/=+\\-]*)$");

  default String getStoragePath(
      CaptureContext captureContext, CloudStorageConfig cloudStorageConfig) {
    final String pattern =
        StringUtils.isNotBlank(cloudStorageConfig.documentKeyPrefixDateFormat())
            ? cloudStorageConfig.documentKeyPrefixDateFormat()
            : "'capture-document/'yyyy/MM/";
    OffsetDateTime currentDateTime = OffsetDateTime.now();
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
    String formattedDateTime = currentDateTime.format(formatter);
    String[] directoryParts = formattedDateTime.split("/");
    StringBuilder path = new StringBuilder();
    for (String directoryPart : directoryParts) {
      path.append(directoryPart).append("/");
    }
    path.append(captureContext.getCaptureID());
    return captureContext.getEpcisFormat().equals(EPCISFormat.JSON_LD)
        ? path.append(".json").toString()
        : path.append(".xml").toString();
  }

  /**
   * Generates a cloud storage path for an event based on the provided context. The path follows the
   * format: [date-time]/capture-id/event-index.[json/xml]
   */
  default String getEventStoragePath(
      final CaptureContext captureContext,
      final CloudStorageConfig cloudStorageConfig,
      final Integer eventIndex) {
    final String pattern =
        Optional.ofNullable(cloudStorageConfig.eventKeyPrefixDateFormat())
            .orElse("'capture-event/'yyyy/MM/");
    final String formattedDateTime =
        OffsetDateTime.now().format(DateTimeFormatter.ofPattern(pattern));
    final String[] directoryParts = formattedDateTime.split("/");
    final StringBuilder path = new StringBuilder();

    for (final String directoryPart : directoryParts) {
      path.append(directoryPart).append("/");
    }
    path.append(captureContext.getCaptureID()).append("/").append(eventIndex);

    return captureContext.getEpcisFormat().equals(EPCISFormat.JSON_LD)
        ? path.append(".json").toString()
        : path.append(".xml").toString();
  }

  default Map<String, String> createCaptureTags(CaptureContext captureContext) {
    final Map<String, String> tags = new HashMap<>();
    tags.put(Constants.CREATED_AT, captureContext.getCreatedAt().toString());
    tags.put(Constants.EPCIS_VERSION, captureContext.getEpcisVersion().toString());
    tags.put(Constants.EPCIS_FORMAT, captureContext.getEpcisFormat().toString());
    tags.put(Constants.CAPTURE_ID, captureContext.getCaptureID());
    tags.put(Constants.CAPTURE_ERROR_BEHAVIOUR, captureContext.getCaptureErrorBehaviour());
    final String capturedBy =
        ((CapturedByES) captureContext.getMetadata().get().get(Constants.CAPTURED_BY)).getUserID();
    tags.put(Constants.DEFAULT_GROUP, captureContext.getDefaultGroup());
    if (VALID_TAG_VALUE_PATTERN.matcher(capturedBy).matches()) {
      tags.put(Constants.CAPTURED_BY, capturedBy);
    } else {
      tags.put(
          Constants.CAPTURED_BY,
          Base64.getEncoder().encodeToString(capturedBy.getBytes(StandardCharsets.UTF_8)));
    }

    final Optional<String> gs1Extensions = captureContext.getGs1Extensions().get();
    gs1Extensions.ifPresent(
        s ->
            tags.put(
                Constants.GS1_EXTENSIONS,
                Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8))));
    return tags;
  }

  default void publishToContext(
      final ObjectNodePublisher<ObjectNode> publisher,
      final CaptureContext context,
      ManagedExecutor managedExecutor) {
    Multi.createFrom()
        .publisher(publisher)
        .runSubscriptionOn(managedExecutor)
        .subscribe()
        .with(
            Unchecked.consumer(
                objectNode -> {
                  if (objectNode.has(Constants.TYPE)) {
                    context.accept(objectNode);
                  } else {
                    throw new CaptureValidationException(
                        "attribute " + Constants.TYPE + " not found");
                  }
                }),
            context::fail,
            context::onComplete);
  }

  Uni<CaptureContext> documentUpload(
      final CaptureContext context, final InputStream inputStream); // Add tags as well

  Uni<CloudStorageResult> documentUpload(
      final String storagePath,
      final String captureId,
      final String contentType,
      final Optional<Long> contentLength,
      final Map<String, String> tags,
      final InputStream inputStream);

  void userExtensionSchemaUpload(
      String keyToSchema, Path tempPathToSchema, Map<String, String> tags, long contentLength);

  // Method to get contents from existing path
  InputStream get(final String storagePath);

  // Asynchronous PUT Method to update the existing path with new contents
  Uni<CloudStorageResult> put(
      final String storagePath,
      final String contentType,
      final Optional<Long> contentLength,
      final Map<String, String> tags,
      final InputStream inputStream);

  // Method to add additional tags to the existing storage location
  Uni<Boolean> addTags(final String storageKey, final Map<String, String> tags);
}
