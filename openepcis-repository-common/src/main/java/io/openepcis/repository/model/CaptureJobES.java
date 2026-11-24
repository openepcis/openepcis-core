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
package io.openepcis.repository.model;

import io.openepcis.model.dto.CaptureJob;
import io.openepcis.model.dto.CaptureJobStatusMessage;
import io.openepcis.model.dto.InvalidEPCISEventInfo;
import io.openepcis.repository.Constants;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class CaptureJobES {
  private String captureID;
  private boolean running;
  private boolean success;
  private OffsetDateTime createdAt;
  private String captureErrorBehaviour;
  private OffsetDateTime finishedAt;
  protected List<InvalidEPCISEventInfo> errors = new ArrayList<>();
  private int validEventCount = 0;
  private int invalidEventCount = 0;
  private int processedEventCount = 0;
  private int capturedEventCount = 0;
  protected String storageBucket;
  protected String storageKey;
  private MetadataES metadata;
  private Long duration;

  public CaptureJobES(CaptureJob captureJob, Map<String, Object> metadata) {
    this.captureID = captureJob.getCaptureID();
    this.running = captureJob.isRunning();
    this.success = captureJob.isSuccess();
    this.createdAt = captureJob.getCreatedAt();
    this.captureErrorBehaviour = captureJob.getCaptureErrorBehaviour();
    this.finishedAt = captureJob.getFinishedAt();
    this.errors = captureJob.getErrors();
    if (captureJob instanceof CaptureJobStatusMessage statusMessage) {
      this.validEventCount = statusMessage.getValidEventCount();
      this.invalidEventCount = statusMessage.getInvalidEventCount();
      this.processedEventCount = statusMessage.getProcessedEventCount();
      this.capturedEventCount = statusMessage.getCapturedEventCount();
      this.storageBucket = statusMessage.getStorageBucket();
      this.storageKey = statusMessage.getStorageKey();
    }
    CapturedByES capturedByES = getCapturedByES(captureJob, metadata);
    this.setMetadata(MetadataES.builder().capturedBy(List.of(CapturedByES.builder().captureID(captureJob.getCaptureID()).timestamp(captureJob.getCreatedAt()).userID(capturedByES.getUserID()).defaultGroup(capturedByES.getDefaultGroup()).build())).build());
    // this method will populate the duration
    populateDuration();
  }

  private static CapturedByES getCapturedByES(CaptureJob captureJob, Map<String, Object> metadata) {
    return metadata.containsKey(Constants.CAPTURED_BY) && metadata.get(Constants.CAPTURED_BY) instanceof CapturedByES ? (CapturedByES) metadata.get(Constants.CAPTURED_BY) : CapturedByES.fromMetadata(metadata, captureJob.getCaptureID(), captureJob.getCreatedAt());
  }

  // Method to calculate duration between createdAt and finishedAt
  public void populateDuration() {
    this.duration = Objects.nonNull(createdAt) && Objects.nonNull(finishedAt) ? Duration.between(createdAt, finishedAt).toMillis() : null;
  }

  public String getCaptureID() {
    return this.captureID;
  }

  public boolean isRunning() {
    return this.running;
  }

  public boolean isSuccess() {
    return this.success;
  }

  public OffsetDateTime getCreatedAt() {
    return this.createdAt;
  }

  public String getCaptureErrorBehaviour() {
    return this.captureErrorBehaviour;
  }

  public OffsetDateTime getFinishedAt() {
    return this.finishedAt;
  }

  public List<InvalidEPCISEventInfo> getErrors() {
    return this.errors;
  }

  public int getValidEventCount() {
    return this.validEventCount;
  }

  public int getInvalidEventCount() {
    return this.invalidEventCount;
  }

  public int getProcessedEventCount() {
    return this.processedEventCount;
  }

  public int getCapturedEventCount() {
    return this.capturedEventCount;
  }

  public String getStorageBucket() {
    return this.storageBucket;
  }

  public String getStorageKey() {
    return this.storageKey;
  }

  public MetadataES getMetadata() {
    return this.metadata;
  }

  public Long getDuration() {
    return this.duration;
  }

  public void setCaptureID(String captureID) {
    this.captureID = captureID;
  }

  public void setRunning(boolean running) {
    this.running = running;
  }

  public void setSuccess(boolean success) {
    this.success = success;
  }

  public void setCreatedAt(OffsetDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public void setCaptureErrorBehaviour(String captureErrorBehaviour) {
    this.captureErrorBehaviour = captureErrorBehaviour;
  }

  public void setFinishedAt(OffsetDateTime finishedAt) {
    this.finishedAt = finishedAt;
  }

  public void setErrors(List<InvalidEPCISEventInfo> errors) {
    this.errors = errors;
  }

  public void setValidEventCount(int validEventCount) {
    this.validEventCount = validEventCount;
  }

  public void setInvalidEventCount(int invalidEventCount) {
    this.invalidEventCount = invalidEventCount;
  }

  public void setProcessedEventCount(int processedEventCount) {
    this.processedEventCount = processedEventCount;
  }

  public void setCapturedEventCount(int capturedEventCount) {
    this.capturedEventCount = capturedEventCount;
  }

  public void setStorageBucket(String storageBucket) {
    this.storageBucket = storageBucket;
  }

  public void setStorageKey(String storageKey) {
    this.storageKey = storageKey;
  }

  public void setMetadata(MetadataES metadata) {
    this.metadata = metadata;
  }

  public void setDuration(Long duration) {
    this.duration = duration;
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) return true;
    if (!(o instanceof CaptureJobES)) return false;
    CaptureJobES other = (CaptureJobES) o;
    if (!other.canEqual((Object) this)) return false;
    if (this.isRunning() != other.isRunning()) return false;
    if (this.isSuccess() != other.isSuccess()) return false;
    if (this.getValidEventCount() != other.getValidEventCount()) return false;
    if (this.getInvalidEventCount() != other.getInvalidEventCount()) return false;
    if (this.getProcessedEventCount() != other.getProcessedEventCount()) return false;
    if (this.getCapturedEventCount() != other.getCapturedEventCount()) return false;
    Object this$duration = this.getDuration();
    Object other$duration = other.getDuration();
    if (this$duration == null ? other$duration != null : !this$duration.equals(other$duration)) return false;
    Object this$captureID = this.getCaptureID();
    Object other$captureID = other.getCaptureID();
    if (this$captureID == null ? other$captureID != null : !this$captureID.equals(other$captureID)) return false;
    Object this$createdAt = this.getCreatedAt();
    Object other$createdAt = other.getCreatedAt();
    if (this$createdAt == null ? other$createdAt != null : !this$createdAt.equals(other$createdAt)) return false;
    Object this$captureErrorBehaviour = this.getCaptureErrorBehaviour();
    Object other$captureErrorBehaviour = other.getCaptureErrorBehaviour();
    if (this$captureErrorBehaviour == null ? other$captureErrorBehaviour != null : !this$captureErrorBehaviour.equals(other$captureErrorBehaviour)) return false;
    Object this$finishedAt = this.getFinishedAt();
    Object other$finishedAt = other.getFinishedAt();
    if (this$finishedAt == null ? other$finishedAt != null : !this$finishedAt.equals(other$finishedAt)) return false;
    Object this$errors = this.getErrors();
    Object other$errors = other.getErrors();
    if (this$errors == null ? other$errors != null : !this$errors.equals(other$errors)) return false;
    Object this$storageBucket = this.getStorageBucket();
    Object other$storageBucket = other.getStorageBucket();
    if (this$storageBucket == null ? other$storageBucket != null : !this$storageBucket.equals(other$storageBucket)) return false;
    Object this$storageKey = this.getStorageKey();
    Object other$storageKey = other.getStorageKey();
    if (this$storageKey == null ? other$storageKey != null : !this$storageKey.equals(other$storageKey)) return false;
    Object this$metadata = this.getMetadata();
    Object other$metadata = other.getMetadata();
    if (this$metadata == null ? other$metadata != null : !this$metadata.equals(other$metadata)) return false;
    return true;
  }

  protected boolean canEqual(Object other) {
    return other instanceof CaptureJobES;
  }

  @Override
  public int hashCode() {
    int PRIME = 59;
    int result = 1;
    result = result * PRIME + (this.isRunning() ? 79 : 97);
    result = result * PRIME + (this.isSuccess() ? 79 : 97);
    result = result * PRIME + this.getValidEventCount();
    result = result * PRIME + this.getInvalidEventCount();
    result = result * PRIME + this.getProcessedEventCount();
    result = result * PRIME + this.getCapturedEventCount();
    Object $duration = this.getDuration();
    result = result * PRIME + ($duration == null ? 43 : $duration.hashCode());
    Object $captureID = this.getCaptureID();
    result = result * PRIME + ($captureID == null ? 43 : $captureID.hashCode());
    Object $createdAt = this.getCreatedAt();
    result = result * PRIME + ($createdAt == null ? 43 : $createdAt.hashCode());
    Object $captureErrorBehaviour = this.getCaptureErrorBehaviour();
    result = result * PRIME + ($captureErrorBehaviour == null ? 43 : $captureErrorBehaviour.hashCode());
    Object $finishedAt = this.getFinishedAt();
    result = result * PRIME + ($finishedAt == null ? 43 : $finishedAt.hashCode());
    Object $errors = this.getErrors();
    result = result * PRIME + ($errors == null ? 43 : $errors.hashCode());
    Object $storageBucket = this.getStorageBucket();
    result = result * PRIME + ($storageBucket == null ? 43 : $storageBucket.hashCode());
    Object $storageKey = this.getStorageKey();
    result = result * PRIME + ($storageKey == null ? 43 : $storageKey.hashCode());
    Object $metadata = this.getMetadata();
    result = result * PRIME + ($metadata == null ? 43 : $metadata.hashCode());
    return result;
  }

  @Override
  public String toString() {
    return "CaptureJobES(captureID=" + this.getCaptureID() + ", running=" + this.isRunning() + ", success=" + this.isSuccess() + ", createdAt=" + this.getCreatedAt() + ", captureErrorBehaviour=" + this.getCaptureErrorBehaviour() + ", finishedAt=" + this.getFinishedAt() + ", errors=" + this.getErrors() + ", validEventCount=" + this.getValidEventCount() + ", invalidEventCount=" + this.getInvalidEventCount() + ", processedEventCount=" + this.getProcessedEventCount() + ", capturedEventCount=" + this.getCapturedEventCount() + ", storageBucket=" + this.getStorageBucket() + ", storageKey=" + this.getStorageKey() + ", metadata=" + this.getMetadata() + ", duration=" + this.getDuration() + ")";
  }

  public CaptureJobES(String captureID, boolean running, boolean success, OffsetDateTime createdAt, String captureErrorBehaviour, OffsetDateTime finishedAt, List<InvalidEPCISEventInfo> errors, int validEventCount, int invalidEventCount, int processedEventCount, int capturedEventCount, String storageBucket, String storageKey, MetadataES metadata, Long duration) {
    this.captureID = captureID;
    this.running = running;
    this.success = success;
    this.createdAt = createdAt;
    this.captureErrorBehaviour = captureErrorBehaviour;
    this.finishedAt = finishedAt;
    this.errors = errors;
    this.validEventCount = validEventCount;
    this.invalidEventCount = invalidEventCount;
    this.processedEventCount = processedEventCount;
    this.capturedEventCount = capturedEventCount;
    this.storageBucket = storageBucket;
    this.storageKey = storageKey;
    this.metadata = metadata;
    this.duration = duration;
  }

  public CaptureJobES() {
  }
}
