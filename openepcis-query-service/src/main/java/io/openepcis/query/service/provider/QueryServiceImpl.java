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
package io.openepcis.query.service.provider;

import io.openepcis.model.dto.EPCISEventResponse;
import io.openepcis.model.dto.EPCPageResult;
import io.openepcis.model.dto.NamedQueryMetaDataResult;
import io.openepcis.model.dto.QueryResult;
import io.openepcis.model.epcis.EPCISEvent;
import io.openepcis.model.epcis.NamedQueryMetaData;
import io.openepcis.model.epcis.exception.DuplicateNameException;
import io.openepcis.model.epcis.exception.ExceptionMessages;
import io.openepcis.model.epcis.exception.NoSuchEventTypeException;
import io.openepcis.model.epcis.exception.NoSuchNameException;
import io.openepcis.model.epcis.exception.QueryValidationException;
import io.openepcis.model.epcis.format.FormatPreference;
import io.openepcis.model.epcis.exception.QueryParameterException;
import io.openepcis.query.aggregation.EventAggregation;
import io.openepcis.query.aggregation.EventGroupBy;
import io.openepcis.query.context.QueryContext;
import io.openepcis.query.data.SimpleEventQueryRequest;
import io.openepcis.query.service.util.OrderByUtil;
import io.openepcis.query.service.util.QueryParamUtil;
import io.openepcis.query.service.validator.NamedQueryValidator;
import io.openepcis.repository.Constants;
import io.openepcis.repository.api.KeyValueRepository;
import io.openepcis.repository.api.QueryRepository;
import io.openepcis.repository.api.SubscriptionRepository;
import io.openepcis.repository.config.RepositoryConfiguration;
import io.openepcis.repository.util.JSONUtil;
import io.openepcis.service.api.QueryService;
import io.openepcis.service.common.ContextBuilder;
import io.openepcis.service.util.EventPredicates;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import java.time.OffsetDateTime;
import java.util.*;
import static io.openepcis.model.epcis.constants.QueryParameterConstants.*;

@ApplicationScoped
public class QueryServiceImpl implements QueryService {
  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(QueryServiceImpl.class);
  private final SubscriptionRepository subscriptionRepository;
  private final QueryRepository queryRepository;
  private final NamedQueryValidator namedQueryValidator;
  private final ContextBuilder contextBuilder;
  private final KeyValueRepository keyValueRepository;
  private final OrderByUtil orderByUtil;
  private final QueryParamUtil queryParamUtil;
  private final RepositoryConfiguration repositoryConfiguration;

  @Override
  public Uni<String> saveEpcisQuery(String epcisQuery, Optional<String> gs1Extensions) {
    return storeEpcisQuery(epcisQuery, gs1Extensions, false);
  }

  /**
   * Stores a named query, either as a new one or in place of one being replaced.
   *
   * <p>The only difference is whether a name already in the repository is an error. For a new
   * query it is, and the caller is told so with a conflict. For a replacement the name is
   * expected to be there, and asking again would be a race besides: the index needs a moment to
   * forget the query just deleted, and a replacement issued straight away would be refused for a
   * name that is on its way out.
   */
  private Uni<String> storeEpcisQuery(String epcisQuery, Optional<String> gs1Extensions, boolean replacing) {
    final String name = getQueryName(epcisQuery);
    if (StringUtils.isBlank(name)) {
      throw new QueryValidationException("missing query name");
    }
    final NamedQueryMetaData namedQueryMetaData = JSONUtil.deserializeJson(epcisQuery, NamedQueryMetaData.class);
    namedQueryMetaData.setName(name);
    if (namedQueryMetaData.getEpcisQuery() == null) {
      // Without this the query travelled as a null map and failed several frames deeper as a
      // NullPointerException, which reached the client as a 500. A body without a query is a
      // request the client got wrong, and the standard requires the member.
      throw new QueryValidationException("missing query");
    }
    return queryParamUtil.getListOfQueryParams(namedQueryMetaData.getEpcisQuery(), false, gs1Extensions).chain(queryParams -> Uni.createFrom().item(new SimpleEventQueryRequest(queryParams))).chain(simpleEventQueryRequest -> (replacing ? Uni.createFrom().item(Boolean.FALSE) : namedQueryValidator.validateQueryName(name)).chain(response -> {
      if (response) {
        throw new DuplicateNameException(ExceptionMessages.QUERY_NAME_ALREADY_EXIST);
      }
      namedQueryValidator.validateEPCISQuery(epcisQuery);
      if (namedQueryMetaData.getCreatedAt() == null) namedQueryMetaData.setCreatedAt(OffsetDateTime.now());
      namedQueryValidator.validateQueryParameters(namedQueryMetaData.getEpcisQuery());
      return queryRepository.saveEpcisQuery(namedQueryMetaData);
    }).chain(documentId -> {
      if (namedQueryMetaData.getEpcisQuery().containsKey(ORDERBY)) {
        final String orderBy = namedQueryMetaData.getEpcisQuery().get(ORDERBY).toString();
        if (orderBy.contains(":")) {
          return orderByUtil.buildOrderByUX((Map<String, Object>) namedQueryMetaData.getEpcisQuery().get(CONTEXT), orderBy).chain(type -> {
            simpleEventQueryRequest.setOrderByUX(type);
            return queryRepository.indexStreamingPercolator(documentId, name, simpleEventQueryRequest).onFailure().recoverWithUni(throwable -> queryRepository.deleteNamedQuery(documentId).chain(b -> Uni.createFrom().failure(throwable)));
          }).onFailure().invoke(failure -> log.error("Failed to index streaming percolator for epcisQuery=%s".formatted(epcisQuery), failure.getCause()));
        }
      }
      return queryRepository.indexStreamingPercolator(documentId, name, simpleEventQueryRequest).onFailure().recoverWithUni(throwable -> queryRepository.deleteNamedQuery(documentId).chain(b -> Uni.createFrom().failure(throwable)));
    })).chain(result -> Uni.createFrom().item(name));
  }

