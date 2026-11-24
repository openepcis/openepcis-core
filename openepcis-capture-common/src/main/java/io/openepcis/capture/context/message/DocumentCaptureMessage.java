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
package io.openepcis.capture.context.message;

import io.openepcis.model.dto.CaptureData;
import io.openepcis.model.dto.InvalidEPCISEventInfo;
import io.quarkus.runtime.annotations.RegisterForReflection;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * DocumentCaptureMessage extends {@link CaptureData} and encapsulates detailed information about a
 * captured EPCIS document. It contains JSON representation of the event, and storage details for
 * events that may be stored externally (e.g., events larger than 4KB).
 *
 * <p>This class is used to manage the EPCIS event data that flows through the system, supporting
 * both inline storage (via the {@code objectNodeString}) for document wrapped EPCIS events < 4kb
 * and external storage S3/AzureBlob (via bucket and key fields) for events > 4kb.
 */
@RegisterForReflection
public class DocumentCaptureMessage extends CaptureData {
  protected OffsetDateTime recordTime;
  protected String objectNodeString;
  protected int eventIndex;
  protected Map<String, Object> metadata;
  protected List<String> eventIDs;
  protected String storageBucket;
  protected String storageKey;
  protected String eventStorageBucket; // Bucket to store EPCIS Event > 4kb to cloud
  protected String eventStorageKey; // Key associated with EPCIS Event > 4kb to store in cloud
  protected Map<String, String> eventStorageTags; // Tags associated to event > 4kb during POST to cloud used again for PUT

  // Primary constructor with all fields
  public DocumentCaptureMessage(final String captureID, final OffsetDateTime createdAt, final OffsetDateTime finishedAt, final boolean running, final boolean success, final String captureErrorBehaviour, final OffsetDateTime recordTime, final String objectNodeString, final int eventIndex, final String storageBucket, final String storageKey, final Map<String, Object> eventMetadata, final List<String> eventIDs, final List<InvalidEPCISEventInfo> errors) {
    super(captureID, createdAt, finishedAt, running, success, captureErrorBehaviour, errors);
    this.recordTime = recordTime;
    this.objectNodeString = objectNodeString;
    this.eventIndex = eventIndex;
    this.storageBucket = storageBucket;
    this.storageKey = storageKey;
    this.metadata = eventMetadata;
    this.eventIDs = eventIDs;
  }

  // Constructor with eventStorage fields
  public DocumentCaptureMessage(final String captureID, final OffsetDateTime createdAt, final OffsetDateTime finishedAt, final boolean running, final boolean success, final String captureErrorBehaviour, final OffsetDateTime recordTime, final int eventIndex, final String storageBucket, final String storageKey, final String eventStorageBucket, final String eventStorageKey, final Map<String, String> eventStorageTags, final Map<String, Object> eventMetadata, final List<String> eventIDs, final List<InvalidEPCISEventInfo> errors) {
    this(captureID, createdAt, finishedAt, running, success, captureErrorBehaviour, recordTime, null,  // objectNodeString
    eventIndex, storageBucket, storageKey, eventMetadata, eventIDs, errors);
    this.eventStorageBucket = eventStorageBucket;
    this.eventStorageKey = eventStorageKey;
    this.eventStorageTags = eventStorageTags;
  }

  public DocumentCaptureMessage() {
  }

  public DocumentCaptureMessage(OffsetDateTime recordTime, String objectNodeString, int eventIndex, Map<String, Object> metadata, List<String> eventIDs, String storageBucket, String storageKey, String eventStorageBucket, String eventStorageKey, Map<String, String> eventStorageTags) {
    this.recordTime = recordTime;
    this.objectNodeString = objectNodeString;
    this.eventIndex = eventIndex;
    this.metadata = metadata;
    this.eventIDs = eventIDs;
    this.storageBucket = storageBucket;
    this.storageKey = storageKey;
    this.eventStorageBucket = eventStorageBucket;
    this.eventStorageKey = eventStorageKey;
    this.eventStorageTags = eventStorageTags;
  }

  public OffsetDateTime getRecordTime() {
    return this.recordTime;
  }

  public String getObjectNodeString() {
    return this.objectNodeString;
  }

  public int getEventIndex() {
    return this.eventIndex;
  }

  public Map<String, Object> getMetadata() {
    return this.metadata;
  }

  public List<String> getEventIDs() {
    return this.eventIDs;
  }

  public String getStorageBucket() {
    return this.storageBucket;
  }

  public String getStorageKey() {
    return this.storageKey;
  }

  public String getEventStorageBucket() {
    return this.eventStorageBucket;
  }

  public String getEventStorageKey() {
    return this.eventStorageKey;
  }

  public Map<String, String> getEventStorageTags() {
    return this.eventStorageTags;
  }

  public void setRecordTime(OffsetDateTime recordTime) {
    this.recordTime = recordTime;
  }

  public void setObjectNodeString(String objectNodeString) {
    this.objectNodeString = objectNodeString;
  }

  public void setEventIndex(int eventIndex) {
    this.eventIndex = eventIndex;
  }

  public void setMetadata(Map<String, Object> metadata) {
    this.metadata = metadata;
  }

  public void setEventIDs(List<String> eventIDs) {
    this.eventIDs = eventIDs;
  }

