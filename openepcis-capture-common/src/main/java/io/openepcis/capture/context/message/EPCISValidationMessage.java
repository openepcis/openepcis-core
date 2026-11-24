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

import io.openepcis.model.dto.InvalidEPCISEventInfo;
import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.List;

/**
 * EPCISValidationMessage extends {@link DocumentCaptureMessage} and represents the outcome of
 * validating an EPCIS event. It aggregates information about invalid events, including counts of
 * valid and invalid events.
 *
 * <p>This class is used after a EPCIS event is processed, either to signal successful validation or
 * to carry the validation errors forward for further handling.
 */
@RegisterForReflection
public class EPCISValidationMessage extends DocumentCaptureMessage {
  private List<InvalidEPCISEventInfo> invalidEPCISEvents;
  private int invalidCount = 0;
  private int validCount = 0;
  /**
   * Where the persisted event is stored, as {@code index/_id}; set on the persisted message, null
   * when the event was not stored or its place is not known (an event found in a legacy index).
   */
  private String document;

  public String getDocument() {
    return document;
  }

  public void setDocument(final String document) {
    this.document = document;
  }

  public EPCISValidationMessage(final DocumentCaptureMessage v, final List<InvalidEPCISEventInfo> invalidEPCISEvents) {
    super(v.getCaptureID(), v.getCreatedAt(), v.getFinishedAt(), v.isRunning(), v.isSuccess(), v.getCaptureErrorBehaviour(), v.getRecordTime(), v.getObjectNodeString(), v.getEventIndex(), v.getStorageBucket(), v.getStorageKey(), v.getMetadata(), v.getEventIDs(), invalidEPCISEvents);
    this.invalidEPCISEvents = invalidEPCISEvents;
  }

  public EPCISValidationMessage updateFrom(final DocumentCaptureMessage msg, final List<InvalidEPCISEventInfo> invalidEPCISEvents) {
    // Update fields from Tracer class
    this.setTraceId(msg.getTraceId());
    this.setSpanId(msg.getSpanId());
    this.setDefaultGroup(msg.getDefaultGroup());
    this.setSubscribedUserGroups(msg.getSubscribedUserGroups());
    this.setUserId(msg.getUserId());
    // Update fields from CaptureData class
    this.setCaptureID(msg.getCaptureID());
    this.setCreatedAt(msg.getCreatedAt());
    this.setFinishedAt(msg.getFinishedAt());
    this.setRunning(msg.isRunning());
    this.setSuccess(msg.isSuccess());
    this.setCaptureErrorBehaviour(msg.getCaptureErrorBehaviour());
    this.setErrors(invalidEPCISEvents);
    // Update fields from DocumentCaptureMessage class
    this.setRecordTime(msg.getRecordTime());
    this.setObjectNodeString(msg.getObjectNodeString());
    this.setEventIndex(msg.getEventIndex());
    this.setStorageBucket(msg.getStorageBucket());
    this.setStorageKey(msg.getStorageKey());
    this.setMetadata(msg.getMetadata());
    this.setEventIDs(msg.getEventIDs());
    this.setEventStorageBucket(msg.getEventStorageBucket());
    this.setEventStorageKey(msg.getEventStorageKey());
    // Update fields from this EPCISValidationMessage class
    this.invalidEPCISEvents = invalidEPCISEvents;
    return this;
  }

  public EPCISValidationMessage updateFrom(final EPCISValidationMessage msg) {
    // Update fields from CaptureData class
    this.setCaptureID(msg.getCaptureID());
    this.setCreatedAt(msg.getCreatedAt());
    this.setFinishedAt(msg.getFinishedAt());
    this.setRunning(msg.isRunning());
    this.setSuccess(msg.isSuccess());
    this.setCaptureErrorBehaviour(msg.getCaptureErrorBehaviour());
    this.setErrors(msg.getErrors());
    // Update fields from DocumentCaptureMessage class
    this.setRecordTime(msg.getRecordTime());
    this.setObjectNodeString(msg.getObjectNodeString());
    this.setEventIndex(msg.getEventIndex());
    this.setStorageBucket(msg.getStorageBucket());
    this.setStorageKey(msg.getStorageKey());
    this.setMetadata(msg.getMetadata());
    this.setEventIDs(msg.getEventIDs());
    this.setEventStorageBucket(msg.getEventStorageBucket());
    this.setEventStorageKey(msg.getEventStorageKey());
    // Update fields from this EPCISValidationMessage class
    this.setInvalidEPCISEvents(msg.getInvalidEPCISEvents());
    return this;
  }

  public List<InvalidEPCISEventInfo> getInvalidEPCISEvents() {
    return this.invalidEPCISEvents;
  }

  public int getInvalidCount() {
    return this.invalidCount;
  }

  public int getValidCount() {
    return this.validCount;
  }

  public void setInvalidEPCISEvents(List<InvalidEPCISEventInfo> invalidEPCISEvents) {
    this.invalidEPCISEvents = invalidEPCISEvents;
  }

  public void setInvalidCount(int invalidCount) {
    this.invalidCount = invalidCount;
  }

  public void setValidCount(int validCount) {
    this.validCount = validCount;
  }

  @Override
  public String toString() {
    return "EPCISValidationMessage(invalidEPCISEvents=" + this.getInvalidEPCISEvents() + ", invalidCount=" + this.getInvalidCount() + ", validCount=" + this.getValidCount() + ")";
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) return true;
    if (!(o instanceof EPCISValidationMessage)) return false;
    EPCISValidationMessage other = (EPCISValidationMessage) o;
    if (!other.canEqual((Object) this)) return false;
    if (!super.equals(o)) return false;
    if (this.getInvalidCount() != other.getInvalidCount()) return false;
    if (this.getValidCount() != other.getValidCount()) return false;
    Object this$invalidEPCISEvents = this.getInvalidEPCISEvents();
    Object other$invalidEPCISEvents = other.getInvalidEPCISEvents();
    if (this$invalidEPCISEvents == null ? other$invalidEPCISEvents != null : !this$invalidEPCISEvents.equals(other$invalidEPCISEvents)) return false;
    return true;
  }

  protected boolean canEqual(Object other) {
    return other instanceof EPCISValidationMessage;
  }

  @Override
  public int hashCode() {
    int PRIME = 59;
    int result = super.hashCode();
    result = result * PRIME + this.getInvalidCount();
    result = result * PRIME + this.getValidCount();
    Object $invalidEPCISEvents = this.getInvalidEPCISEvents();
    result = result * PRIME + ($invalidEPCISEvents == null ? 43 : $invalidEPCISEvents.hashCode());
    return result;
  }

  public EPCISValidationMessage() {
  }
}