  @Override
  public Uni<NamedQueryMetaDataResult> getEpcisQuery(Integer perPage) {
    return queryRepository.getAllEpcisQuery(perPage);
  }

  @Override
  public Uni<NamedQueryMetaDataResult> getEpcisQuery(String nextPageToken, Optional<Integer> perPage) {
    return queryRepository.getAllEpcisQuery(nextPageToken, perPage);
  }

  @Override
  public Uni<NamedQueryMetaData> getEpcisQueryByName(String queryName) {
    return queryRepository.getQueryByName(queryName, false);
  }

  @Override
  public Uni<EPCISEvent> getEventbyId(String eventId, String defaultGroup) {
    return queryRepository.getEventById(eventId, defaultGroup);
  }

  @Override
  public Uni<java.util.List<EPCISEvent>> getEventsById(String eventId, String defaultGroup) {
    return queryRepository.getEventsById(eventId, defaultGroup);
  }

  @Override
  public Uni<EPCISEventResponse> getEventbyId(String eventType, String eventId, String defaultGroup) {
    final Uni<EPCISEvent> epcisEventUni;
    if (EventPredicates.INVALID_EVENT_TYPE.test(eventType)) throw new NoSuchEventTypeException(eventType + " is invalid event type.");
    if (StringUtils.equalsIgnoreCase(eventType, "all")) {
      epcisEventUni = queryRepository.getEventById(eventId, defaultGroup);
    } else {
      epcisEventUni = queryRepository.getEPCISEventByTypeAndID(eventType, eventId);
    }
    return epcisEventUni.map(EPCISEventResponse::new);
  }

  @Override
  public Uni<QueryResult> getEventsOfNextPage(String nextPageToken, Integer perPage, QueryContext context) {
    return queryRepository.getEventsFromNextPageToken(nextPageToken, context.getFormatPreference(), Optional.ofNullable(perPage));
  }

  @Override
  public Uni<QueryResult> getTopLevelOfNextPage(String nextPageToken, Integer perPage, QueryContext context) {
    return queryRepository.getEventsFromNextPageToken(nextPageToken, context.getFormatPreference(), Optional.ofNullable(perPage));
  }

  @Override
  public Uni<QueryResult> getEvents(Optional<Integer> perPage, QueryContext context) {
    return executeEventsQuery(context.getGs1Extensions().get(), perPage, context.getParams(), context.getFormatPreference());
  }

  private Map<String, Object> buildContextAsMap(Map<String, Object> params, Optional<String> userExtensionAsCSV) {
    final JSONObject paramsAsJson = new JSONObject(params);
    Map<String, Object> contextAsMap = new HashMap<>();
    if (params.containsKey(CONTEXT)) {
      contextAsMap = contextBuilder.collectContextInfoIntoMap(paramsAsJson.get(CONTEXT), contextAsMap);
    }
    if (userExtensionAsCSV.isPresent()) {
      ContextBuilder.addGs1ExtensionInContext(userExtensionAsCSV, contextAsMap);
    }
    return contextAsMap;
  }

