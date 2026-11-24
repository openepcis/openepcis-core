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

import io.openepcis.model.dto.*;
import io.openepcis.model.epcis.EPCISEvent;
import io.openepcis.model.epcis.NamedQueryMetaData;
import io.openepcis.model.epcis.NamedQuerySubscription;
import io.openepcis.model.epcis.UserExtensionField;
import io.openepcis.model.epcis.format.FormatPreference;
import io.openepcis.query.aggregation.EventAggregation;
import io.openepcis.query.aggregation.EventGroupBy;
import io.openepcis.query.data.SimpleEventQueryRequest;
import io.openepcis.repository.model.EPCISEventES;
import io.openepcis.repository.model.EpcES;
import io.openepcis.repository.model.StreamingSubscriptionQueryES;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface QueryRepository {
  Uni<QueryResult> executeEventsQuery(
      SimpleEventQueryRequest request,
      Integer perPage,
      FormatPreference preference,
      boolean isSuperAdminClient,
      Optional<String> defaultGroup);

  /**
   * Counts the events {@link #executeEventsQuery} would return for the same request, grouped by
   * {@code groupBy}, without returning any of them. The same tenant isolation applies: the
   * request-scoped client (document-level security) unless {@code isSuperAdminClient}, and the
   * {@code defaultGroup} filter when a tenant is named.
   *
   * @param size the number of buckets of a terms grouping; ignored for a calendar histogram
   */
  Uni<EventAggregation> aggregateEvents(
      SimpleEventQueryRequest request,
      EventGroupBy groupBy,
      int size,
      boolean isSuperAdminClient,
      Optional<String> defaultGroup);

  Uni<CaptureJob> getCaptureJobFromCaptureID(String captureID);

  Uni<String> saveEpcisQuery(NamedQueryMetaData namedQueryMetaData);

  Uni<Boolean> isQueryExists(String name);

  Uni<NamedQueryMetaData> getQueryByName(String queryName, boolean hasSuperAdminClientAccess);

  Uni<NamedQueryMetaDataResult> getAllEpcisQuery(Integer perPage);

  Uni<NamedQueryMetaDataResult> getAllEpcisQuery(String nextPageToken, Optional<Integer> perPage);

  Uni<EPCISEvent> getEventById(String eventId, String defaultGroup);

  /**
   * Every record with this eventID, oldest first. An error declaration carries the eventID of the
   * event it declares erroneous, so one eventID can name two records; EPCIS 2.0 (12.7.4, /events/{eventID})
   * returns both.
   */
  default Uni<List<EPCISEvent>> getEventsById(String eventId, String defaultGroup) {
    return getEventById(eventId, defaultGroup).map(List::of);
  }

  Uni<List<EPCISEvent>> getEPCISEventByHash(String... setOfHash);

  /**
   * The records with this content hash that this tenant captured. An error declaration may only
   * name an event of its own tenant; unscoped, the capture path (which runs without the caller's
   * request) looked across every tenant. A blank tenant falls back to the unscoped lookup.
   */
  default Uni<List<EPCISEvent>> getTenantEventsByHash(String tenant, String hash) {
    return getEPCISEventByHash(hash);
  }

  /** {@link #getEventIDs(List)} within one tenant; see {@link #getTenantEventsByHash}. */
  default Uni<Set<String>> getTenantEventIDs(String tenant, List<String> eventIDs) {
    return getEventIDs(eventIDs);
  }

  Uni<QueryResult> getEventsFromNextPageToken(
      String nextPageToken, FormatPreference preference, Optional<Integer> perPage);

  Uni<QueryResult> getTopLevelFromNextPageToken(
      String nextPageToken, FormatPreference preference, Optional<Integer> perPage);

  Uni<String> getSessionIDFromSubscriptionID(String subscriptionID);

  Multi<NamedQuerySubscription> getNamedSubscriptionFromQueryName(
      String queryName, String defaultGroup);

  Uni<Boolean> deleteStreamingQuery(String queryID);

  Uni<String> getIDByQueryName(String queryName);

  Uni<Boolean> deleteNamedQuery(String queryID);

  Uni<List<String>> getSubscriptionIdsFromQueryName(String queryName);

  Uni<EPCISEvent> getEPCISEventByTypeAndID(String eventType, String eventID);

  void deletePIT(String pitID);

  void updateCaptureBehaviour(
      String captureID,
      boolean running,
      boolean success,
      List<InvalidEPCISEventInfo> errors,
      ZonedDateTime docCreationDate,
      String defaultGroup);

  Uni<CaptureJobPageResult> getCaptureJobs(Integer perPage);

  Uni<CaptureJobPageResult> getCaptureJobs(String nextPageToken, Optional<Integer> perPage);

  Uni<EPCISEventES> getEPCISEventESFromHash(String[] arrayOfHashes);

  Uni<Boolean> updateEventMetaData(String hash, Map<String, Object> metadata, String captureId)
      throws IOException;

  Uni<Set<String>> getEventIDs(List<String> eventIDs);

  Uni<Set<UserExtensionField>> getAllUserExtensionFields();

  Uni<Optional<UserExtensionField>> getUserExtensionField(String name);

  Uni<Long> getEventCountByCaptureID(String captureID) throws IOException;

  Multi<String> getEventIDsByCaptureID(String captureID);

  Uni<EPCISEventES> getEPCISEventByEventID(String eventID);

  /**
   * One stored record by its index and document id, read with a realtime GET: what was just
   * written, without a refresh, and exactly that record (an eventID can name an event and its error
   * declaration). {@code null} when it is not there; repositories that cannot read by document
   * answer {@code null} and the caller falls back to {@link #getEPCISEventByEventID}.
   */
  default Uni<EPCISEventES> getEPCISEventByDocument(String index, String documentId) {
    return Uni.createFrom().nullItem();
  }

  /**
   * The record an {@code event-saved} message announces: by its document when the message names
   * one, otherwise (older messages, replays, older events) by its eventID.
   */
  default Uni<EPCISEventES> getSavedEvent(String index, String documentId, String eventID) {
    if (index != null && !index.isBlank() && documentId != null && !documentId.isBlank()) {
      return getEPCISEventByDocument(index, documentId)
          .chain(found -> found != null ? Uni.createFrom().item(found) : getEPCISEventByEventID(eventID));
    }
    return getEPCISEventByEventID(eventID);
  }

  Uni<String> indexStreamingPercolator(
      String id, String queryName, SimpleEventQueryRequest simpleEventQueryRequest);

  Multi<StreamingSubscriptionQueryES> percolateEvent(EPCISEventES event, String defaultGroup);

  Uni<QueryResult> getTopLevelResource(
      Optional<Integer> perPage, String fieldName, FormatPreference formatPreference);

  Uni<QueryResult> getTopLevelResourceFromNextPageToken(
      final String nextPageToken,
      final Optional<Integer> perPage,
      String fieldName,
      FormatPreference formatPreference);

  Uni<Boolean> saveEpcs(Map<String, EpcES> epcHashMap);

  Uni<EPCPageResult> getEPCFromNextPageToken(
      String nextPageToken, Optional<Integer> perPage, FormatPreference formatPreference);

  Uni<EPCPageResult> getEPC(Optional<Integer> perPage, FormatPreference formatPreference);

  /**
   * The stored events behind the given documents ({@code index/_id}), read by a realtime multi get
   * and keyed by the document. Documents that are not there are missing from the map. Only
   * eventID and the capture metadata are loaded.
   */
  default Uni<Map<String, EPCISEventES>> getEventsByDocuments(List<String> documents) {
    return Uni.createFrom().failure(new UnsupportedOperationException("getEventsByDocuments"));
  }
}