  public void setStorageBucket(String storageBucket) {
    this.storageBucket = storageBucket;
  }

  public void setStorageKey(String storageKey) {
    this.storageKey = storageKey;
  }

  public void setEventStorageBucket(String eventStorageBucket) {
    this.eventStorageBucket = eventStorageBucket;
  }

  public void setEventStorageKey(String eventStorageKey) {
    this.eventStorageKey = eventStorageKey;
  }

  public void setEventStorageTags(Map<String, String> eventStorageTags) {
    this.eventStorageTags = eventStorageTags;
  }

  @Override
  public String toString() {
    return "DocumentCaptureMessage(recordTime=" + this.getRecordTime() + ", objectNodeString=" + this.getObjectNodeString() + ", eventIndex=" + this.getEventIndex() + ", metadata=" + this.getMetadata() + ", eventIDs=" + this.getEventIDs() + ", storageBucket=" + this.getStorageBucket() + ", storageKey=" + this.getStorageKey() + ", eventStorageBucket=" + this.getEventStorageBucket() + ", eventStorageKey=" + this.getEventStorageKey() + ", eventStorageTags=" + this.getEventStorageTags() + ")";
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) return true;
    if (!(o instanceof DocumentCaptureMessage)) return false;
    DocumentCaptureMessage other = (DocumentCaptureMessage) o;
    if (!other.canEqual((Object) this)) return false;
    if (!super.equals(o)) return false;
    if (this.getEventIndex() != other.getEventIndex()) return false;
    Object this$recordTime = this.getRecordTime();
    Object other$recordTime = other.getRecordTime();
    if (this$recordTime == null ? other$recordTime != null : !this$recordTime.equals(other$recordTime)) return false;
    Object this$objectNodeString = this.getObjectNodeString();
    Object other$objectNodeString = other.getObjectNodeString();
    if (this$objectNodeString == null ? other$objectNodeString != null : !this$objectNodeString.equals(other$objectNodeString)) return false;
    Object this$metadata = this.getMetadata();
    Object other$metadata = other.getMetadata();
    if (this$metadata == null ? other$metadata != null : !this$metadata.equals(other$metadata)) return false;
    Object this$eventIDs = this.getEventIDs();
    Object other$eventIDs = other.getEventIDs();
    if (this$eventIDs == null ? other$eventIDs != null : !this$eventIDs.equals(other$eventIDs)) return false;
    Object this$storageBucket = this.getStorageBucket();
    Object other$storageBucket = other.getStorageBucket();
    if (this$storageBucket == null ? other$storageBucket != null : !this$storageBucket.equals(other$storageBucket)) return false;
    Object this$storageKey = this.getStorageKey();
    Object other$storageKey = other.getStorageKey();
    if (this$storageKey == null ? other$storageKey != null : !this$storageKey.equals(other$storageKey)) return false;
    Object this$eventStorageBucket = this.getEventStorageBucket();
    Object other$eventStorageBucket = other.getEventStorageBucket();
    if (this$eventStorageBucket == null ? other$eventStorageBucket != null : !this$eventStorageBucket.equals(other$eventStorageBucket)) return false;
    Object this$eventStorageKey = this.getEventStorageKey();
    Object other$eventStorageKey = other.getEventStorageKey();
    if (this$eventStorageKey == null ? other$eventStorageKey != null : !this$eventStorageKey.equals(other$eventStorageKey)) return false;
    Object this$eventStorageTags = this.getEventStorageTags();
    Object other$eventStorageTags = other.getEventStorageTags();
    if (this$eventStorageTags == null ? other$eventStorageTags != null : !this$eventStorageTags.equals(other$eventStorageTags)) return false;
    return true;
  }

  protected boolean canEqual(Object other) {
    return other instanceof DocumentCaptureMessage;
  }

  @Override
  public int hashCode() {
    int PRIME = 59;
    int result = super.hashCode();
    result = result * PRIME + this.getEventIndex();
    Object $recordTime = this.getRecordTime();
    result = result * PRIME + ($recordTime == null ? 43 : $recordTime.hashCode());
    Object $objectNodeString = this.getObjectNodeString();
    result = result * PRIME + ($objectNodeString == null ? 43 : $objectNodeString.hashCode());
    Object $metadata = this.getMetadata();
    result = result * PRIME + ($metadata == null ? 43 : $metadata.hashCode());
    Object $eventIDs = this.getEventIDs();
    result = result * PRIME + ($eventIDs == null ? 43 : $eventIDs.hashCode());
    Object $storageBucket = this.getStorageBucket();
    result = result * PRIME + ($storageBucket == null ? 43 : $storageBucket.hashCode());
    Object $storageKey = this.getStorageKey();
    result = result * PRIME + ($storageKey == null ? 43 : $storageKey.hashCode());
    Object $eventStorageBucket = this.getEventStorageBucket();
    result = result * PRIME + ($eventStorageBucket == null ? 43 : $eventStorageBucket.hashCode());
    Object $eventStorageKey = this.getEventStorageKey();
    result = result * PRIME + ($eventStorageKey == null ? 43 : $eventStorageKey.hashCode());
    Object $eventStorageTags = this.getEventStorageTags();
    result = result * PRIME + ($eventStorageTags == null ? 43 : $eventStorageTags.hashCode());
    return result;
  }
}