  @Override
  public Uni<QueryResult> getEvents(String epcisQueryAsString, Optional<Integer> perPage, QueryContext context) {
    final Map<String, Object> epcisQuery = JSONUtil.deserializeJson(epcisQueryAsString, Map.class);
    namedQueryValidator.validateEPCISQuery(epcisQueryAsString);
    namedQueryValidator.validateQueryParameters((Map<String, Object>) epcisQuery.get("query"));
    return executeEventsQuery(context.getGs1Extensions().get(), perPage, (Map<String, Object>) epcisQuery.get("query"), context.getFormatPreference());
  }

  @Override
  public Uni<QueryResult> getEventsByType(String eventType, Optional<Integer> perPage, QueryContext context) {
    final Map<String, Object> params = context.getParams();
    namedQueryValidator.validateForEventTypesEndPoint(eventType, params);
    if (params.containsKey("eventType")) throw new QueryValidationException("Query param: eventType not allowed here.");
    if (!eventType.equalsIgnoreCase("all")) params.put("eventType", List.of(eventType));
    return executeEventsQuery(context.getGs1Extensions().get(), perPage, params, context.getFormatPreference());
  }

  @Override
  public Uni<QueryResult> getEventsByEPC(String epc, Optional<Integer> perPage, QueryContext context) {
    final Map<String, Object> params = context.getParams();
    if (params.containsKey("epc")) throw new QueryValidationException("Query param: epc not allowed here.");
    params.remove(PER_PAGE);
    params.put("EPC", List.of(epc));
    return executeEventsQuery(context.getGs1Extensions().get(), perPage, params, context.getFormatPreference());
  }

  @Override
  public Uni<QueryResult> getTopLevelByFieldName(String fieldName, String fieldValue, Optional<Integer> perPage, QueryContext context) {
    final Map<String, Object> params = context.getParams();
    params.remove(PER_PAGE);
    if (params.containsKey(fieldName)) throw new QueryValidationException(String.format("Query param: %s not allowed here.", fieldName));
    params.put("EQ_" + fieldName, List.of(fieldValue));
    return executeEventsQuery(context.getGs1Extensions().get(), perPage, params, context.getFormatPreference());
  }

  private Uni<QueryResult> executeEventsQuery(Optional<String> userExtensionAsCSV, Optional<Integer> perPage, Map<String, Object> epcisQuery, FormatPreference preference) {
    Map<String, Object> contextAsMap = buildContextAsMap(epcisQuery, userExtensionAsCSV);
    if (MapUtils.isNotEmpty(contextAsMap)) {
      epcisQuery.put(CONTEXT, contextAsMap);
    }
    return queryParamUtil.getListOfQueryParams(epcisQuery, true, userExtensionAsCSV).chain(queryParams -> Uni.createFrom().item(new SimpleEventQueryRequest(queryParams))).chain(simpleEventQueryRequest -> {
      if (epcisQuery.containsKey(ORDERBY)) {
        final String orderBy = epcisQuery.get(ORDERBY).toString();
        if (orderBy.contains(":")) {
          return orderByUtil.buildOrderByUX(contextAsMap, orderBy).chain(type -> {
            simpleEventQueryRequest.setOrderByUX(type);
            return queryRepository.executeEventsQuery(simpleEventQueryRequest, perPage.orElse(repositoryConfiguration.epcis().perPageDefault()), preference, false, Optional.empty());
          });
        }
      }
      return queryRepository.executeEventsQuery(simpleEventQueryRequest, perPage.orElse(repositoryConfiguration.epcis().perPageDefault()), preference, false, Optional.empty());
    });
  }

