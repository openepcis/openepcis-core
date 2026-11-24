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
package io.openepcis.service.api;

import io.openepcis.model.dto.EPCISEventResponse;
import io.openepcis.model.dto.EPCPageResult;
import io.openepcis.model.dto.NamedQueryMetaDataResult;
import io.openepcis.model.dto.QueryResult;
import io.openepcis.model.epcis.EPCISEvent;
import io.openepcis.model.epcis.NamedQueryMetaData;
import io.openepcis.query.aggregation.EventAggregation;
import io.openepcis.query.aggregation.EventGroupBy;
import io.openepcis.query.context.QueryContext;
import io.smallrye.mutiny.Uni;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

public interface QueryService {

  Uni<String> saveEpcisQuery(String epcisQuery, Optional<String> gs1Extensions);

  Uni<QueryResult> queryEvents(String queryName, Integer perPage, QueryContext context);

  Uni<NamedQueryMetaDataResult> getEpcisQuery(Integer perPage);

  Uni<NamedQueryMetaDataResult> getEpcisQuery(String nextPageToken, Optional<Integer> perPage);

  Uni<NamedQueryMetaData> getEpcisQueryByName(String queryName);

  Uni<EPCISEvent> getEventbyId(String eventId, String eventAccessRoles);

  /** Every record with this eventID, oldest first: the event and any error declaration of it. */
  Uni<java.util.List<EPCISEvent>> getEventsById(String eventId, String defaultGroup);

  Uni<EPCISEventResponse> getEventbyId(String eventType, String eventID, String defaultGroup)
      throws ExecutionException, InterruptedException;

  Uni<QueryResult> getEventsOfNextPage(String nextPageToken, Integer perPage, QueryContext context);

  Uni<QueryResult> getTopLevelOfNextPage(
      String nextPageToken, Integer perPage, QueryContext context);

  Uni<QueryResult> getEvents(Optional<Integer> perPage, QueryContext context);

  Uni<QueryResult> getEvents(String epcisQuery, Optional<Integer> perPage, QueryContext context);

  Uni<QueryResult> getEventsByType(
      String eventType, Optional<Integer> perPage, QueryContext context);

  Uni<QueryResult> getEventsByEPC(String epc, Optional<Integer> perPage, QueryContext context);

  Uni<QueryResult> getTopLevelByFieldName(
      String fieldName, String field, Optional<Integer> perPage, QueryContext context);

  Uni<Boolean> deleteQuery(String queryName);

  Uni<Void> deleteNextPageToken(String token);

  /**
   * Counts the events {@link #queryEvents(QueryContext)} would return for the same parameters,
   * grouped by {@code groupBy}. Paging parameters are refused: there is nothing to page.
   */
  Uni<EventAggregation> aggregateEvents(QueryContext queryContext, EventGroupBy groupBy, int size);

  Uni<QueryResult> queryEvents(QueryContext queryContext);

  Uni<NamedQueryMetaData> getQueryByName(String queryName);

  Uni<Boolean> isQueryExists(String queryName);

  Uni<QueryResult> getTopLevelResourceNextPage(
      String nextPageToken, Integer perPage, QueryContext ctx, String fieldName);

  Uni<QueryResult> getTopLevelResource(
      Optional<Integer> perPage, QueryContext ctx, String fieldName);

  Uni<EPCPageResult> getEPCNextPage(String nextPageToken, Integer perPage, QueryContext ctx);

  Uni<EPCPageResult> getEPC(Optional<Integer> perPage, QueryContext ctx);

  Uni<String> updateEpcisQuery(String queryName, String epcisQuery, Optional<String> gs1Extensions);
}
