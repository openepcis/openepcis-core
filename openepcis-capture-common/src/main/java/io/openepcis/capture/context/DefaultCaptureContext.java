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
import io.openepcis.capture.context.delegates.EventCountEmitter;
import io.openepcis.capture.context.delegates.ExceptionDelegate;
import io.openepcis.capture.context.message.DocumentCaptureMessage;
import io.openepcis.constants.EPCIS;
import io.openepcis.constants.EPCISVersion;
import io.openepcis.model.dto.CaptureJobStatusMessage;
import io.openepcis.model.epcis.constants.CommonConstants;
import io.openepcis.model.rest.ProblemResponseBodyForCapture;
import io.openepcis.reactive.publisher.ObjectNodePublisher;
import io.openepcis.service.config.CloudStorageConfig;
import io.openepcis.service.context.DefaultEPCISContext;
import io.opentelemetry.api.trace.Span;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

public class DefaultCaptureContext extends DefaultEPCISContext implements Consumer<ObjectNode>, CaptureContext {
  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DefaultCaptureContext.class);
  private final CaptureContextDelegation delegation;
  private final EventCountEmitter eventCountEmitter;
  private final String captureErrorBehaviour;
  private final Optional<Long> contentLength;
  private final AtomicReference<ObjectNodePublisher<? extends ObjectNode>> objectNodePublisher;
  private String storageBucket;
  private String storageKey;
  private String storagePathConfig;
  private AtomicReference<List<String>> eventIDs = new AtomicReference<>(new ArrayList<>());
  private final AtomicReference<ObjectNode> epcisDocumentRef = new AtomicReference<>(null);
  private final AtomicReference<ConcurrentHashMap<Integer, ObjectNode>> errorCorrectionMap = new AtomicReference<>(new ConcurrentHashMap<>());
  private final AtomicInteger eventCount = new AtomicInteger(0);
  private final AtomicReference<List<Throwable>> throwableRef = new AtomicReference<>(new ArrayList<>());
  private final AtomicReference<OffsetDateTime> finishedAt = new AtomicReference(null);
  private String captureID = UUID.randomUUID().toString();
  private OffsetDateTime recordTime = OffsetDateTime.now();
  private OffsetDateTime createdAt = OffsetDateTime.now();
  private final AtomicReference<ConcurrentHashMap<String, Object>> metadata = new AtomicReference<>(new ConcurrentHashMap<>());
  private final ExceptionDelegate exceptionDelegate;
  private final String defaultGroup;
  private final CloudStorageConfig cloudStorageConfig;
  private final StorageService storageService;
  private String documentType;

  public DefaultCaptureContext(final String contentType, final EPCISVersion version, final CaptureContextDelegation delegation, final EventCountEmitter eventCountEmitter, final String captureErrorBehaviour, final Optional<Long> contentLength, final AtomicReference<ObjectNodePublisher<? extends ObjectNode>> objectNodePublisher, final ExceptionDelegate exceptionDelegate, final String defaultGroup, final CloudStorageConfig cloudStorageConfig, final StorageService storageService) {
    super(contentType, version);
    this.delegation = delegation;
    this.eventCountEmitter = eventCountEmitter;
    this.captureErrorBehaviour = captureErrorBehaviour;
    this.contentLength = contentLength;
    this.objectNodePublisher = objectNodePublisher;
    this.exceptionDelegate = exceptionDelegate;
    this.defaultGroup = defaultGroup;
    this.cloudStorageConfig = cloudStorageConfig;
    this.storageService = storageService;
  }

  @Override
  public void accept(ObjectNode node) {
    delegation.findDelegate(this, node).accept(this, node);
  }

  @Override
  public void acceptErrorDeclarationEvent(ObjectNode node) {
    delegation.getEventNodeMessageDelegate().accept(this, node);
  }

  @Override
  public boolean isProceed() {
    return "PROCEED".equalsIgnoreCase(captureErrorBehaviour);
  }

  @Override
  public void fail(Throwable throwable) {
    throwableRef.get().add(throwable);
    exceptionDelegate.accept(this);
  }

  @Override
  public void onComplete() {
    EventCountMessage eventCountMessage = new EventCountMessage(getEventCount().get());
    eventCountMessage.setTraceId(Span.current().getSpanContext().getTraceId());
    eventCountMessage.setSpanId(Span.current().getSpanContext().getSpanId());
    eventCountMessage.setDefaultGroup(defaultGroup);
    eventCountEmitter.send(getCaptureID(), eventCountMessage);
    ConcurrentHashMap<Integer, ObjectNode> errorDeclarationEvents = getErrorCorrectionMap().get();
    if (CollectionUtils.isNotEmpty(errorDeclarationEvents.values())) {
      errorDeclarationEvents.forEach((eventCount, event) -> acceptErrorDeclarationEvent(event));
    } else {
      finishedAt.set(OffsetDateTime.now());
    }
  }

  /**
   * Converts an EPCIS event node to a DocumentCaptureMessage, handling both regular events and
   * large events (>4KB) separately. Large events are stored in StorageService S3/Azure Blob while
   * small event as String on objectNodeString.
   *
   * @param node Node containing the EPCIS event data
   * @param traceId Trace identifier for distributed tracing
   * @param spanId Span identifier for distributed tracing
   * @return DocumentCaptureMessage containing processed event data
   */
  @Override
  public Uni<DocumentCaptureMessage> toDocumentCapturedMessage(final ObjectNode node, final String traceId, final String spanId) {
    // Prepare common message attributes
    final String captureId = getCaptureID();
    final int eventIndex = getEventCount().get();
    final String documentWrappedEPCISEvent = EPCISEventObjectNodeUtil.wrapEventIntoEPCISDocument(node, getEpcisDocumentRef().get().deepCopy()).toString();
    // Check if each event in EPCISDocument exceeds size threshold of 4kb in UTF-8 encoding
    final byte[] eventBytes = documentWrappedEPCISEvent.getBytes(StandardCharsets.UTF_8);
    final int eventContentLength = eventBytes.length;
    // Process large events (>4KB) via S3/Azure Blob upload
    if (eventContentLength > 4096) {
      // Generate storage key for potential S3/Blob upload
      final String eventStorageKey = storageService.getEventStoragePath(this, cloudStorageConfig, eventIndex);
      final Map<String, String> eventStorageTags = storageService.createCaptureTags(this);
      eventStorageTags.put(EPCIS.EVENT_TYPE, node.get(EPCIS.TYPE).asText());
      eventStorageTags.put("Content_Type", getContentType());
      eventStorageTags.put("Event_Index", String.valueOf(eventIndex));
      Log.debug(String.format("Event size is above >4kb : %s so upload to StorageService and access from there, key : %s", eventContentLength, eventStorageKey));
      final DocumentCaptureMessage documentCaptureMessage = new DocumentCaptureMessage(captureId, createdAt, finishedAt.get(), getFinishedAt().get() == null, getThrowableRef().get().isEmpty(), getCaptureErrorBehaviour(), OffsetDateTime.now(), eventIndex, getStorageBucket(), getStorageKey(), cloudStorageConfig.bucket(), eventStorageKey, eventStorageTags, getMetadata().get(), getEventIDs().get(), new ArrayList<>());
      documentCaptureMessage.setTraceId(traceId);
      documentCaptureMessage.setSpanId(spanId);
      documentCaptureMessage.setDefaultGroup(defaultGroup);
      // Start the asynchronous upload and after the upload return the Uni<DocumentCaptureMessage>
      // with eventStorageKey for further processing.
      return storageService.documentUpload(eventStorageKey, captureId, getContentType(), Optional.of((long) eventContentLength), eventStorageTags, new ByteArrayInputStream(eventBytes)).invoke(result -> Log.debug(String.format("Event >4kb successfully uploaded to StorageService path : %s", eventStorageKey))).onFailure().recoverWithUni(failure -> {
        Log.error(String.format("Event >4kb failed to upload to StorageService path : %s", eventStorageKey));
        return Uni.createFrom().failure(new RuntimeException("Update failed", failure));
      }).replaceWith(documentCaptureMessage);
    }
    // Process regular-sized events (<4KB) with EPCIS document wrapping
    Log.debug("Event size is below <4kb so use directly as objectNodeString String in DocumentCaptureMessage.");
    final DocumentCaptureMessage documentCaptureMessage = new DocumentCaptureMessage(captureId, createdAt, finishedAt.get(), getFinishedAt().get() == null, getThrowableRef().get().isEmpty(), getCaptureErrorBehaviour(), OffsetDateTime.now(), documentWrappedEPCISEvent, eventIndex, getStorageBucket(), getStorageKey(), getMetadata().get(), getEventIDs().get(), new ArrayList<>());
    documentCaptureMessage.setTraceId(traceId);
    documentCaptureMessage.setSpanId(spanId);
    documentCaptureMessage.setDefaultGroup(defaultGroup);
    return Uni.createFrom().item(documentCaptureMessage);
  }

  @Override
  public void updateFromCaptureJobStatusMessage(CaptureJobStatusMessage m) {
    // TODO improve - might need some additions
    if (m.getFinishedAt() != null) {
      getFinishedAt().set(m.getFinishedAt());
    }
  }

  @Override
  public CaptureJobStatusMessage toCaptureJobStatusMessage() {
    final CaptureJobStatusMessage dto = new CaptureJobStatusMessage(getCaptureID(), getFinishedAt().get() == null, getThrowableRef().get().isEmpty(), getRecordTime(), getCaptureErrorBehaviour(), getFinishedAt().get(), getStorageBucket(), getStorageKey());
    if (!getThrowableRef().get().isEmpty()) {
      final List<ProblemResponseBodyForCapture> errors = new ArrayList<>();
      getThrowableRef().get().forEach(t -> errors.add(new ProblemResponseBodyForCapture(t.getClass().getSimpleName(), "Capture", -1, t.getMessage(), List.of(1))));
    }
    dto.setCapturedEventCount(getEventCount().get());
    return dto;
  }

  // Method to be triggered after the successful completion of document upload to storageService
  @Override
  public void uploadDocumentComplete() {
    // After the completion of document upload to storage service add the DocumentType tag to it
    // EPCISDocument, QueryDocument, etc.
    if (StringUtils.isNotBlank(getDocumentType())) {
      Log.debug("Add the tag/metadata to StorageService indicating the captured documentType");
      storageService.addTags(getStorageKey(), Map.of(CommonConstants.DOCUMENT_TYPE, getDocumentType())).subscribe().with(success -> {
        if (Boolean.TRUE.equals(success)) {
          Log.debug("Addition of tag to indicate documentType has been added successfully.");
        } else {
          Log.error("Addition of tag to indicate documentType has failed.");
        }
      }, failure -> Log.error("Addition of tag to indicate documentType has failed."));
    }
  }

  public CaptureContextDelegation getDelegation() {
    return this.delegation;
  }

  public EventCountEmitter getEventCountEmitter() {
    return this.eventCountEmitter;
  }

  public String getCaptureErrorBehaviour() {
    return this.captureErrorBehaviour;
  }

  public Optional<Long> getContentLength() {
    return this.contentLength;
  }

  public AtomicReference<ObjectNodePublisher<? extends ObjectNode>> getObjectNodePublisher() {
    return this.objectNodePublisher;
  }

  public String getStorageBucket() {
    return this.storageBucket;
  }

  public String getStorageKey() {
    return this.storageKey;
  }

  public String getStoragePathConfig() {
    return this.storagePathConfig;
  }

  public AtomicReference<List<String>> getEventIDs() {
    return this.eventIDs;
  }

  public AtomicReference<ObjectNode> getEpcisDocumentRef() {
    return this.epcisDocumentRef;
  }

  public AtomicReference<ConcurrentHashMap<Integer, ObjectNode>> getErrorCorrectionMap() {
    return this.errorCorrectionMap;
  }

  public AtomicInteger getEventCount() {
    return this.eventCount;
  }

  public AtomicReference<List<Throwable>> getThrowableRef() {
    return this.throwableRef;
  }

  public AtomicReference<OffsetDateTime> getFinishedAt() {
    return this.finishedAt;
  }

  public String getCaptureID() {
    return this.captureID;
  }

  public OffsetDateTime getRecordTime() {
    return this.recordTime;
  }

  public OffsetDateTime getCreatedAt() {
    return this.createdAt;
  }

  public AtomicReference<ConcurrentHashMap<String, Object>> getMetadata() {
    return this.metadata;
  }

  public ExceptionDelegate getExceptionDelegate() {
    return this.exceptionDelegate;
  }

  public String getDefaultGroup() {
    return this.defaultGroup;
  }

  public CloudStorageConfig getCloudStorageConfig() {
    return this.cloudStorageConfig;
  }

  public StorageService getStorageService() {
    return this.storageService;
  }

  public String getDocumentType() {
    return this.documentType;
  }

  public void setStorageBucket(String storageBucket) {
    this.storageBucket = storageBucket;
  }

  public void setStorageKey(String storageKey) {
    this.storageKey = storageKey;
  }

  public void setStoragePathConfig(String storagePathConfig) {
    this.storagePathConfig = storagePathConfig;
  }

  public void setEventIDs(AtomicReference<List<String>> eventIDs) {
    this.eventIDs = eventIDs;
  }

  public void setCaptureID(String captureID) {
    this.captureID = captureID;
  }

  public void setRecordTime(OffsetDateTime recordTime) {
    this.recordTime = recordTime;
  }

  public void setCreatedAt(OffsetDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public void setDocumentType(String documentType) {
    this.documentType = documentType;
  }

  @Override
  public String toString() {
    return "DefaultCaptureContext(delegation=" + this.getDelegation() + ", eventCountEmitter=" + this.getEventCountEmitter() + ", captureErrorBehaviour=" + this.getCaptureErrorBehaviour() + ", contentLength=" + this.getContentLength() + ", objectNodePublisher=" + this.getObjectNodePublisher() + ", storageBucket=" + this.getStorageBucket() + ", storageKey=" + this.getStorageKey() + ", storagePathConfig=" + this.getStoragePathConfig() + ", eventIDs=" + this.getEventIDs() + ", epcisDocumentRef=" + this.getEpcisDocumentRef() + ", errorCorrectionMap=" + this.getErrorCorrectionMap() + ", eventCount=" + this.getEventCount() + ", throwableRef=" + this.getThrowableRef() + ", finishedAt=" + this.getFinishedAt() + ", captureID=" + this.getCaptureID() + ", recordTime=" + this.getRecordTime() + ", createdAt=" + this.getCreatedAt() + ", metadata=" + this.getMetadata() + ", exceptionDelegate=" + this.getExceptionDelegate() + ", defaultGroup=" + this.getDefaultGroup() + ", cloudStorageConfig=" + this.getCloudStorageConfig() + ", storageService=" + this.getStorageService() + ", documentType=" + this.getDocumentType() + ")";
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) return true;
    if (!(o instanceof DefaultCaptureContext)) return false;
    DefaultCaptureContext other = (DefaultCaptureContext) o;
    if (!other.canEqual((Object) this)) return false;
    if (!super.equals(o)) return false;
    Object this$delegation = this.getDelegation();
    Object other$delegation = other.getDelegation();
    if (this$delegation == null ? other$delegation != null : !this$delegation.equals(other$delegation)) return false;
    Object this$eventCountEmitter = this.getEventCountEmitter();
    Object other$eventCountEmitter = other.getEventCountEmitter();
    if (this$eventCountEmitter == null ? other$eventCountEmitter != null : !this$eventCountEmitter.equals(other$eventCountEmitter)) return false;
    Object this$captureErrorBehaviour = this.getCaptureErrorBehaviour();
    Object other$captureErrorBehaviour = other.getCaptureErrorBehaviour();
    if (this$captureErrorBehaviour == null ? other$captureErrorBehaviour != null : !this$captureErrorBehaviour.equals(other$captureErrorBehaviour)) return false;
    Object this$contentLength = this.getContentLength();
    Object other$contentLength = other.getContentLength();
    if (this$contentLength == null ? other$contentLength != null : !this$contentLength.equals(other$contentLength)) return false;
    Object this$objectNodePublisher = this.getObjectNodePublisher();
    Object other$objectNodePublisher = other.getObjectNodePublisher();
    if (this$objectNodePublisher == null ? other$objectNodePublisher != null : !this$objectNodePublisher.equals(other$objectNodePublisher)) return false;
    Object this$storageBucket = this.getStorageBucket();
    Object other$storageBucket = other.getStorageBucket();
    if (this$storageBucket == null ? other$storageBucket != null : !this$storageBucket.equals(other$storageBucket)) return false;
    Object this$storageKey = this.getStorageKey();
    Object other$storageKey = other.getStorageKey();
    if (this$storageKey == null ? other$storageKey != null : !this$storageKey.equals(other$storageKey)) return false;
    Object this$storagePathConfig = this.getStoragePathConfig();
    Object other$storagePathConfig = other.getStoragePathConfig();
    if (this$storagePathConfig == null ? other$storagePathConfig != null : !this$storagePathConfig.equals(other$storagePathConfig)) return false;
    Object this$eventIDs = this.getEventIDs();
    Object other$eventIDs = other.getEventIDs();
    if (this$eventIDs == null ? other$eventIDs != null : !this$eventIDs.equals(other$eventIDs)) return false;
    Object this$epcisDocumentRef = this.getEpcisDocumentRef();
    Object other$epcisDocumentRef = other.getEpcisDocumentRef();
    if (this$epcisDocumentRef == null ? other$epcisDocumentRef != null : !this$epcisDocumentRef.equals(other$epcisDocumentRef)) return false;
    Object this$errorCorrectionMap = this.getErrorCorrectionMap();
    Object other$errorCorrectionMap = other.getErrorCorrectionMap();
    if (this$errorCorrectionMap == null ? other$errorCorrectionMap != null : !this$errorCorrectionMap.equals(other$errorCorrectionMap)) return false;
    Object this$eventCount = this.getEventCount();
    Object other$eventCount = other.getEventCount();
    if (this$eventCount == null ? other$eventCount != null : !this$eventCount.equals(other$eventCount)) return false;
    Object this$throwableRef = this.getThrowableRef();
    Object other$throwableRef = other.getThrowableRef();
    if (this$throwableRef == null ? other$throwableRef != null : !this$throwableRef.equals(other$throwableRef)) return false;
    Object this$finishedAt = this.getFinishedAt();
    Object other$finishedAt = other.getFinishedAt();
    if (this$finishedAt == null ? other$finishedAt != null : !this$finishedAt.equals(other$finishedAt)) return false;
    Object this$captureID = this.getCaptureID();
    Object other$captureID = other.getCaptureID();
    if (this$captureID == null ? other$captureID != null : !this$captureID.equals(other$captureID)) return false;
    Object this$recordTime = this.getRecordTime();
    Object other$recordTime = other.getRecordTime();
    if (this$recordTime == null ? other$recordTime != null : !this$recordTime.equals(other$recordTime)) return false;
    Object this$createdAt = this.getCreatedAt();
    Object other$createdAt = other.getCreatedAt();
    if (this$createdAt == null ? other$createdAt != null : !this$createdAt.equals(other$createdAt)) return false;
    Object this$metadata = this.getMetadata();
    Object other$metadata = other.getMetadata();
    if (this$metadata == null ? other$metadata != null : !this$metadata.equals(other$metadata)) return false;
    Object this$exceptionDelegate = this.getExceptionDelegate();
    Object other$exceptionDelegate = other.getExceptionDelegate();
    if (this$exceptionDelegate == null ? other$exceptionDelegate != null : !this$exceptionDelegate.equals(other$exceptionDelegate)) return false;
    Object this$defaultGroup = this.getDefaultGroup();
    Object other$defaultGroup = other.getDefaultGroup();
    if (this$defaultGroup == null ? other$defaultGroup != null : !this$defaultGroup.equals(other$defaultGroup)) return false;
    Object this$cloudStorageConfig = this.getCloudStorageConfig();
    Object other$cloudStorageConfig = other.getCloudStorageConfig();
    if (this$cloudStorageConfig == null ? other$cloudStorageConfig != null : !this$cloudStorageConfig.equals(other$cloudStorageConfig)) return false;
    Object this$storageService = this.getStorageService();
    Object other$storageService = other.getStorageService();
    if (this$storageService == null ? other$storageService != null : !this$storageService.equals(other$storageService)) return false;
    Object this$documentType = this.getDocumentType();
    Object other$documentType = other.getDocumentType();
    if (this$documentType == null ? other$documentType != null : !this$documentType.equals(other$documentType)) return false;
    return true;
  }

  protected boolean canEqual(Object other) {
    return other instanceof DefaultCaptureContext;
  }

  @Override
  public int hashCode() {
    int PRIME = 59;
    int result = super.hashCode();
    Object $delegation = this.getDelegation();
    result = result * PRIME + ($delegation == null ? 43 : $delegation.hashCode());
    Object $eventCountEmitter = this.getEventCountEmitter();
    result = result * PRIME + ($eventCountEmitter == null ? 43 : $eventCountEmitter.hashCode());
    Object $captureErrorBehaviour = this.getCaptureErrorBehaviour();
    result = result * PRIME + ($captureErrorBehaviour == null ? 43 : $captureErrorBehaviour.hashCode());
    Object $contentLength = this.getContentLength();
    result = result * PRIME + ($contentLength == null ? 43 : $contentLength.hashCode());
    Object $objectNodePublisher = this.getObjectNodePublisher();
    result = result * PRIME + ($objectNodePublisher == null ? 43 : $objectNodePublisher.hashCode());
    Object $storageBucket = this.getStorageBucket();
    result = result * PRIME + ($storageBucket == null ? 43 : $storageBucket.hashCode());
    Object $storageKey = this.getStorageKey();
    result = result * PRIME + ($storageKey == null ? 43 : $storageKey.hashCode());
    Object $storagePathConfig = this.getStoragePathConfig();
    result = result * PRIME + ($storagePathConfig == null ? 43 : $storagePathConfig.hashCode());
    Object $eventIDs = this.getEventIDs();
    result = result * PRIME + ($eventIDs == null ? 43 : $eventIDs.hashCode());
    Object $epcisDocumentRef = this.getEpcisDocumentRef();
    result = result * PRIME + ($epcisDocumentRef == null ? 43 : $epcisDocumentRef.hashCode());
    Object $errorCorrectionMap = this.getErrorCorrectionMap();
    result = result * PRIME + ($errorCorrectionMap == null ? 43 : $errorCorrectionMap.hashCode());
    Object $eventCount = this.getEventCount();
    result = result * PRIME + ($eventCount == null ? 43 : $eventCount.hashCode());
    Object $throwableRef = this.getThrowableRef();
    result = result * PRIME + ($throwableRef == null ? 43 : $throwableRef.hashCode());
    Object $finishedAt = this.getFinishedAt();
    result = result * PRIME + ($finishedAt == null ? 43 : $finishedAt.hashCode());
    Object $captureID = this.getCaptureID();
    result = result * PRIME + ($captureID == null ? 43 : $captureID.hashCode());
    Object $recordTime = this.getRecordTime();
    result = result * PRIME + ($recordTime == null ? 43 : $recordTime.hashCode());
    Object $createdAt = this.getCreatedAt();
    result = result * PRIME + ($createdAt == null ? 43 : $createdAt.hashCode());
    Object $metadata = this.getMetadata();
    result = result * PRIME + ($metadata == null ? 43 : $metadata.hashCode());
    Object $exceptionDelegate = this.getExceptionDelegate();
    result = result * PRIME + ($exceptionDelegate == null ? 43 : $exceptionDelegate.hashCode());
    Object $defaultGroup = this.getDefaultGroup();
    result = result * PRIME + ($defaultGroup == null ? 43 : $defaultGroup.hashCode());
    Object $cloudStorageConfig = this.getCloudStorageConfig();
    result = result * PRIME + ($cloudStorageConfig == null ? 43 : $cloudStorageConfig.hashCode());
    Object $storageService = this.getStorageService();
    result = result * PRIME + ($storageService == null ? 43 : $storageService.hashCode());
    Object $documentType = this.getDocumentType();
    result = result * PRIME + ($documentType == null ? 43 : $documentType.hashCode());
    return result;
  }
}