  @Override
  public Uni<QueryResult> queryEvents(String queryName, Integer perPage, QueryContext context) {
    return queryRepository.getQueryByName(queryName, false).chain(namedQueryMetaData -> {
      namedQueryMetaData.getEpcisQuery().put(Constants.VISIBLE, context.getParams().get(Constants.VISIBLE));
      return queryParamUtil.getListOfQueryParams(namedQueryMetaData.getEpcisQuery(), false, context.getGs1Extensions().get()).chain(queryParams -> Uni.createFrom().item(new SimpleEventQueryRequest(queryParams))).chain(simpleEventQueryRequest -> {
        if (namedQueryMetaData.getEpcisQuery().containsKey(ORDERBY)) {
          final String orderBy = namedQueryMetaData.getEpcisQuery().get(ORDERBY).toString();
          if (orderBy.contains(":")) {
            return orderByUtil.buildOrderByUX((Map<String, Object>) namedQueryMetaData.getEpcisQuery().get(CONTEXT), orderBy).chain(type -> {
              simpleEventQueryRequest.setOrderByUX(type);
              return queryRepository.executeEventsQuery(simpleEventQueryRequest, perPage, context.getFormatPreference(), false, Optional.empty());
            });
          }
        }
        return queryRepository.executeEventsQuery(simpleEventQueryRequest, perPage, context.getFormatPreference(), false, Optional.empty());
      });
    });
  }

  @Override
  public Uni<Boolean> deleteQuery(final String queryName) {
    return queryRepository.getIDByQueryName(queryName).chain(queryID -> queryRepository.getSubscriptionIdsFromQueryName(queryName).chain(subscriptionRepository::deleteSubscription).chain(r -> {
      if (r) {
        return queryRepository.deleteStreamingQuery(queryID).chain(rr -> {
          if (rr) {
            return queryRepository.deleteNamedQuery(queryID);
          }
          return Uni.createFrom().item(false);
        });
      }
      return Uni.createFrom().item(false);
    })).onFailure().recoverWithUni(throwable -> {
      log.error("Error while deleting the query {} with its subscriptions and percolator", queryName, throwable);
      return Uni.createFrom().failure(throwable);
    });
  }

  @Override
  public Uni<Void> deleteNextPageToken(String token) {
    // Returns the chain instead of subscribing to it: the previous
    // fire-and-forget subscribe() answered the caller before the delete had
    // run, and its consumer had no failure handler, so a failed invalidation
    // was reported to the client as success and logged nowhere.
    return keyValueRepository
        .get(token)
        .map(searchBuilder -> {
          queryRepository.deletePIT(
              new JSONObject(searchBuilder).getJSONObject(Constants.PIT).getString(Constants.ID));
          return searchBuilder;
        })
        .chain(searchBuilder -> keyValueRepository.delete(token))
        .invoke(() -> log.info("{} token successfully invalidated.", token))
        .replaceWithVoid();
  }

  @Override
  public Uni<EventAggregation> aggregateEvents(QueryContext queryContext, EventGroupBy groupBy, int size) {
    final Map<String, Object> params = queryContext.getParams();
    for (final String paging : List.of(PER_PAGE, NEXT_PAGE_TOKEN)) {
      if (params.containsKey(paging)) {
        throw new QueryParameterException(String.format("'%s' is not accepted by /events/aggregate: an aggregation has no pages.", paging));
      }
    }
    // the same parameters as GET /events, parsed the same way, the visible filter included
    final Map<String, Object> contextAsMap = buildContextAsMap(params, queryContext.getGs1Extensions().get());
    if (MapUtils.isNotEmpty(contextAsMap)) {
      params.put(CONTEXT, contextAsMap);
    }
    return queryParamUtil.getListOfQueryParams(params, true, queryContext.getGs1Extensions().get())
        .map(SimpleEventQueryRequest::new)
        .chain(request -> queryRepository.aggregateEvents(request, groupBy, size, false, Optional.empty()));
  }

  @Override
  public Uni<QueryResult> queryEvents(QueryContext queryContext) {
    final Map<String, Object> params = queryContext.getParams();
    final Integer perPage = params.containsKey(PER_PAGE) ? Integer.parseInt(((List<String>) params.remove(PER_PAGE)).get(0)) : null;
    final Optional<Integer> optionalPerPage = Optional.ofNullable(perPage);
    final String nextPageToken = params.containsKey(NEXT_PAGE_TOKEN) ? ((List<String>) params.remove(NEXT_PAGE_TOKEN)).get(0) : null;
    ;
    if (StringUtils.isNotBlank(nextPageToken)) {
      return queryRepository.getEventsFromNextPageToken(nextPageToken, queryContext.getFormatPreference(), optionalPerPage);
    } else {
      Map<String, Object> contextAsMap = buildContextAsMap(params, queryContext.getGs1Extensions().get());
      if (MapUtils.isNotEmpty(contextAsMap)) {
        params.put(CONTEXT, contextAsMap);
      }
      return queryParamUtil.getListOfQueryParams(params, true, queryContext.getGs1Extensions().get()).chain(queryParams -> Uni.createFrom().item(new SimpleEventQueryRequest(queryParams))).chain(simpleEventQueryRequest -> queryRepository.executeEventsQuery(simpleEventQueryRequest, optionalPerPage.orElse(repositoryConfiguration.epcis().perPageDefault()), queryContext.getFormatPreference(), false, Optional.empty()));
    }
  }

