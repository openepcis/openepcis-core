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
import io.openepcis.capture.context.delegates.CaptureContextDelegation;
import io.openepcis.capture.context.message.DocumentCaptureMessage;
import io.openepcis.constants.EPCISFormat;
import io.openepcis.constants.EPCISVersion;
import io.openepcis.model.dto.CaptureJobStatusMessage;
import io.openepcis.reactive.publisher.ObjectNodePublisher;
import io.openepcis.service.context.EPCISContext;
import io.smallrye.mutiny.Uni;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public interface CaptureContext extends EPCISContext {
  void accept(ObjectNode node);

  void acceptErrorDeclarationEvent(ObjectNode node);

  boolean isProceed();

  void fail(Throwable throwable);

  void onComplete();

  Uni<DocumentCaptureMessage> toDocumentCapturedMessage(
      ObjectNode node, String traceId, String spanId);

  void updateFromCaptureJobStatusMessage(CaptureJobStatusMessage m);

  CaptureJobStatusMessage toCaptureJobStatusMessage();

  CaptureContextDelegation getDelegation();

  String getCaptureErrorBehaviour();

  java.util.Optional<Long> getContentLength();

  String getContentType();

  AtomicReference<ObjectNodePublisher<? extends ObjectNode>> getObjectNodePublisher();

  String getStorageBucket();

  String getStorageKey();

  String getStoragePathConfig();

  AtomicReference<List<String>> getEventIDs();

  AtomicReference<ObjectNode> getEpcisDocumentRef();

  AtomicReference<ConcurrentHashMap<Integer, ObjectNode>> getErrorCorrectionMap();

  AtomicInteger getEventCount();

  AtomicReference<List<Throwable>> getThrowableRef();

  AtomicReference<OffsetDateTime> getFinishedAt();

  String getCaptureID();

  OffsetDateTime getRecordTime();

  OffsetDateTime getCreatedAt();

  AtomicReference<ConcurrentHashMap<String, Object>> getMetadata();

  void setStorageBucket(String storageBucket);

  void setStorageKey(String storageKey);

  void setCaptureID(String captureID);

  void setRecordTime(OffsetDateTime recordTime);

  EPCISVersion getEpcisVersion();

  EPCISFormat getEpcisFormat();

  void setStoragePathConfig(String documentKeyPrefixDateFormat);

  String getDefaultGroup();

  // Method to indicate the successful document upload completion to StorageService
  void uploadDocumentComplete();

  // For setting the documentType via EpcisDocumentNodePersistenceDelegate during the capture
  // EPCISDocument/EPCISQueryDocument, etc.
  void setDocumentType(final String documentType);

  // For getting the documentType and adding as tags/metadata to storage service
  String getDocumentType();
}
