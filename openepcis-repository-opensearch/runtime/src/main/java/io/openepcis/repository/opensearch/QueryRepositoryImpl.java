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
package io.openepcis.repository.opensearch;

import static io.openepcis.model.epcis.constants.EpcisEventFieldConstants.*;
import static io.openepcis.model.epcis.exception.ExceptionMessages.*;
import static io.openepcis.repository.Constants.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openepcis.constants.EPCIS;
import io.openepcis.core.exception.ImplementationException;
import io.openepcis.core.exception.ResourceNotFoundException;
import io.openepcis.model.epcis.exception.QueryExecutionException;
import io.openepcis.core.model.PaginationSupport;
import io.openepcis.model.dto.*;
import io.openepcis.model.epcis.*;
import io.openepcis.model.epcis.constants.CommonConstants;
import io.openepcis.model.epcis.exception.*;
import io.openepcis.model.epcis.format.FormatPreference;
import io.openepcis.opentelemetry.logging.OpenEPCISLogger;
import io.openepcis.query.data.EpcisQueryType;
import io.openepcis.query.aggregation.EventAggregation;
import io.openepcis.query.aggregation.EventGroupBy;
import io.openepcis.query.builder.QueryBuilder;
import io.openepcis.query.data.SimpleEventQueryRequest;
import io.openepcis.query.factory.QueryFactory;
import io.openepcis.repository.Constants;
import io.openepcis.repository.api.QueryRepository;
import io.openepcis.repository.config.RepositoryConfiguration;
import io.openepcis.repository.model.*;
import io.openepcis.repository.opensearch.util.OpenSearchJSONUtil;
import io.openepcis.repository.opensearch.util.QueryConverterOpenSearchJC;
import io.openepcis.repository.opensearch.util.SearchSourceUtil;
import io.openepcis.repository.util.EPCFormatUtil;
import io.openepcis.repository.util.EventDocuments;
import io.openepcis.repository.util.RollingIndexUtil;
import io.openepcis.service.api.UserDetailsExtractor;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch._types.*;
import org.opensearch.client.opensearch._types.query_dsl.*;
import org.opensearch.client.opensearch.core.*;
import org.opensearch.client.opensearch.core.bulk.BulkOperation;
import org.opensearch.client.opensearch.core.bulk.IndexOperation;
import org.opensearch.client.opensearch.core.get.GetResult;
import org.opensearch.client.opensearch.core.mget.MultiGetResponseItem;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.indices.CreateIndexRequest;

@ApplicationScoped
public class QueryRepositoryImpl implements QueryRepository {
    private final ObjectMapper objectMapper;
    private final QueryFactory queryFactory;
    private final RepositoryConfiguration configService;
    // Context-aware client that automatically selects request-scoped or admin client based on CDI context
    private final ContextAwareOpenSearchClient contextAwareClient;
    private final PitPaginationProvider pitPaginationProvider;
    private final ManagedExecutor managedExecutor;
    private final UserDetailsExtractor userDetailsExtractor;
    private final QueryScope queryScope;
    private static final OpenEPCISLogger log = OpenEPCISLogger.getLogger(QueryRepositoryImpl.class);
    @Inject
    Instance<SecurityIdentity> securityIdentity;

    @Override
    public Uni<QueryResult> executeEventsQuery(SimpleEventQueryRequest request, Integer perPage, FormatPreference preference, boolean isSuperAdminClient, Optional<String> defaultGroup) {
        final SortOrder orderDirection = request.getOrderDirection() == null ? SortOrder.Desc : request.getOrderDirection().equalsIgnoreCase("DESC") ? SortOrder.Desc : SortOrder.Asc;
        final String orderBy = request.getOrderBy();
        final Integer maxEventCount = request.getMaxEventCount();
        final Integer eventCountLimit = request.getEventCountLimit();
        final Optional<String> group = defaultGroup.filter(g -> !g.isBlank());
        final List<io.openepcis.query.data.QueryParam> filters = queryScope.filters(request, group);
        final PitPaginationProvider.Context pitContext = pitPaginationProvider.createContext(Optional.of(perPage), eventIndexPattern(group));
        // a failure anywhere in the chain, or the client hanging up, closes a PIT a later page still owns
        return pitContext.guard(pitContext.newSearchRequest().chain(searchRequestBuilder -> {
            final QueryBuilder customQuery = queryFactory.createMainQuery(filters);
            final Query mainQuery = QueryConverterOpenSearchJC.convert(customQuery.build());
            searchRequestBuilder.query(mainQuery);
            if (Objects.nonNull(orderBy)) {
                if (Objects.nonNull(request.getOrderByUX())) {
                    buildNestedSort(request, orderDirection, searchRequestBuilder);
                } else {
                    searchRequestBuilder.sort(builder -> builder.field(fieldSort -> fieldSort.field(orderBy).order(orderDirection)));
                }
            } else {
                searchRequestBuilder.sort(builder -> builder.field(fieldSort -> fieldSort.field(Constants.EVENT_TIME).order(SortOrder.Desc)));
            }
            searchRequestBuilder.sort(builder -> builder.field(FieldSort.of(fieldSort -> fieldSort.field(EVENT_ID).order(SortOrder.Desc))));
            // the first N (eventCountLimit) has no next page: the context then opens no PIT and saves no token
            pitContext.limit(eventCountLimit);
            return isSuperAdminClient ? pitContext.searchResponse(EPCISEventES.class, contextAwareClient.getAsyncClient("admin")) : pitContext.searchResponse(EPCISEventES.class);
        }).map(searchResponse -> {
            requireAllShards(searchResponse.shards(), "the event query");
            if (maxEventCount != null) {
                final long totalEventCount = searchResponse.hits().total().value();
                if (totalEventCount > maxEventCount) throw new QueryTooLargeException(SEARCH_RESULT_SIZE_TOO_LARGE_NARROW_DOWN_THE_SEARCH_CRITERIA);
            }
            return searchResponse;
        }).chain(searchResponse -> responseContainer(pitContext, searchResponse, preference)));
    }

    private String eventIndexPattern(final Optional<String> group) {
        return queryScope.eventIndexPattern(configService.epcis().event().index(), group);
    }

    private static final String AGGREGATION = "groups";
    private static final String FALLBACK = "without_urn";

    /**
     * Counts what {@link #executeEventsQuery} would find, grouped: the same filters, the same
     * indices, the same client, so document-level security and the tenant filter decide what is
     * counted exactly as they decide what is listed. No hits are fetched (size 0) and the total is
     * counted exactly, not capped at 10,000.
     */
    @Override
    public Uni<EventAggregation> aggregateEvents(SimpleEventQueryRequest request, EventGroupBy groupBy, int size, boolean isSuperAdminClient, Optional<String> defaultGroup) {
        final Optional<String> group = defaultGroup.filter(g -> !g.isBlank());
        final SearchRequest searchRequest;
        try {
            final Query mainQuery = QueryConverterOpenSearchJC.convert(queryFactory.createMainQuery(queryScope.filters(request, group)).build());
            searchRequest = aggregationRequest(eventIndexPattern(group), mainQuery, groupBy, size);
        } catch (RuntimeException e) {
            return Uni.createFrom().failure(e);
        }
        final OpenSearchAsyncClient client = isSuperAdminClient ? contextAwareClient.getAsyncClient("admin") : contextAwareClient.getAsyncClient();
        return Uni.createFrom()
                .completionStage(() -> {
                    try {
                        return client.search(searchRequest, JsonData.class);
                    } catch (IOException e) {
                        return CompletableFuture.<SearchResponse<JsonData>>failedStage(e);
                    }
                })
                .onFailure().transform(PitPaginationProvider.TRANSPORT_EXCEPTION_MAPPER)
                .map(response -> {
                    requireAllShards(response.shards(), "the event aggregation");
                    return aggregationResult(response, groupBy, size);
                });
    }

    static SearchRequest aggregationRequest(final String index, final Query query, final EventGroupBy groupBy, final int size) {
        final SearchRequest.Builder builder = new SearchRequest.Builder()
                .index(index)
                .size(0)
                .trackTotalHits(t -> t.enabled(true))
                .query(query);
        if (groupBy.isHistogram()) {
            final org.opensearch.client.opensearch._types.aggregations.CalendarInterval interval =
                    groupBy == EventGroupBy.day
                            ? org.opensearch.client.opensearch._types.aggregations.CalendarInterval.Day
                            : org.opensearch.client.opensearch._types.aggregations.CalendarInterval.Month;
            builder.aggregations(AGGREGATION, a -> a.dateHistogram(h -> h
                    .field(groupBy.field())
                    .calendarInterval(interval)
                    .format(groupBy.dateFormat())
                    .timeZone("UTC")
                    .minDocCount(1)));
        } else {
            builder.aggregations(AGGREGATION, a -> a.terms(t -> t.field(groupBy.field()).size(size)));
            groupBy.fallbackField().ifPresent(fallback -> builder.aggregations(FALLBACK, a -> a
                    .filter(f -> f.bool(b -> b
                            .mustNot(n -> n.exists(e -> e.field(groupBy.field())))
                            .filter(x -> x.exists(e -> e.field(fallback)))))
                    .aggregations(AGGREGATION, sub -> sub.terms(t -> t.field(fallback).size(size)))));
        }
        return builder.build();
    }