  @Override
  public Uni<NamedQueryMetaData> getQueryByName(String queryName) {
    return queryRepository.getQueryByName(queryName, false);
  }

  @Override
  public Uni<Boolean> isQueryExists(String queryName) {
    return queryRepository.isQueryExists(queryName);
  }

  private String getQueryName(String epcisQuery) {
    final JSONObject jsonObject = new JSONObject(epcisQuery);
    // getString throws when the member is absent, and that JSONException reached the client as a
    // 500 reading JSONObject["name"] not found. The standard requires name, so a body without one
    // is a bad request and is answered as one.
    if (!jsonObject.has(Constants.NAME)) {
      throw new QueryValidationException("missing query name");
    }
    return jsonObject.optString(Constants.NAME);
  }

  @Override
  public Uni<QueryResult> getTopLevelResourceNextPage(String nextPageToken, Integer perPage, QueryContext ctx, String fieldName) {
    return queryRepository.getTopLevelResourceFromNextPageToken(nextPageToken, Optional.of(perPage), fieldName, ctx.getFormatPreference());
  }

  @Override
  public Uni<QueryResult> getTopLevelResource(Optional<Integer> perPage, QueryContext ctx, String fieldName) {
    return queryRepository.getTopLevelResource(perPage, fieldName, ctx.getFormatPreference());
  }

  @Override
  public Uni<EPCPageResult> getEPCNextPage(String nextPageToken, Integer perPage, QueryContext ctx) {
    return queryRepository.getEPCFromNextPageToken(nextPageToken, Optional.of(perPage), ctx.getFormatPreference());
  }

  @Override
  public Uni<EPCPageResult> getEPC(Optional<Integer> perPage, QueryContext ctx) {
    return queryRepository.getEPC(perPage, ctx.getFormatPreference());
  }

  /**
   * Replaces the definition of an existing named query, keeping its subscribers.
   *
   * <p>This used to delegate to the save path, which refuses a name that is already in the
   * repository. Every update of an existing query was therefore answered with a duplicate name,
   * and an update of one that did not exist with a 404, so the endpoint could not succeed at all.
   *
   * <p>The old definition is given up only once the new one has been validated, and the
   * subscriptions of the query are deliberately left alone: they name the query, and a subscriber
   * that asked for "what failed inspection" still wants that answer after the question behind it
   * has been sharpened. Dropping them is what DELETE is for.
   */
  @Override
  public Uni<String> updateEpcisQuery(String queryName, String epcisQuery, Optional<String> gs1Extensions) {
    final String name = getQueryName(epcisQuery);
    if (!StringUtils.equals(queryName, name)) {
      throw new QueryValidationException(String.format("the query is named %s in the body and %s in the path", name, queryName));
    }
    return queryRepository.getIDByQueryName(queryName).chain(queryID -> {
      if (queryID == null) {
        return Uni.createFrom().failure(new NoSuchNameException(String.format("No EPCIS Query exists with the name: %s", queryName)));
      }
      namedQueryValidator.validateEPCISQuery(epcisQuery);
      return queryRepository.deleteStreamingQuery(queryID)
          .chain(ignored -> queryRepository.deleteNamedQuery(queryID))
          .chain(ignored -> storeEpcisQuery(epcisQuery, gs1Extensions, true));
    }).onFailure().invoke(throwable -> log.error("Error while updating query: {}", queryName, throwable));
  }

  public QueryServiceImpl(SubscriptionRepository subscriptionRepository, QueryRepository queryRepository, NamedQueryValidator namedQueryValidator, ContextBuilder contextBuilder, KeyValueRepository keyValueRepository, OrderByUtil orderByUtil, QueryParamUtil queryParamUtil, RepositoryConfiguration repositoryConfiguration) {
    this.subscriptionRepository = subscriptionRepository;
    this.queryRepository = queryRepository;
    this.namedQueryValidator = namedQueryValidator;
    this.contextBuilder = contextBuilder;
    this.keyValueRepository = keyValueRepository;
    this.orderByUtil = orderByUtil;
    this.queryParamUtil = queryParamUtil;
    this.repositoryConfiguration = repositoryConfiguration;
  }
}
