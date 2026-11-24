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

import io.openepcis.model.dto.Tracer;

public class EventSavedMessage extends Tracer {
  private String eventID;

  /**
   * Where the saved record is: its index and document id. A consumer reads it with a GET, which
   * sees it at once and is exact; an eventID can name an event and its error declaration, and a
   * search sees only what was refreshed. Absent on messages for events stored before documents
   * had a deterministic id, and for the replay; readers then fall back to the eventID.
   */
  private String index;

  private String documentId;

  public String getIndex() {
    return index;
  }

  public void setIndex(String index) {
    this.index = index;
  }

  public String getDocumentId() {
    return documentId;
  }

  public void setDocumentId(String documentId) {
    this.documentId = documentId;
  }

  /** Whether the message says where the record is. */
  public boolean hasDocument() {
    return index != null && !index.isBlank() && documentId != null && !documentId.isBlank();
  }


  public static class EventSavedMessageBuilder {
    private String eventID;

    EventSavedMessageBuilder() {
    }

    /**
     * @return {@code this}.
     */
    public EventSavedMessage.EventSavedMessageBuilder eventID(String eventID) {
      this.eventID = eventID;
      return this;
    }

    public EventSavedMessage build() {
      return new EventSavedMessage(this.eventID);
    }

    @Override
    public String toString() {
      return "EventSavedMessage.EventSavedMessageBuilder(eventID=" + this.eventID + ")";
    }
  }

  public static EventSavedMessage.EventSavedMessageBuilder builder() {
    return new EventSavedMessage.EventSavedMessageBuilder();
  }

  public String getEventID() {
    return this.eventID;
  }

  public void setEventID(String eventID) {
    this.eventID = eventID;
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) return true;
    if (!(o instanceof EventSavedMessage)) return false;
    EventSavedMessage other = (EventSavedMessage) o;
    if (!other.canEqual((Object) this)) return false;
    Object this$eventID = this.getEventID();
    Object other$eventID = other.getEventID();
    if (this$eventID == null ? other$eventID != null : !this$eventID.equals(other$eventID)) return false;
    return true;
  }

  protected boolean canEqual(Object other) {
    return other instanceof EventSavedMessage;
  }

  @Override
  public int hashCode() {
    int PRIME = 59;
    int result = 1;
    Object $eventID = this.getEventID();
    result = result * PRIME + ($eventID == null ? 43 : $eventID.hashCode());
    return result;
  }

  @Override
  public String toString() {
    return "EventSavedMessage(eventID=" + this.getEventID() + ", index=" + index + ", documentId=" + documentId + ")";
  }

  public EventSavedMessage() {
  }

  public EventSavedMessage(String eventID) {
    this.eventID = eventID;
  }
}