    static EventAggregation aggregationResult(final SearchResponse<?> response, final EventGroupBy groupBy, final int size) {
        final long total = response.hits().total() == null ? 0L : response.hits().total().value();
        final org.opensearch.client.opensearch._types.aggregations.Aggregate aggregate = response.aggregations().get(AGGREGATION);
        if (groupBy.isHistogram()) {
            final List<EventAggregation.Bucket> buckets = new ArrayList<>();
            if (aggregate != null && aggregate.isDateHistogram()) {
                aggregate.dateHistogram().buckets().array().forEach(b -> buckets.add(new EventAggregation.Bucket(b.keyAsString(), b.docCount())));
            }
            return EventAggregation.histogram(groupBy, total, buckets);
        }
        final TermsBuckets primary = TermsBuckets.of(aggregate);
        TermsBuckets fallback = TermsBuckets.EMPTY;
        final org.opensearch.client.opensearch._types.aggregations.Aggregate withoutUrn = response.aggregations().get(FALLBACK);
        if (withoutUrn != null && withoutUrn.isFilter()) {
            fallback = TermsBuckets.of(withoutUrn.filter().aggregations().get(AGGREGATION));
        }
        return EventAggregation.terms(groupBy, total, primary.buckets(), primary.other(), fallback.buckets(), fallback.other(), size);
    }

    private record TermsBuckets(List<EventAggregation.Bucket> buckets, long other) {
        static final TermsBuckets EMPTY = new TermsBuckets(List.of(), 0L);

        /** String terms; an unmapped field (no index has it yet) is no buckets, not an error. */
        static TermsBuckets of(final org.opensearch.client.opensearch._types.aggregations.Aggregate aggregate) {
            if (aggregate == null || !aggregate.isSterms()) {
                return EMPTY;
            }
            final org.opensearch.client.opensearch._types.aggregations.StringTermsAggregate terms = aggregate.sterms();
            final List<EventAggregation.Bucket> buckets = terms.buckets().array().stream()
                    .map(b -> new EventAggregation.Bucket(b.key(), b.docCount()))
                    .toList();
            return new TermsBuckets(buckets, terms.sumOtherDocCount() == null ? 0L : terms.sumOtherDocCount());
        }
    }

    private Uni<QueryResult> responseContainer(final PitPaginationProvider.Context pitContext, final SearchResponse<EPCISEventES> searchResponse, final FormatPreference preference) {
        final QueryResult queryResult = new QueryResult();
        queryResult.setQueryResults(unnamedQueryResult(buildEventList(searchResponse, preference)));
        return pitContext.applyPaginationSupport(searchResponse, queryResult, EPCISEventES.class).map(r -> queryResult);
    }

    /**
     * The result of a query that has no name of its own: GET /events and the top-level resources.
     * EPCIS 2.0 requires queryName in every QueryResults, and an unnamed event query is a
     * SimpleEventQuery (8.2.7.1); a named query overwrites the name with its own.
     */
    static EpcisQueryResult unnamedQueryResult(final List<? extends EPCISEvent> events) {
        final EpcisQueryResult queryResults = new EpcisQueryResult();
        queryResults.setQueryName(EpcisQueryType.SIMPLE_EVENT_QUERY.getType());
        queryResults.setResultsBody(new QueryResultsBody(events));
        return queryResults;
    }

    private List<? extends EPCISEvent> buildEventList(SearchResponse<EPCISEventES> searchResponse, FormatPreference preference) {
        final List<Hit<EPCISEventES>> hits = searchResponse.hits().hits();
        if (CollectionUtils.isEmpty(hits)) {
            return Collections.emptyList();
        }
        final List<EPCISEvent> epcisEvents = new ArrayList<>();
        hits.forEach(hit -> epcisEvents.add(objectMapper.convertValue(hit.source(), EPCISEventES.class).getCoreModel(preference)));
        return epcisEvents;
    }

    private void buildNestedSort(SimpleEventQueryRequest request, SortOrder orderDirection, SearchRequest.Builder searchRequestBuilder) {
        final NestedSortValue nestedSortValue = NestedSortValue.of(nestedSortBuilder -> nestedSortBuilder.filter(QueryBuilders.term().field(request.getOrderByUX().getNestedPath() + "." + PATH).value(FieldValue.of(request.getOrderByUX().getPath())).build().toQuery()));
        searchRequestBuilder.sort(sortBuilder -> sortBuilder.field(FieldSort.of(fieldSort -> fieldSort.field(request.getOrderByUX().getNestedPath() + "." + request.getOrderByUX().getDataType()).order(orderDirection).mode(SortMode.Max).nested(nestedSortValue))));
    }

    /**
     * Refuse to report a partly failed search as a result.
     *
     * <p>OpenSearch answers a query whose shards failed with HTTP 200, the hits it
     * did manage, and a count of what went wrong. Reading only the hits turns "I
     * could not look" into "there is nothing" — the two most different answers a
     * search can give, told apart by a field nobody was reading.
     *
     * <p>That cost four months here. A catch-all index template had quietly
     * displaced the ones carrying the mappings, so {@code metadata} came out as an
     * object rather than nested; the document-level security rule is a nested query
     * on that field, and every search against those indices failed at the shard
     * level. The cluster said {@code query_shard_exception} on every single request.
     * The application said: no events.
     */
    // Package-private so the test in this package can hold it to its promise directly.
    static void requireAllShards(final ShardStatistics shards, final String what) {
        if (shards == null || shards.failed() == 0) {
            return;
        }
        final String reason = shards.failures().isEmpty() || shards.failures().get(0).reason() == null
                ? "no reason given"
                : shards.failures().get(0).reason().reason();
        throw new QueryExecutionException(String.format(
                "%s could not be answered: %d of %d shards failed (%s)",
                what, shards.failed(), shards.total(), reason));
    }

    private static final List<String> CAPTURE_JOB_FIELDS = List.of(CAPTURE_ID, RUNNING, SUCCESS, ERRORS, CREATED_AT, FINISHED_AT, CAPTURE_ERROR_BEHAVIOUR);

