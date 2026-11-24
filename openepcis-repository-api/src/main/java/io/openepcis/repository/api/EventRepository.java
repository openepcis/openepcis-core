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
package io.openepcis.repository.api;

import io.openepcis.model.dto.CaptureJob;
import io.openepcis.model.dto.InvalidEPCISEventInfo;
import io.openepcis.model.epcis.EPCISEvent;
import io.openepcis.model.epcis.UserExtensionField;
import io.smallrye.mutiny.Uni;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface EventRepository {

  Uni<String> saveCaptureJob(
      CaptureJob body,
      String captureID,
      OffsetDateTime recordDate,
      String defaultGroup,
      Map<String, Object> metadata);

  Uni<Boolean> bulkSaveOrUpdate(
      List<EPCISEvent> listEPCISEvent,
      List<InvalidEPCISEventInfo> invalidEventsInfo,
      boolean isProceed,
      String captureID,
      ZonedDateTime docCreationDate,
      Map<String, Object> metadata,
      String defaultGroup,
      List<Object> context,
      boolean refresh);

  Uni<Boolean> bulkSaveUserExtensionFields(Set<UserExtensionField> userExtensions);

  /**
   * The index a newly stored event with this eventTime goes to, or {@code null} when this
   * repository does not place events deterministically (then there is no document to name).
   * See {@code EventDocuments}.
   */
  default String eventIndexName(java.time.OffsetDateTime eventTime) {
    return null;
  }

  Uni<Boolean> removeEventsForCaptureID(
      String captureID, ZonedDateTime createdAt, String defaultGroup);

  /**
   * Deletes every event with this eventID in every tenant's index.
   *
   * @deprecated Nothing may call this for an error declaration: EPCIS 2.0 keeps the erroneous event
   *     unmodified next to its declaration, and both share the eventID. Kept only because other
   *     implementations of this interface still override it.
   */
  @Deprecated(forRemoval = true)
  Uni<Boolean> deleteEventsFromEventID(String eventID);

  Uni<Boolean> updateVisibilityByCaptureID(
      String captureID, ZonedDateTime createdAt, String defaultGroup);

  /**
   * Marks the given event documents visible in one bulk update by id, without waiting for a
   * refresh. Each document is named {@code index/_id}. False when any update failed. A
   * repository that does not place events by document answers with a failure, and the caller
   * falls back to {@link #updateVisibilityByCaptureID}.
   */
  default Uni<Boolean> markVisible(List<String> documents) {
    return Uni.createFrom().failure(new UnsupportedOperationException("markVisible"));
  }

  /**
   * Makes what was written to the event indices searchable. Only the fallback that finds a
   * capture's events by search needs it; captures write without waiting for a refresh.
   */
  default Uni<Void> refreshEventIndices() {
    return Uni.createFrom().voidItem();
  }
}