    /**
     * The capture job by its id. A client polls this right after capturing, so it reads by
     * document id first: a get is realtime, a search only sees the job after the next refresh,
     * which on a search-idle shard took up to 30 s. The job lives in
     * capture-job-[tenant]-[yyyy-MM] of its creation, so this month and the last one are asked.
     * Older jobs, or a tenant the caller cannot name, fall back to the search over all indices.
     */
    @Override
    public Uni<CaptureJob> getCaptureJobFromCaptureID(String captureID) {
        final List<String> indices;
        try {
            indices = captureJobIndices(configService.capture().job().index(), userDetailsExtractor.getDefaultGroup(securityIdentity), OffsetDateTime.now());
        } catch (RuntimeException e) {
            log.debug("no tenant for a realtime capture job read, searching instead: {}", e.getMessage());
            return searchCaptureJob(captureID);
        }
        return Uni.createFrom().completionStage(() -> {
            try {
                return contextAwareClient.getAsyncClient().mget(m -> {
                    m.sourceIncludes(CAPTURE_JOB_FIELDS);
                    indices.forEach(index -> m.docs(d -> d.index(index).id(captureID)));
                    return m;
                }, CaptureJob.class);
            } catch (IOException e) {
                throw new QueryExecutionException(e.getMessage(), e);
            }
        }).map(response -> response.docs().stream()
                .filter(MultiGetResponseItem::isResult)
                .map(MultiGetResponseItem::result)
                .filter(GetResult::found)
                .map(GetResult::source)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null))
          .onFailure().recoverWithItem(e -> {
              log.debug("realtime capture job read failed, searching instead: {}", e.getMessage());
              return null;
          })
          .chain(job -> job != null ? Uni.createFrom().item(job) : searchCaptureJob(captureID));
    }

    private static final List<String> COMPLETION_FIELDS = List.of("type", EVENT_ID, "metadata.capturedBy");

    /**
     * A capture job's events by their documents, for the job's completion: a realtime multi get,
     * routed to the shards that hold them, with only eventID and capturedBy loaded. Replaces a
     * count and a scroll search over every event index, both of which needed a refresh first.
     */
    @Override
    public Uni<Map<String, EPCISEventES>> getEventsByDocuments(final List<String> documents) {
        if (documents == null || documents.isEmpty()) {
            return Uni.createFrom().item(Map.of());
        }
        final List<EventDocuments.Location> locations = documents.stream().map(EventDocuments.Location::parse).toList();
        return Uni.createFrom().completionStage(() -> {
            try {
                return contextAwareClient.getAsyncClient().mget(m -> {
                    m.sourceIncludes(COMPLETION_FIELDS);
                    locations.forEach(at -> m.docs(d -> d.index(at.index()).id(at.id())));
                    return m;
                }, EPCISEventES.class);
            } catch (IOException e) {
                throw new QueryExecutionException(e.getMessage(), e);
            }
        }).map(response -> {
            final Map<String, EPCISEventES> found = new LinkedHashMap<>();
            response.docs().stream()
                    .filter(MultiGetResponseItem::isResult)
                    .map(MultiGetResponseItem::result)
                    .filter(GetResult::found)
                    .filter(r -> r.source() != null)
                    .forEach(r -> found.put(new EventDocuments.Location(r.index(), r.id()).toString(), r.source()));
            return found;
        });
    }

    static List<String> captureJobIndices(final String prefix, final String tenant, final OffsetDateTime now) {
        if (tenant == null || tenant.isBlank()) {
            throw new IllegalStateException("no tenant");
        }
        return List.of(
                RollingIndexUtil.getRollingIndexName(prefix, now, tenant),
                RollingIndexUtil.getRollingIndexName(prefix, now.minusMonths(1), tenant));
    }

    private Uni<CaptureJob> searchCaptureJob(String captureID) {
        final IdsQuery query = QueryBuilders.ids().values(captureID).build();
        final SearchRequest.Builder searchRequestBuilder = new SearchRequest.Builder();
        final List<String> includeFields = CAPTURE_JOB_FIELDS;
        final List<String> excludeFields = new ArrayList<>();
        // SPI automatically injects JWT auth headers for default client
        return Uni.createFrom().completionStage(managedExecutor.supplyAsync(() -> {
            searchRequestBuilder.index(configService.capture().job().index() + "*");
            searchRequestBuilder.source(s -> s.filter(f -> f.includes(includeFields).excludes(excludeFields)));
            searchRequestBuilder.query(query.toQuery());
            return searchRequestBuilder;
        })).chain(builder -> {
            try {
                return Uni.createFrom().completionStage(contextAwareClient.getAsyncClient().search(searchRequestBuilder.build(), CaptureJob.class));
            } catch (IOException e) {
                return Uni.createFrom().failure(e);
            }
        }).map(response -> {
            requireAllShards(response.shards(), "the capture job lookup");
            if (response.hits().total().value() == 0) {
                throw new ResourceNotFoundException(NO_CAPTURE_JOB_FOUND);
            }
            return response.hits().hits().stream().findFirst().get().source();
        }).onFailure().recoverWithUni(throwable -> Uni.createFrom().failure(throwable));
    }

    /**
     * Stores the named query to OpenSearch and returns the generated document id.
     *
     * @param namedQueryMetaData query details
     * @return new document id
     */
    @Override
    public Uni<String> saveEpcisQuery(final NamedQueryMetaData namedQueryMetaData) {
        try {
            // Extract default group and user ID from the security context.
            final String defaultGroup = userDetailsExtractor.getDefaultGroup(securityIdentity);
            final String userID = userDetailsExtractor.getUserId(securityIdentity);
            // Build the OpenSearch document from the provided named query metadata.
            final NamedQueryMetaDataES metaData = new NamedQueryMetaDataES(namedQueryMetaData);
            metaData.setDeleted(false);
            metaData.setMetadata(MetadataES.builder().capturedBy(List.of(CapturedByES.builder().timestamp(metaData.getCreatedAt()).userID(userID).defaultGroup(defaultGroup).build())).build());
            // Determine the index name based on configuration and default group.
            final String indexName = RollingIndexUtil.getIndexName(configService.epcis().query().index(), defaultGroup);
            final IndexRequest<NamedQueryMetaDataES> indexRequest = new IndexRequest.Builder<NamedQueryMetaDataES>().index(indexName).document(metaData).build();
            // invoke the method to perform the indexing operation with retry logic if index not found
            return performIndexWithRetry(indexName, indexRequest);
        } catch (Exception exception) {
            log.error("Unexpected error while saving EPCIS query: {}", exception.getMessage(), exception);
            return Uni.createFrom().failure(exception);
        }
    }

    @Override
    public Uni<String> indexStreamingPercolator(final String documentId, final String queryName, final SimpleEventQueryRequest simpleEventQueryRequest) {
        try {
            final io.openepcis.query.builder.QueryBuilder customQuery = queryFactory.createMainQuery(simpleEventQueryRequest.getFilters());
            final JsonNode queryNode = SearchSourceUtil.toJsonNode(customQuery.build());
            final String defaultGroup = userDetailsExtractor.getDefaultGroup(securityIdentity);
            final String userID = userDetailsExtractor.getUserId(securityIdentity);
            MetadataES metadataES = MetadataES.builder().capturedBy(List.of(CapturedByES.builder().userID(userID).defaultGroup(defaultGroup).build())).build();
            final JsonNode metadataNode = objectMapper.valueToTree(metadataES);
            final ObjectNode node = objectMapper.createObjectNode();
            node.putIfAbsent("query", queryNode);
            node.put("queryName", queryName);
            node.set("metadata", metadataNode);
            final String indexName = RollingIndexUtil.getIndexName(configService.epcis().streaming().subscription(), defaultGroup);
            final IndexRequest<ObjectNode> indexRequest = new IndexRequest.Builder<ObjectNode>().index(indexName).id(documentId).document(node).build();
            // invoke the method to perform the indexing operation with retry logic if index not found
            return performIndexWithRetry(indexName, indexRequest);
        } catch (Exception e) {
            log.error("Error while saving Percolator for epcis-query");
            return Uni.createFrom().failure(new PersistenceException("Error while saving Percolator for epcis-query " + queryName, e));
        }
    }

    // Common method to index document if index not found exception then create index and retry
    private <T> Uni<String> performIndexWithRetry(final String indexName, final IndexRequest<T> indexRequest) {
        try {
            // Initially try to index the document.
            return Uni.createFrom().completionStage(contextAwareClient.getAsyncClient().index(indexRequest)).map(indexResponse -> {
                log.info("Saved to ES: {} with id : {} ", indexName, indexResponse.id());
                return indexResponse.id();
            }).onFailure().recoverWithUni(throwable -> {
                log.debug("Initial indexing failed for index {}: {}", indexName, throwable.getMessage());
                // Check if the error is due to a OpenSearch Exception with missing index (HTTP 404).
                if (OpenSearchException.class.isAssignableFrom(throwable.getClass())) {
                    final OpenSearchException openSearchEx = (OpenSearchException) throwable;
                    if (openSearchEx.status() == 404) {
                        log.warn("Index {} not found. Attempting to create the index.", indexName);
                        try {
                            // Attempt to create the missing index
                            return 
                            // after the index is created, retry the indexing operation.
                            // Log and return the document id on successful indexing after index creation.
                            Uni.createFrom().completionStage(contextAwareClient.getAsyncClient().indices().create(b -> b.index(indexName))).chain(ignore -> {
                                try {
                                    return Uni.createFrom().completionStage(contextAwareClient.getAsyncClient().index(indexRequest));
                                } catch (IOException e) {
                                    return Uni.createFrom().failure(new PersistenceException("Error during the creation of index : " + indexName, e));
                                }
                            }).map(indexResponse -> {
                                log.debug("Successfully indexed document after creating index {} with id: {}", indexName, indexResponse.id());
                                return indexResponse.id();
                            }).onFailure().recoverWithUni(e -> {
                                // Log any error that occurs during index creation or the subsequent indexing retry.
                                log.error("Failed to create index {} and index document: {}", indexName, e.getMessage());
                                return Uni.createFrom().failure(new PersistenceException(ERROR_WHILE_SAVING_QUERY, e));
                            });
                        } catch (Exception e) {
                            return Uni.createFrom().failure(e);
                        }
                    }
                }
                return Uni.createFrom().failure(new PersistenceException("unable to create percolator for epcis-query ", throwable));
            });
        } catch (Exception e) {
            return Uni.createFrom().failure(new PersistenceException("Error during creation of index ", e));
        }
    }

    @Override
    public Uni<Boolean> isQueryExists(String name) {
        final String defaultGroup = userDetailsExtractor.getDefaultGroup(securityIdentity);
        final CountRequest countRequest = new CountRequest.Builder().index(configService.epcis().query().index() + "-" + defaultGroup).query(q -> q.bool(b -> b.filter(q1 -> q1.term(t1 -> t1.field(NAME).value(FieldValue.of(name)))).filter(q2 -> q2.term(t2 -> t2.field(DELETED).value(FieldValue.of(false)))))).build();
        return Uni.createFrom().completionStage(() -> {
            try {
                return contextAwareClient.getAsyncClient().count(countRequest);
            } catch (IOException e) {
                throw new PersistenceException(e.getMessage(), e);
            }
        }).map(countResponse -> {
            long count = countResponse.count();
            return count > 0;
        }).onFailure().recoverWithUni(throwable -> {
            if (OpenSearchException.class.isAssignableFrom(throwable.getClass())) {
                final OpenSearchException openSearchEx = (OpenSearchException) throwable;
                if (openSearchEx.status() == 404) {
                    return Uni.createFrom().item(Boolean.FALSE);
                }
            }
            return Uni.createFrom().failure(throwable);
        });
    }

    @Override
    public Uni<NamedQueryMetaData> getQueryByName(String queryName, boolean hasSuperAdminClientAccess) {
        return getEpcisQueryByName(queryName, hasSuperAdminClientAccess).chain(searchResponse -> {
            if (searchResponse.hits().total().value() == 0) {
                log.error(NO_QUERY_EXIST_WITH_NAME + queryName);
                return Uni.createFrom().failure(new NoSuchNameException(NO_QUERY_EXIST_WITH_NAME + queryName));
            }
            return Uni.createFrom().item(searchResponse.hits().hits().stream().findFirst().map(Hit::source).map(NamedQueryMetaDataES::getCoreModel).get()).onFailure().recoverWithUni(throwable -> Uni.createFrom().failure(throwable));
        }).onFailure().recoverWithUni(throwable -> {
            log.error(NO_QUERY_EXIST_WITH_NAME + queryName);
            return Uni.createFrom().failure(throwable);
        });
    }

    private Uni<SearchResponse<NamedQueryMetaDataES>> getEpcisQueryByName(String queryName, boolean hasSuperAdminClientAccess) {
        final BoolQuery query = QueryBuilders.bool().filter(q -> q.term(t -> t.field(NAME).value(FieldValue.of(queryName)))).filter(q2 -> q2.term(t2 -> t2.field(DELETED).value(FieldValue.of(false)))).build();
        final SearchRequest searchRequest = new SearchRequest.Builder().index(configService.epcis().query().index()).query(query.toQuery()).build();
        try {
            // SPI automatically injects JWT auth headers for default client (DLS support)
            // TODO: If hasSuperAdminClientAccess needs to bypass DLS, use named admin client
            return Uni.createFrom().completionStage(contextAwareClient.getAsyncClient().search(searchRequest, NamedQueryMetaDataES.class)).onFailure().recoverWithUni(throwable -> Uni.createFrom().failure(throwable));
        } catch (Exception e) {
            log.error(ERROR_WHILE_PROCESSING_QUERY, e);
            return Uni.createFrom().failure(e);
        }
    }

    @Override
    public Uni<NamedQueryMetaDataResult> getAllEpcisQuery(Integer perPage) {
        final NamedQueryMetaDataResult namedQueryMetaDataResult = new NamedQueryMetaDataResult();
        final PitPaginationProvider.Context pitContext = pitPaginationProvider.createContext(Optional.of(perPage), configService.epcis().query().index());
        return pitContext.newSearchRequest().chain(searchRequestBuilder -> {
            searchRequestBuilder.query(QueryBuilders.term().field(DELETED).value(FieldValue.of(false)).build().toQuery());
            return getUniSearchResponse(pitContext, NAME, NamedQueryMetaDataES.class);
        }).chain(searchResponse -> pitContext.applyPaginationSupport(searchResponse, namedQueryMetaDataResult, NamedQueryMetaDataES.class)).map(searchResponse -> {
            namedQueryMetaDataResult.setNamedQueryMetaDataList(OpenSearchJSONUtil.convert(searchResponse.hits().hits(), NamedQueryMetaData.class));
            return namedQueryMetaDataResult;
        }).onFailure().recoverWithUni(throwable -> {
            log.error(ERROR_WHILE_FETCHING_RESOURCE, throwable);
            return Uni.createFrom().failure(throwable);
        });
    }

    @Override
    public Uni<NamedQueryMetaDataResult> getAllEpcisQuery(String nextPageToken, Optional<Integer> perPage) {
        if (perPage != null && perPage.isPresent() && perPage.get() > configService.epcis().perPageMax()) {
            return Uni.createFrom().failure(new QueryTooLargeException(String.format("requested %d perPage, max %d is allowed", perPage.get(), configService.epcis().perPageMax())));
        }
        final NamedQueryMetaDataResult namedQueryMetaDataResult = new NamedQueryMetaDataResult();
        try {
            return getHitsFromNextPageToken(nextPageToken, perPage, namedQueryMetaDataResult, NamedQueryMetaDataES.class).map(searchHits -> {
                namedQueryMetaDataResult.setNamedQueryMetaDataList(OpenSearchJSONUtil.convert(searchHits, NamedQueryMetaData.class));
                return namedQueryMetaDataResult;
            });
        } catch (ResourceNotFoundException e) {
            log.info(e.getMessage());
            return Uni.createFrom().failure(e);
        } catch (Exception e) {
            log.error(ERROR_WHILE_FETCHING_NEXT_PAGE_RESOURCE, e);
            return Uni.createFrom().failure(e);
        }
    }

    private static final String RECORD_TIME_FIELD = "recordTime";

    @Override
    public Uni<EPCISEvent> getEventById(String eventId, String defaultGroup) {
        // The oldest record: the event itself, not a later error declaration of it.
        return getEventsById(eventId, defaultGroup).map(events -> events.get(0));
    }

    @Override
    public Uni<List<EPCISEvent>> getEventsById(String eventId, String defaultGroup) {
        final SearchRequest searchRequest = getSearchRequestForFetchEvents(eventId, defaultGroup);
        try {
            return Uni.createFrom().completionStage(contextAwareClient.getAsyncClient().search(searchRequest, EPCISEventES.class)).map(searchResponse -> {
                final List<EPCISEvent> events = searchResponse.hits().hits().stream().map(Hit::source).filter(Objects::nonNull).map(EPCISEventES::getCoreModel).toList();
                if (events.isEmpty()) {
                    throw new NoEventFoundException(String.format(ExceptionMessages.NO_EVENT_FOUND_WITH_EVENT_ID, eventId));
                }
                return events;
            });
        } catch (Exception e) {
            log.error(ERROR_WHILE_PROCESSING_QUERY, e);
            return Uni.createFrom().failure(e);
        }
    }

    @Override
    public Multi<StreamingSubscriptionQueryES> percolateEvent(EPCISEventES event, String defaultGroup) {
        final String indexName = configService.epcis().streaming().subscription() + "-" + defaultGroup;
        try {
            Query percolateQuery = QueryBuilders.bool().filter(f -> f.percolate(p -> p.index(indexName).field(EPCIS.QUERY).document(JsonData.of(event, new JacksonJsonpMapper(objectMapper))))).build().toQuery();
            SearchRequest.Builder searchRequestBuilder = new SearchRequest.Builder().index(indexName).query(percolateQuery);
            return scrollSearch(searchRequestBuilder, StreamingSubscriptionQueryES.class).onFailure().recoverWithMulti(throwable -> {
                log.warn(throwable.getMessage());
                try {
                    // Use fallback client for potential background thread context
                    final OpenSearchAsyncClient adminClient = contextAwareClient.getAsyncClient();
                    return Multi.createFrom().uni(
                    // if index doesn't exist create it
                    // keep original response from exists call
                    Uni.createFrom().completionStage(adminClient.indices().exists(b -> b.index(indexName))).chain(booleanResponse -> {
                        if (!booleanResponse.value()) {
                            try {
                                return Uni.createFrom().completionStage(adminClient.indices().create(builder -> builder.index(indexName))).map(response -> booleanResponse);
                            } catch (IOException e) {
                                log.error(e.getMessage(), e);
                            }
                        }
                        return Uni.createFrom().item(booleanResponse);
                    }).chain(booleanResponse -> {
                        // throw failure if index exists,
                        // then something else went wrong
                        if (booleanResponse.value()) {
                            return Uni.createFrom().failure(throwable);
                        }
                        return Uni.createFrom().nothing();
                    }));
                } catch (IOException e) {
                    log.error(e.getMessage(), e);
                    return Multi.createFrom().failure(e);
                }
            });
        } catch (Exception e) {
            log.error("Error while percolating on event with eventID: {}", event.getEventID());
            return Multi.createFrom().failure(e);
        }
    }

    public Multi<String> getEventIDsByCaptureID(String captureID) {
        Query captureIdNestedQuery = getNestedQueryWithCaptureId(captureID);
        final SearchRequest.Builder searchRequestBuilder = new SearchRequest.Builder().index(configService.epcis().event().index() + "*").query(captureIdNestedQuery);
        // Only what is news under this capture: an idempotent replay of what the tenant captured
        // before is counted under the captureID (it is in capturedBy), but its event-saved went
        // out with the first capture, and a second one would deliver it to subscribers again.
        return scrollSearch(searchRequestBuilder, EPCISEventES.class).filter(event -> EventDocuments.newInCapture(event, captureID)).map(EPCISEventES::getEventID);
    }

    private <T> Multi<T> scrollSearch(final SearchRequest.Builder searchRequestBuilder, Class<T> cls) {
        final SearchRequest searchRequest = searchRequestBuilder.scroll(Time.of(b -> b.time("10s"))).build();
        // Get client now while potentially in request context, fall back to admin for background threads
        final OpenSearchAsyncClient asyncClient = contextAwareClient.getAsyncClient();
        return Multi.createFrom().emitter(em -> {
            managedExecutor.runAsync(() -> {
                try {
                    final var future = asyncClient.search(searchRequest, cls);
                    final var response = future.get(10, TimeUnit.SECONDS);
                    final AtomicBoolean done = new AtomicBoolean(false);
                    response.hits().hits().stream().filter(Objects::nonNull).map(Hit::source).filter(Objects::nonNull).forEach(em::emit);
                    done.set(StringUtils.isEmpty(response.scrollId()));
                    while (!em.isCancelled() && !done.get()) {
                        final ScrollRequest scrollRequest = ScrollRequest.of(b -> b.scroll(Time.of(t -> t.time("10s"))).scrollId(response.scrollId()));
                        final var scrollResponseCompletableFuture = asyncClient.scroll(scrollRequest, cls);
                        final var scrollResponse = scrollResponseCompletableFuture.get(10, TimeUnit.SECONDS);
                        scrollResponse.hits().hits().stream().filter(Objects::nonNull).map(Hit::source).filter(Objects::nonNull).forEach(em::emit);
                        done.set(done.get() || em.isCancelled() || scrollResponse.hits().hits().isEmpty());
                        if (done.get()) {
                            final OpenSearchAsyncClient clearClient = asyncClient;
                            managedExecutor.runAsync(() -> {
                                try {
                                    clearClient.clearScroll(ClearScrollRequest.of(b -> b.scrollId(scrollRequest.scrollId()))).get(10, TimeUnit.SECONDS);
                                } catch (Exception ex) {
                                    log.error(ex.getMessage());
                                }
                            });
                        }
                    }
                    em.complete();
                } catch (Exception e) {
                    em.fail(e);
                }
            });
        });
    }

    @Override
    public Uni<List<EPCISEvent>> getEPCISEventByHash(String... setOfHash) {
        TermsQueryField termsQueryField = new TermsQueryField.Builder().value(Arrays.stream(setOfHash).map(FieldValue::of).toList()).build();
        final TermsQuery query = QueryBuilders.terms().field(HASH).terms(termsQueryField).build();
        SearchRequest searchRequest = new SearchRequest.Builder().index(configService.epcis().event().index() + "*").query(query.toQuery()).build();
        try {
            return Uni.createFrom().completionStage(contextAwareClient.getAsyncClient().search(searchRequest, EPCISEventES.class)).map(searchResponse -> searchResponse.hits().hits().stream().map(Hit::source).filter(Objects::nonNull).map(EPCISEventES::getCoreModel).toList());
        } catch (Exception e) {
            log.error(ERROR_WHILE_PROCESSING_QUERY, e);
            return Uni.createFrom().failure(new QueryExecutionException(e.getMessage(), e));
        }
    }

    @Override
    public Uni<QueryResult> getEventsFromNextPageToken(final String nextPageToken, final FormatPreference preference, final Optional<Integer> perPage) {
        final PitPaginationProvider.Context pitContext = pitPaginationProvider.loadContext(nextPageToken, perPage);
        return pitContext.guard(pitContext.newSearchRequest().chain(searchRequestBuilder -> pitContext.searchResponse(EPCISEventES.class))
            .chain(searchResponse -> responseContainer(pitContext, searchResponse, preference)));
    }

    @Override
    public Uni<QueryResult> getTopLevelFromNextPageToken(String nextPageToken, FormatPreference preference, Optional<Integer> perPage) {
        final PitPaginationProvider.Context pitContext = pitPaginationProvider.loadContext(nextPageToken, configService.epcis().topLevel().perPageMax(), Optional.of(perPage.orElse(configService.epcis().topLevel().perPageDefault())));
        return pitContext.guard(pitContext.newSearchRequest().chain(searchRequestBuilder -> pitContext.searchResponse(EPCISEventES.class))
            .chain(searchResponse -> responseContainer(pitContext, searchResponse, preference)));
    }

    @Override
    public Uni<String> getSessionIDFromSubscriptionID(String subscriptionID) {
        BoolQuery boolQuery = QueryBuilders.bool().filter(f -> f.term(t -> t.field(SUBSCRIPTION_ID).value(FieldValue.of(subscriptionID)))).build();
        SearchRequest searchRequest = new SearchRequest.Builder().index(configService.epcis().subscription().index() + "*").query(boolQuery.toQuery()).build();
        try {
            return Uni.createFrom().completionStage(contextAwareClient.getAsyncClient().search(searchRequest, NamedQuerySubscription.class)).map(searchResponse -> {
                if (searchResponse.hits().total().value() == 0) {
                    return StringUtils.EMPTY;
                }
                return searchResponse.hits().hits().stream().findFirst().get().source().getWsSessionID();
            });
        } catch (Exception e) {
            log.error(ERROR_WHILE_PROCESSING_QUERY, e);
            return Uni.createFrom().failure(new QueryExecutionException(e.getMessage()));
        }
    }

    @Override
    public Multi<NamedQuerySubscription> getNamedSubscriptionFromQueryName(String queryName, String defaultGroup) {
        final BoolQuery query = QueryBuilders.bool().filter(queryBuilder -> queryBuilder.term(TermQuery.of(termBuilder -> termBuilder.field(QUERY_NAME).value(FieldValue.of(queryName))))).filter(queryBuilder -> queryBuilder.term(TermQuery.of(termBuilder -> termBuilder.field(DELETED).value(FieldValue.of(false))))).build();
        final SearchRequest.Builder searchRequestBuilder = new SearchRequest.Builder().index(configService.epcis().subscription().index() + "-" + defaultGroup).query(query.toQuery()).sort(sortBuilder -> sortBuilder.field(FieldSort.of(fieldBuilder -> fieldBuilder.field(QUERY_NAME).order(SortOrder.Asc)))).sort(sortBuilder -> sortBuilder.field(FieldSort.of(fieldBuilder -> fieldBuilder.field(CREATED_AT).order(SortOrder.Desc))));
        return scrollSearch(searchRequestBuilder, NamedQuerySubscription.class);
    }

    @Override
    public Uni<Boolean> deleteStreamingQuery(String queryID) {
        final String defaultGroup = userDetailsExtractor.getDefaultGroup(securityIdentity);
        DeleteRequest deleteRequest = new DeleteRequest.Builder().index(configService.epcis().streaming().subscription() + "-" + defaultGroup).id(queryID).build();
        try {
            return Uni.createFrom().completionStage(contextAwareClient.getAsyncClient().delete(deleteRequest)).chain(deleteResponse -> {
                log.info("Deleted Percolator for query with queryID : {}", queryID);
                return Uni.createFrom().item(true);
            });
        } catch (IOException e) {
            return Uni.createFrom().failure(e);
        }
    }

    @Override
    public Uni<String> getIDByQueryName(String queryName) {
        return getEpcisQueryByName(queryName, false).map(searchResponse -> {
            if (searchResponse.hits().total().value() == 0) {
                log.error(NO_QUERY_EXIST_WITH_NAME + queryName);
                throw new NoSuchNameException(NO_QUERY_EXIST_WITH_NAME + queryName);
            }
            return searchResponse.hits().hits().get(0).id();
        }).onFailure().recoverWithUni(throwable -> Uni.createFrom().failure(throwable));
    }

    @Override
    public Uni<Boolean> deleteNamedQuery(String queryID) {
        try {
            final String defaultGroup = userDetailsExtractor.getDefaultGroup(securityIdentity);
            // SPI automatically injects JWT auth headers for default client
            return Uni.createFrom().completionStage(contextAwareClient.getAsyncClient().delete(DeleteRequest.of(b -> b.index(configService.epcis().query().index() + "-" + defaultGroup).id(queryID)))).chain(response -> {
                log.info("Query with id {} has been deleted successfully.", queryID);
                return Uni.createFrom().item(true);
            });
        } catch (IOException e) {
            return Uni.createFrom().failure(new QueryExecutionException("Error while deleting named query", e));
        }
    }

    @Override
    public Uni<List<String>> getSubscriptionIdsFromQueryName(String queryName) {
        final BoolQuery boolQuery = QueryBuilders.bool().filter(f1 -> f1.term(t1 -> t1.field(QUERY_NAME).value(FieldValue.of(queryName)))).build();
        final SearchRequest searchRequest = new SearchRequest.Builder().index(configService.epcis().subscription().index() + "*").query(boolQuery.toQuery()).build();
        try {
            // SPI automatically injects JWT auth headers for default client
            return Uni.createFrom().completionStage(contextAwareClient.getAsyncClient().search(searchRequest, NamedQuerySubscription.class)).map(searchResponse -> searchResponse.hits().hits().stream().map(Hit::source).filter(Objects::nonNull).map(namedQuerySubscription -> namedQuerySubscription.getSubscriptionID().toString()).toList());
        } catch (Exception e) {
            log.error(ERROR_WHILE_PROCESSING_QUERY, e);
            return Uni.createFrom().failure(new QueryExecutionException(e.getMessage()));
        }
    }

    @Override
    public Uni<EPCISEventES> getEPCISEventByDocument(String index, String documentId) {
        try {
            return Uni.createFrom().completionStage(contextAwareClient.getAsyncClient().get(g -> g.index(index).id(documentId), EPCISEventES.class))
                    .map(response -> response.found() ? response.source() : null);
        } catch (IOException e) {
            log.error(ERROR_WHILE_PROCESSING_QUERY, e);
            return Uni.createFrom().failure(new QueryExecutionException(e.getMessage(), e));
        }
    }

    @Override
    public Uni<EPCISEventES> getEPCISEventByEventID(String eventID) {
        final TermQuery query = QueryBuilders.term().field(EVENT_ID).value(FieldValue.of(eventID)).build();
        // Newest first. This is what the streaming subscriptions percolate after an event-saved
        // message, and an error declaration shares its eventID with the event it declares
        // erroneous: unsorted, a subscriber could get the old event again instead of the declaration.
        final SearchRequest searchRequest = new SearchRequest.Builder().index(configService.epcis().event().index() + "*").query(query.toQuery()).size(1).sort(s -> s.field(f -> f.field(RECORD_TIME_FIELD).order(SortOrder.Desc))).build();
        return Uni.createFrom().completionStage(() -> {
            try {
                return contextAwareClient.getAsyncClient().search(searchRequest, EPCISEventES.class);
            } catch (IOException e) {
                log.error(ERROR_WHILE_PROCESSING_QUERY, e);
                throw new QueryExecutionException(e.getMessage(), e);
            }
        }).map(searchResponse -> searchResponse.hits().hits().stream().map(Hit::source).filter(Objects::nonNull).findFirst().orElse(null)).onFailure().invoke(e -> {
            log.error(ERROR_WHILE_PROCESSING_QUERY, e);
            throw new QueryExecutionException(e.getMessage(), e);
        });
    }

    @Override
    public Uni<EPCISEvent> getEPCISEventByTypeAndID(String eventType, String eventID) {
        final BoolQuery boolQuery = QueryBuilders.bool().filter(f1 -> f1.term(t1 -> t1.field(TYPE).value(FieldValue.of(eventType)))).filter(f2 -> f2.term(t2 -> t2.field(EVENT_ID).value(FieldValue.of(eventID)))).build();
        final SearchRequest searchRequest = new SearchRequest.Builder().index(configService.epcis().event().index() + "*").query(boolQuery.toQuery()).build();
        try {
            return Uni.createFrom().completionStage(contextAwareClient.getAsyncClient().search(searchRequest, EPCISEventES.class)).map(response -> response.hits().hits().stream().map(Hit::source).filter(Objects::nonNull).map(EPCISEventES::getCoreModel).findFirst().orElseThrow(() -> new NoEventFoundException(String.format(ExceptionMessages.NO_EVENT_FOUND_WITH_EVENT_ID, eventID))));
        } catch (Exception e) {
            log.error(ERROR_WHILE_PROCESSING_QUERY, e);
            return Uni.createFrom().failure(new QueryExecutionException(e.getMessage(), e));
        }
    }

    @Override
    public void deletePIT(String pitID) {
        if (StringUtils.isNotBlank(pitID)) {
            try {
                final DeletePitRequest deletePitRequest = new DeletePitRequest.Builder().pitId(Collections.singletonList(pitID)).build();
                Uni.createFrom().completionStage(contextAwareClient.getAsyncClient().deletePit(deletePitRequest)).subscribe().with(deletePitResponse -> {
                    if (deletePitResponse.pits().get(0).successful()) {
                        log.info("Point-in-time search deleted successfully");
                    } else {
                        log.info("Failed to delete point-in-time search");
                    }
                });
            } catch (Exception e) {
                log.error("error while deleting PIT {}", e.getMessage());
            }
        }
    }

    @Override
    public void updateCaptureBehaviour(String captureID, boolean running, boolean success, List<InvalidEPCISEventInfo> errors, ZonedDateTime docCreationDate, String defaultGroup) {
        final CaptureJobES captureJob = new CaptureJobES();
        captureJob.setRunning(running);
        captureJob.setSuccess(success);
        captureJob.setErrors(errors);
        captureJob.setFinishedAt(OffsetDateTime.now());
        captureJob.populateDuration();
        try {
            Uni.createFrom().completionStage(contextAwareClient.getAsyncClient().update(u -> u.index(RollingIndexUtil.getRollingIndexName(configService.capture().job().index(), docCreationDate, defaultGroup)).id(captureID).doc(captureJob), CaptureJob.class)).onFailure().recoverWithUni(throwable -> Uni.createFrom().failure(throwable)).subscribe().with(item -> log.info("capture behaviour for captureID: {} is updated", captureID), throwable -> {
                log.error("error while updating capture behaviour", throwable);
                throw new QueryExecutionException("error while updating capture behaviour", throwable);
            });
        } catch (Exception e) {
            log.error("error while updating capture behaviour", e);
            throw new QueryExecutionException("error while updating capture behaviour", e);
        }
    }

    @Override
    public Uni<CaptureJobPageResult> getCaptureJobs(Integer perPage) {
        final CaptureJobPageResult captureJobPageResult = new CaptureJobPageResult();
        final PitPaginationProvider.Context pitContext = pitPaginationProvider.createContext(Optional.of(perPage), configService.capture().job().index() + "*");
        try {
            return pitContext.newSearchRequest().chain(searchRequestBuilder -> {
                searchRequestBuilder.query(QueryBuilders.matchAll().build().toQuery()).sort(SortOptions.of(b -> b.field(FieldSort.of(f -> f.field("createdAt").order(SortOrder.Desc)))));
                return getUniSearchResponse(pitContext, CAPTURE_ID, CaptureJob.class);
            }).chain(searchResponse -> pitContext.applyPaginationSupport(searchResponse, captureJobPageResult, CaptureJob.class)).map(searchResponse -> {
                captureJobPageResult.setCaptureJobs(searchResponse.hits().hits().stream().map(Hit::source).map(CaptureJob::of).toList());
                return captureJobPageResult;
            });
        } catch (Exception e) {
            log.error(ERROR_WHILE_FETCHING_RESOURCE, e);
            return Uni.createFrom().failure(new ImplementationException(ERROR_WHILE_FETCHING_RESOURCE, e));
        }
    }

    @Override
    public Uni<CaptureJobPageResult> getCaptureJobs(String nextPageToken, Optional<Integer> perPage) {
        if (perPage != null && perPage.isPresent() && perPage.get() > configService.epcis().perPageMax()) {
            return Uni.createFrom().failure(new QueryTooLargeException(String.format("requested %d perPage, max %d is allowed", perPage.get(), configService.epcis().perPageMax())));
        }
        final CaptureJobPageResult captureJobPageResult = new CaptureJobPageResult();
        try {
            return getHitsFromNextPageToken(nextPageToken, perPage, captureJobPageResult, CaptureJob.class).map(searchHits -> {
                captureJobPageResult.setCaptureJobs(searchHits.stream().map(Hit::source).map(CaptureJob::of).toList());
                return captureJobPageResult;
            });
        } catch (ResourceNotFoundException e) {
            log.info(e.getMessage());
            throw new ResourceNotFoundException(e.getMessage());
        } catch (Exception e) {
            log.error(ERROR_WHILE_FETCHING_NEXT_PAGE_RESOURCE, e);
            return Uni.createFrom().failure(new QueryExecutionException(ERROR_WHILE_FETCHING_NEXT_PAGE_RESOURCE, e));
        }
    }

    private <T> Uni<List<Hit<T>>> getHitsFromNextPageToken(final String nextPageToken, Optional<Integer> perPage, final PaginationSupport paginationSupport, Class<T> responseType) {
        final PitPaginationProvider.Context pitContext = pitPaginationProvider.loadContext(nextPageToken, perPage);
        return pitContext.newSearchRequest().chain(searchRequestBuilder -> {
            perPage.ifPresent(searchRequestBuilder::size);
            return pitContext.searchResponse(responseType);
        }).chain(searchResponse -> pitContext.applyPaginationSupport(searchResponse, paginationSupport, responseType).map(r -> r.hits().hits()));
    }

    @Override
    public Uni<EPCISEventES> getEPCISEventESFromHash(String[] arrayOfHashes) {
        TermsQueryField termsQueryField = new TermsQueryField.Builder().value(Arrays.stream(arrayOfHashes).map(FieldValue::of).toList()).build();
        // The event, not its error declaration: both have the same hash (CBV 8.9.2).
        SearchRequest searchRequest = new SearchRequest.Builder().index(configService.epcis().event().index() + "*").query(withoutDeclarations(termsQueryField)).build();
        try {
            // Use fallback client for Kafka Streams background thread context (duplicate check)
            return Uni.createFrom().completionStage(contextAwareClient.getAsyncClient().search(searchRequest, EPCISEventES.class)).map(response -> response.hits().hits().stream().map(Hit::source).filter(Objects::nonNull).findFirst().orElse(new EPCISEventES()));
        } catch (Exception e) {
            log.error(ERROR_WHILE_PROCESSING_QUERY, e);
            return Uni.createFrom().failure(new QueryExecutionException(e.getMessage(), e));
        }
    }

    /** The hash, on events only: an error declaration has the hash of the event it declares erroneous. */
    private static Query withoutDeclarations(final TermsQueryField hashes) {
        return Query.of(q -> q.bool(b -> b.filter(f -> f.terms(t -> t.field(HASH).terms(hashes))).mustNot(n -> n.exists(e -> e.field("errorDeclaration")))));
    }

    @Override
    public Uni<Boolean> updateEventMetaData(String hash, Map<String, Object> metadata, String captureId) {
        final TermsQueryField termsQueryField = new TermsQueryField.Builder().value(List.of(FieldValue.of(hash))).build();
        final SearchRequest searchRequest = new SearchRequest.Builder().index(configService.epcis().event().index()).query(withoutDeclarations(termsQueryField)).build();
        try {
            // Use fallback client for Kafka Streams background thread context (metadata update)
            return 
            // Update metadata, removing any duplicate flag and adding capturedBy details
            // Update the metadata in the EPCISEventES object
            // Build and execute the update request
            // No document found, return false as the operation result
            Uni.createFrom().completionStage(contextAwareClient.getAsyncClient().search(searchRequest, EPCISEventES.class)).map(response -> response.hits().hits().stream().filter(Objects::nonNull).findFirst()).chain(epcisEventESHit -> {
                if (epcisEventESHit.isPresent()) {
                    final Hit<EPCISEventES> documentHit = epcisEventESHit.get();
                    final String documentId = documentHit.id();
                    final String indexName = documentHit.index();
                    final EPCISEventES epcisEventES = documentHit.source();
                    metadata.remove(CommonConstants.IS_DUPLICATE);
                    if (EventDocuments.capturedIn(epcisEventES, captureId)) {
                        // a retry of this very update: the capture is listed already
                        return Uni.createFrom().item(true);
                    }
                    final CapturedByES capturedByES = objectMapper.convertValue(metadata.get(CommonConstants.CAPTURED_BY), CapturedByES.class);
                    if (capturedByES == null) {
                        // no capturer to add: the content is stored, that is all there is to say
                        return Uni.createFrom().item(true);
                    }
                    capturedByES.setCaptureID(captureId);
                    capturedByES.setTimestamp(ZonedDateTime.now().toOffsetDateTime());
                    if (epcisEventES.getMetadata() == null) {
                        epcisEventES.setMetadata(new MetadataES());
                    }
                    epcisEventES.getMetadata().setCapturedBy(epcisEventES.getMetadata().getCapturedBy() == null ? new ArrayList<>() : new ArrayList<>(epcisEventES.getMetadata().getCapturedBy()));
                    epcisEventES.getMetadata().getCapturedBy().add(capturedByES);
                    final UpdateRequest<EPCISEventES, EPCISEventES> updateRequest = new UpdateRequest.Builder<EPCISEventES, EPCISEventES>().index(indexName).id(documentId).doc(epcisEventES).build();
                    try {
                        return Uni.createFrom().completionStage(contextAwareClient.getAsyncClient().update(updateRequest, EPCISEventES.class)).map(updateResponse -> updateResponse.result() == Result.Updated);
                    } catch (IOException e) {
                        return Uni.createFrom().failure(new PersistenceException("Error while updating event metadata", e));
                    }
                } else {
                    log.warn("No event found for the captureID: {}", captureId);
                    return Uni.createFrom().item(false);
                }
            }).onFailure().recoverWithUni(throwable -> Uni.createFrom().failure(new PersistenceException(throwable.getMessage(), throwable)));
        } catch (Exception e) {
            log.error("Error while update event metadata for captureId: %", captureId, e);
            return Uni.createFrom().failure(new PersistenceException(e.getMessage(), e));
        }
    }

    @Override
    public Uni<List<EPCISEvent>> getTenantEventsByHash(String tenant, String hash) {
        if (StringUtils.isBlank(tenant)) {
            return getEPCISEventByHash(hash);
        }
        final SearchRequest searchRequest = new SearchRequest.Builder().index(configService.epcis().event().index() + "*").size(100)
                .query(q -> q.bool(b -> b.filter(f -> f.term(t -> t.field(HASH).value(FieldValue.of(hash)))).filter(tenantFilter(tenant)))).build();
        try {
            return Uni.createFrom().completionStage(contextAwareClient.getAsyncClient().search(searchRequest, EPCISEventES.class)).map(searchResponse -> searchResponse.hits().hits().stream().map(Hit::source).filter(Objects::nonNull).map(EPCISEventES::getCoreModel).toList());
        } catch (Exception e) {
            log.error(ERROR_WHILE_PROCESSING_QUERY, e);
            return Uni.createFrom().failure(new QueryExecutionException(e.getMessage(), e));
        }
    }

    @Override
    public Uni<Set<String>> getTenantEventIDs(String tenant, List<String> eventIDs) {
        if (StringUtils.isBlank(tenant)) {
            return getEventIDs(eventIDs);
        }
        final TermsQueryField termsQueryField = new TermsQueryField.Builder().value(eventIDs.stream().map(FieldValue::of).toList()).build();
        final SearchRequest searchRequest = new SearchRequest.Builder().index(configService.epcis().event().index() + "*").size(Math.max(10, eventIDs.size() * 2))
                .query(q -> q.bool(b -> b.filter(f -> f.terms(t -> t.field(EVENT_ID).terms(termsQueryField))).filter(tenantFilter(tenant)))).build();
        try {
            return Uni.createFrom().completionStage(contextAwareClient.getAsyncClient().search(searchRequest, EPCISEventES.class)).map(response -> response.hits().hits().stream().map(Hit::source).filter(Objects::nonNull).map(EPCISEventES::getEventID).collect(Collectors.toSet()));
        } catch (Exception e) {
            log.error(ERROR_WHILE_PROCESSING_QUERY, e);
            return Uni.createFrom().failure(new QueryExecutionException(e.getMessage(), e));
        }
    }

    /** Events this tenant captured: the same nested term the tenant's DLS rule is built on. */
    static Query tenantFilter(String tenant) {
        return Query.of(q -> q.nested(n -> n.path("metadata").query(i -> i.term(t -> t.field("metadata.capturedBy.defaultGroup").value(FieldValue.of(tenant))))));
    }

    @Override
    public Uni<Set<String>> getEventIDs(List<String> eventIDs) {
        try {
            TermsQueryField termsQueryField = new TermsQueryField.Builder().value(eventIDs.stream().map(FieldValue::of).toList()).build();
            SearchRequest searchRequest = new SearchRequest.Builder().index(configService.epcis().event().index() + "*").query(q -> q.terms(t -> t.field(EVENT_ID).terms(termsQueryField))).build();
            return Uni.createFrom().completionStage(contextAwareClient.getAsyncClient().search(searchRequest, EPCISEventES.class)).map(response -> response.hits().hits().stream().map(Hit::source).filter(Objects::nonNull).map(EPCISEventES::getEventID).collect(Collectors.toSet()));
        } catch (Exception e) {
            log.error(ERROR_WHILE_PROCESSING_QUERY, e);
            throw new QueryExecutionException(e.getMessage());
        }
    }

    @Override
    public Uni<Set<UserExtensionField>> getAllUserExtensionFields() {
        final PitPaginationProvider.Context pitContext = pitPaginationProvider.createContext(configService.user().extension().fields().index());
        return pitContext.newSearchRequest().chain(searchRequestBuilder -> {
            searchRequestBuilder.query(QueryBuilders.matchAll().build().toQuery()).size(configService.epcis().perPageDefault()).sort(builder -> builder.field(fieldSortBuilder -> fieldSortBuilder.field(CREATED_AT).order(SortOrder.Desc)));
            try {
                return pitContext.searchResponse(UserExtensionField.class, contextAwareClient.getAsyncClient("admin"));
            } catch (Exception e) {
                log.error(ERROR_WHILE_PROCESSING_QUERY, e);
                return Uni.createFrom().failure(new QueryExecutionException(e.getMessage(), e));
            }
        }).map(searchResponse -> collectUserExtensionFields(searchResponse.hits().hits())).onFailure().recoverWithUni(throwable -> {
            log.error(ERROR_WHILE_PROCESSING_QUERY, throwable);
            return Uni.createFrom().failure(new PersistenceException(throwable.getMessage(), throwable));
        });
    }

    private Set<UserExtensionField> collectUserExtensionFields(List<Hit<UserExtensionField>> searchHits) {
        return searchHits.stream().map(h -> objectMapper.convertValue(h.fields(), UserExtensionField.class)).collect(Collectors.toSet());
    }

    @Override
    public Uni<Optional<UserExtensionField>> getUserExtensionField(String name) {
        final BoolQuery boolQuery = QueryBuilders.bool().filter(f -> f.term(t -> t.field(NAME).value(FieldValue.of(name)))).build();
        SearchRequest searchRequest = new SearchRequest.Builder().index(configService.user().extension().fields().index()).query(boolQuery.toQuery()).build();
        try {
            return Uni.createFrom().completionStage(contextAwareClient.getAsyncClient().search(searchRequest, UserExtensionField.class)).map(response -> response.hits().hits().stream().findFirst().map(Hit::source));
        } catch (Exception e) {
            log.error(ERROR_WHILE_PROCESSING_QUERY, e);
            return Uni.createFrom().failure(new QueryExecutionException(e.getMessage()));
        }
    }

    private <T> Uni<SearchResponse<T>> getUniSearchResponse(PitPaginationProvider.Context pitContext, String name, Class<T> responseType) {
        pitContext.searchRequestBuilder().sort(List.of(SortOptions.of(b -> b.field(FieldSort.of(builder -> builder.field(name).order(SortOrder.Asc)))), SortOptions.of(b -> b.field(FieldSort.of(builder -> builder.field(CREATED_AT).order(SortOrder.Desc)))))).size(pitContext.perPage());
        return pitContext.searchResponse(responseType);
    }

    @Override
    public Uni<Long> getEventCountByCaptureID(String captureID) throws IOException {
        Query captureIdNestedQuery = getNestedQueryWithCaptureId(captureID);
        final CountRequest countRequest = new CountRequest.Builder().index(configService.epcis().event().index() + "*").query(captureIdNestedQuery).build();
        // Use fallback client for Kafka Streams background thread context
        return Uni.createFrom().completionStage(contextAwareClient.getAsyncClient().count(countRequest)).map(countResponse -> {
            // The visibility step decides on this number whether a capture's events
            // are all present. A short count from failed shards used to read as
            // "not yet" and, once the retries ran out, was swallowed entirely.
            requireAllShards(countResponse.shards(), "the event count for a capture");
            return countResponse.count();
        });
    }

    @Override
    public Uni<QueryResult> getTopLevelResource(Optional<Integer> optionalPerPage, String fieldName, FormatPreference formatPreference) {
        final PitPaginationProvider.Context pitContext = pitPaginationProvider.createContext(configService.epcis().topLevel().perPageMax(), Optional.of(optionalPerPage.orElse(configService.epcis().topLevel().perPageDefault())), configService.epcis().event().index() + "*");
        return pitContext.guard(pitContext.newSearchRequest().chain(searchRequestBuilder -> {
            configureSearchRequest(searchRequestBuilder, optionalPerPage.orElse(configService.epcis().perPageDefault()), fieldName);
            return executeSearch(pitContext, EPCISEventES.class);
        }).chain(searchResponse -> responseContainer(pitContext, searchResponse, formatPreference)));
    }

    @Override
    public Uni<QueryResult> getTopLevelResourceFromNextPageToken(String nextPageToken, Optional<Integer> perPage, String fieldName, FormatPreference formatPreference) {
        final PitPaginationProvider.Context pitContext = pitPaginationProvider.loadContext(nextPageToken, configService.epcis().topLevel().perPageMax(), Optional.of(perPage.orElse(configService.epcis().topLevel().perPageDefault())));
        return pitContext.guard(pitContext.newSearchRequest().chain(searchRequestBuilder -> executeSearch(pitContext, EPCISEventES.class)).chain(searchResponse -> responseContainer(pitContext, searchResponse, formatPreference)));
    }

    @Override
    public Uni<Boolean> saveEpcs(Map<String, EpcES> epcHashMap) {
        if (epcHashMap == null || epcHashMap.isEmpty()) {
            log.warn("EPC HashMap is null or empty");
            return Uni.createFrom().item(false);
        }
        // must be optimized: existing EPCs have to be searched for and updated
        /*
           Multi.createFrom().items(epcHashMap.values().stream()).group().intoLists().of(1000).collect().asList().chain(epc -> {
             final BoolQuery boolQuery =
                     QueryBuilders.bool()
                             .filter(f -> f.terms(t -> t.field("canonicalDL").terms(tt -> tt.value(epcHashMap.values().stream().map(e -> FieldValue.of(e.getCanonicalDL())).toList()))))
                             .build();
             SearchRequest searchRequest =
                     new SearchRequest.Builder()
                             .index(configService.epc().index())
                             .size(1000)
                             .query(boolQuery.toQuery())
                             .build();
             try {
               return Uni.createFrom()
                       .completionStage(contextAwareClient.getAsyncClient().search(searchRequest, EpcES.class))
                       .map(response -> response.hits().hits().stream().findFirst().map(Hit::source));
             } catch (IOException e) {
               return Uni.createFrom().failure(e);
             }
           }).subscribe().with(epc ->{ System.out.println(epc);}, throwable -> {});
    */
        try {
            final String indexName = RollingIndexUtil.getRollingIndexName(configService.epc().index(), Instant.now());
            List<BulkOperation> bulkOperations = epcHashMap.entrySet().stream().map(entry -> BulkOperation.of(builder -> builder.index(IndexOperation.of(indexOperationBuilder -> indexOperationBuilder.index(indexName).document(entry.getValue()).id(entry.getKey()))))).collect(Collectors.toList());
            BulkRequest bulkRequest = BulkRequest.of(builder -> builder.operations(bulkOperations));
            return Uni.createFrom().completionStage(contextAwareClient.getAsyncClient().bulk(bulkRequest)).onFailure().recoverWithUni(throwable -> {
                log.error(ERROR_WHILE_SAVING_EPC, throwable);
                return Uni.createFrom().failure(new PersistenceException(ERROR_WHILE_SAVING_EPC, throwable));
            }).invoke(bulkResponse -> log.debug("Saved {} EPCs in index {}", bulkResponse.items().size(), indexName)).replaceWith(true);
        } catch (Exception e) {
            log.error("Unexpected error while saving EPCs", e);
            return Uni.createFrom().failure(new PersistenceException("Unexpected error while saving EPCs", e));
        }
    }

    @Override
    public Uni<EPCPageResult> getEPCFromNextPageToken(String nextPageToken, Optional<Integer> perPage, FormatPreference formatPreference) {
        final PaginationSupport paginationResult = new PaginationSupport();
        final PitPaginationProvider.Context pitContext = pitPaginationProvider.loadContext(nextPageToken, configService.epcis().topLevel().perPageMax(), Optional.of(perPage.orElse(configService.epcis().topLevel().perPageDefault())));
        return pitContext.newSearchRequest().chain(searchRequestBuilder -> executeSearch(pitContext, EpcES.class)).chain(searchResponse -> pitContext.applyPaginationSupport(searchResponse, paginationResult, EpcES.class)).map(searchHits -> searchHits.hits().hits().stream().map(Hit::source).map(epcES -> EPCFormatUtil.getEpcInExpectedFormat(epcES, formatPreference.getEpcFormat().getEPCFormat())).toList()).map(epcs -> createTopLevelResourcePageResult(epcs, paginationResult));
    }

    @Override
    public Uni<EPCPageResult> getEPC(Optional<Integer> optionalPerPage, FormatPreference formatPreference) {
        final PaginationSupport paginationResult = new PaginationSupport();
        final PitPaginationProvider.Context pitContext = pitPaginationProvider.createContext(configService.epcis().topLevel().perPageMax(), Optional.of(optionalPerPage.orElse(configService.epcis().topLevel().perPageDefault())), configService.epc().index());
        return pitContext.newSearchRequest().chain(searchRequestBuilder -> {
            searchRequestBuilder.query(q -> q.matchAll(new MatchAllQuery.Builder().build())).sort(SortOptions.of(b -> b.field(FieldSort.of(f -> f.field(AS_CAPTURED).order(SortOrder.Desc))))).size(optionalPerPage.orElse(configService.epcis().perPageDefault()));
            return executeSearch(pitContext, EpcES.class);
        }).chain(searchResponse -> pitContext.applyPaginationSupport(searchResponse, paginationResult, EpcES.class)).map(searchHits -> searchHits.hits().hits().stream().map(Hit::source).map(epcES -> EPCFormatUtil.getEpcInExpectedFormat(epcES, formatPreference.getEpcFormat().getEPCFormat())).toList()).map(epcs -> createTopLevelResourcePageResult(epcs, paginationResult));
    }

    private EPCPageResult createTopLevelResourcePageResult(List<String> values, PaginationSupport paginationResult) {
        final EPCPageResult topLevelResource = new EPCPageResult();
        topLevelResource.setRel(paginationResult.getRel());
        topLevelResource.setNextPageToken(paginationResult.getNextPageToken());
        topLevelResource.setTokenExpiryTime(paginationResult.getTokenExpiryTime());
        topLevelResource.getMember().addAll(values);
        return topLevelResource;
    }

    private void configureSearchRequest(SearchRequest.Builder searchRequestBuilder, int size, String fieldName) {
        searchRequestBuilder.query(q -> q.matchAll(new MatchAllQuery.Builder().build())).collapse(c -> c.field(fieldName)).size(size);
    }

    private <T> Uni<SearchResponse<T>> executeSearch(PitPaginationProvider.Context pitContext, Class<T> clazz, OpenSearchAsyncClient superUserAsyncClient) {
        try {
            return (superUserAsyncClient == null) ? pitContext.searchResponse(clazz) : pitContext.searchResponse(clazz, superUserAsyncClient);
        } catch (Exception e) {
            log.error(ERROR_WHILE_PROCESSING_QUERY, e);
            return Uni.createFrom().failure(new QueryExecutionException("Failed to execute search query", e));
        }
    }

    private <T> Uni<SearchResponse<T>> executeSearch(PitPaginationProvider.Context pitContext, Class<T> clazz) {
        return executeSearch(pitContext, clazz, null);
    }

    private static Query getNestedQueryWithCaptureId(String captureID) {
        TermsQueryField captureIdTermsQuery = new TermsQueryField.Builder().value(Collections.singletonList(FieldValue.of(captureID))).build();
        return new Query.Builder().nested(n -> n.path(METADATA).query(q -> q.terms(t -> t.field(METADATA_CAPTURE_ID).terms(captureIdTermsQuery)))).build();
    }

    /**
     * One event by its ID, as the event query reads it: across every event index, through the
     * caller's own client, so what the caller may read is decided by the same document level
     * security that answers {@code GET /events}. The capturer's group is no filter: an event another
     * group captured and the caller may read (shared with it, or one it holds) is found here too,
     * as it is in the query.
     */
    SearchRequest getSearchRequestForFetchEvents(String eventId, String defaultGroup) {
        TermsQueryField termsQueryForVisible = new TermsQueryField.Builder().value(List.of(FieldValue.of(true))).build();
        NestedQuery nestedQueryForVisible = QueryBuilders.nested().path("metadata").query(q -> q.terms(t -> t.field("metadata.visible").terms(termsQueryForVisible))).build();
        final BoolQuery query = QueryBuilders.bool().must(builder -> builder.nested(nestedQueryForVisible)).must(q -> q.term(t -> t.field(EVENT_ID).value(FieldValue.of(eventId)))).build();
        // Oldest first, so the event comes before any error declaration of it. Without a sort the
        // two came back in whatever order the shards answered.
        final SearchRequest searchRequest = new SearchRequest.Builder().index(configService.epcis().event().index() + "*").query(query.toQuery()).size(100).sort(s -> s.field(f -> f.field(RECORD_TIME_FIELD).order(SortOrder.Asc))).build();
        return searchRequest;
    }

    public QueryRepositoryImpl(ObjectMapper objectMapper, QueryFactory queryFactory, RepositoryConfiguration configService, ContextAwareOpenSearchClient contextAwareClient, PitPaginationProvider pitPaginationProvider, ManagedExecutor managedExecutor, UserDetailsExtractor userDetailsExtractor) {
        this(objectMapper, queryFactory, configService, contextAwareClient, pitPaginationProvider, managedExecutor, userDetailsExtractor, new AllEventsQueryScope());
    }

    @Inject
    public QueryRepositoryImpl(ObjectMapper objectMapper, QueryFactory queryFactory, RepositoryConfiguration configService, ContextAwareOpenSearchClient contextAwareClient, PitPaginationProvider pitPaginationProvider, ManagedExecutor managedExecutor, UserDetailsExtractor userDetailsExtractor, QueryScope queryScope) {
        this.queryScope = queryScope;
        this.objectMapper = objectMapper;
        this.queryFactory = queryFactory;
        this.configService = configService;
        this.contextAwareClient = contextAwareClient;
        this.pitPaginationProvider = pitPaginationProvider;
        this.managedExecutor = managedExecutor;
        this.userDetailsExtractor = userDetailsExtractor;
    }
}
