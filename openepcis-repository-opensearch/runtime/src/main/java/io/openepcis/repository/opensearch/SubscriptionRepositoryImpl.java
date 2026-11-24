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

import static io.openepcis.model.epcis.constants.EpcisEventFieldConstants.CREATED_AT;
import static io.openepcis.model.epcis.exception.ExceptionMessages.*;
import static io.openepcis.repository.Constants.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openepcis.core.exception.ImplementationException;
import io.openepcis.core.exception.ResourceNotFoundException;
import io.openepcis.core.model.PaginationSupport;
import io.openepcis.model.dto.QuerySubscription;
import io.openepcis.model.dto.QuerySubscriptionResults;
import io.openepcis.model.epcis.NamedQuerySubscription;
import io.openepcis.model.epcis.exception.ExceptionMessages;
import io.openepcis.model.epcis.exception.NoSubscriptionFoundException;
import io.openepcis.model.epcis.exception.PersistenceException;
import io.openepcis.model.epcis.exception.QueryExecutionException;
import io.openepcis.opentelemetry.logging.OpenEPCISLogger;
import io.openepcis.query.context.SubscriptionContext;
import io.openepcis.repository.api.SubscriptionRepository;
import io.openepcis.repository.config.RepositoryConfiguration;
import io.openepcis.repository.model.*;
import io.openepcis.repository.opensearch.util.OpenSearchJSONUtil;
import io.openepcis.repository.util.RollingIndexUtil;
import io.openepcis.service.api.UserDetailsExtractor;
import io.quarkiverse.opensearch.OpenSearchClientName;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.stream.JsonGenerator;
import java.io.IOException;
import java.io.StringWriter;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import org.opensearch.client.json.jackson.JacksonJsonProvider;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch._types.FieldSort;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.Refresh;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.IdsQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.QueryBuilders;
import org.opensearch.client.opensearch._types.query_dsl.TermQuery;
import org.opensearch.client.opensearch.core.*;
import org.opensearch.client.opensearch.core.bulk.BulkOperation;
import org.opensearch.client.opensearch.core.bulk.DeleteOperation;
import org.opensearch.client.opensearch.core.search.Hit;

@ApplicationScoped
public class SubscriptionRepositoryImpl implements SubscriptionRepository {
  private final RepositoryConfiguration configService;
  private final PitPaginationProvider pitPaginationProvider;
  private final UserDetailsExtractor userDetailsExtractor;
  private final ObjectMapper objectMapper;
  private static final OpenEPCISLogger log = OpenEPCISLogger.getLogger(SubscriptionRepositoryImpl.class);
  private final OpenSearchConfig openSearchConfig;
  // Context-aware client that automatically selects request-scoped or admin client based on CDI context
  private final ContextAwareOpenSearchClient contextAwareClient;
  // Named "admin" client for subscription write operations (bypasses DLS)
  @Inject
  @OpenSearchClientName("admin")
  OpenSearchAsyncClient superUserAsyncClient;
  @Inject
  SecurityIdentity securityIdentity;

  @Override
  public Uni<UUID> subscribeNamedQuery(NamedQuerySubscription namedQuerySubscription, SubscriptionContext subscriptionContext) {
    final NamedQuerySubscriptionES namedQuerySubscriptionES = new NamedQuerySubscriptionES(namedQuerySubscription);
    String userID = subscriptionContext.getUserId();
    namedQuerySubscriptionES.setMetadata(MetadataES.builder().capturedBy(List.of(CapturedByES.builder().timestamp(namedQuerySubscription.getCreatedAt()).userID(userID).defaultGroup(subscriptionContext.getDefaultGroup()).build())).build());
    final IndexRequest<NamedQuerySubscriptionES> request = new IndexRequest.Builder<NamedQuerySubscriptionES>().index(RollingIndexUtil.getIndexName(configService.epcis().subscription().index(), subscriptionContext.getDefaultGroup())).id(namedQuerySubscription.getSubscriptionID().toString()).document(namedQuerySubscriptionES).build();
    try {
      return Uni.createFrom().completionStage(superUserAsyncClient.index(request)).map(indexResponse -> {
        log.info("Query Saved in ES: {} with id : {} ", configService.epcis().subscription().index(), indexResponse.id());
        return namedQuerySubscription.getSubscriptionID();
      }).onFailure().recoverWithUni(throwable -> {
        log.error(ERROR_WHILE_SAVING_QUERY, throwable);
        return Uni.createFrom().failure(throwable);
      });
    } catch (Exception exception) {
      log.error(ERROR_WHILE_SAVING_QUERY, exception);
      return Uni.createFrom().failure(exception);
    }
  }

  @Override
  public Uni<String> getSubscriptionIdFromSessionId(String wsSessionID) {
    BoolQuery boolQuery = QueryBuilders.bool().filter(f -> f.term(t -> t.field("wsSessionID").value(FieldValue.of(wsSessionID)))).build();
    SearchRequest searchRequest = new SearchRequest.Builder().index(configService.epcis().subscription().index() + "*").query(Query.of(q -> q.bool(boolQuery))).build();
    try {
      return Uni.createFrom().completionStage(superUserAsyncClient.search(searchRequest, NamedQuerySubscription.class)).map(searchResponse -> {
        if (searchResponse.hits().total().value() == 0) {
          return StringUtils.EMPTY;
        }
        return searchResponse.hits().hits().stream().findFirst().get().source().getSubscriptionID().toString();
      });
    } catch (Exception e) {
      log.error(ERROR_WHILE_PROCESSING_QUERY, e);
      return Uni.createFrom().failure(new QueryExecutionException(e.getMessage()));
    }
  }

  @Override
  public Uni<NamedQuerySubscription> getSubscriptionFromSubscriptionID(String subscriptionID) {
    return getQuerySubscriptionByID(subscriptionID).onFailure().recoverWithUni(throwable -> {
      log.error("Subscription not found for subscriptionID: {}", subscriptionID);
      return Uni.createFrom().failure(throwable);
    });
  }

  @Override
  public Uni<Boolean> deleteSubscription(List<String> subscriptionIDs, SubscriptionContext subscriptionContext) {
    final String defaultGroup = (subscriptionContext == null) ? userDetailsExtractor.getDefaultGroup(securityIdentity) : subscriptionContext.getDefaultGroup();
    if (subscriptionIDs == null || subscriptionIDs.isEmpty()) {
      return Uni.createFrom().item(true);
    }
    final List<BulkOperation> ops = subscriptionIDs.stream().map(id -> new BulkOperation.Builder().delete(DeleteOperation.of(uo -> uo.id(id).index(configService.epcis().subscription().index() + "-" + defaultGroup))).build()).toList();
    final BulkRequest.Builder bulkReq = new BulkRequest.Builder().operations(ops).refresh(Refresh.WaitFor);
    try {
      return Uni.createFrom().completionStage(superUserAsyncClient.bulk(bulkReq.build())).chain(response -> Uni.createFrom().item(!response.errors()));
    } catch (IOException e) {
      return Uni.createFrom().failure(e);
    }
  }

  @Override
  public Uni<Boolean> deleteSubscription(List<String> subscriptionIDs) {
    return deleteSubscription(subscriptionIDs, null);
  }

  @Override
  public Uni<QuerySubscription> getSubscriptionEventFromSubscriptionID(String subscriptionID) {
    final BoolQuery query = QueryBuilders.bool().filter(q1 -> q1.term(t1 -> t1.field(SUBSCRIPTION_ID).value(FieldValue.of(subscriptionID)))).build();
    SearchRequest searchRequest = new SearchRequest.Builder().index(configService.epcis().subscription().index() + "*").query(Query.of(q -> q.bool(query))).build();
    StringWriter writer = new StringWriter();
    JsonGenerator generator = new JacksonJsonProvider().createGenerator(writer);
    query.serialize(generator, new JacksonJsonpMapper(objectMapper));
    generator.flush();
    String queryString = writer.toString();
    log.info(queryString);
    try {
      // SPI automatically injects JWT auth headers for default client
      return Uni.createFrom().completionStage(contextAwareClient.getAsyncClient().search(searchRequest, QuerySubscription.class)).map(searchResponse -> {
        if (searchResponse.hits().total().value() == 0) throw new NoSubscriptionFoundException("subscription not found");
        return searchResponse.hits().hits().stream().findFirst().get().source();
      }).onFailure().recoverWithUni(throwable -> Uni.createFrom().failure(throwable));
    } catch (Exception e) {
      return Uni.createFrom().failure(new QueryExecutionException(e.getMessage(), e));
    }
  }

  @Override
  public Uni<QuerySubscriptionResults> getSubscriptionOfNextPage(String nextPageToken) {
    final QuerySubscriptionResults querySubscriptionResults = new QuerySubscriptionResults();
    try {
      return getHitsFromNextPageToken(nextPageToken, Optional.empty(), querySubscriptionResults, NamedQueryMetaDataES.class).map(searchHits -> {
        querySubscriptionResults.setQuerySubscriptions(OpenSearchJSONUtil.convert(searchHits, QuerySubscription.class));
        return querySubscriptionResults;
      });
    } catch (ResourceNotFoundException e) {
      log.info(e.getMessage());
      return Uni.createFrom().failure(new ResourceNotFoundException(e.getMessage()));
    } catch (Exception e) {
      log.error(ERROR_WHILE_FETCHING_NEXT_PAGE_RESOURCE, e);
      return Uni.createFrom().failure(new QueryExecutionException(ERROR_WHILE_FETCHING_NEXT_PAGE_RESOURCE, e));
    }
  }

  @Override
  public Uni<QuerySubscriptionResults> getSubscriptionFromQueryName(String queryName, Optional<Integer> perPage) {
    final QuerySubscriptionResults querySubscriptionResults = new QuerySubscriptionResults();
    final PitPaginationProvider.Context pitContext = pitPaginationProvider.createContext(perPage, configService.epcis().subscription().index() + "*");
    return pitContext.newSearchRequest().chain(searchRequestBuilder -> {
      searchRequestBuilder.query(Query.of(qb -> qb.bool(QueryBuilders.bool().filter(queryBuilder -> queryBuilder.term(TermQuery.of(termBuilder -> termBuilder.field(QUERY_NAME).value(FieldValue.of(queryName))))).filter(queryBuilder -> queryBuilder.term(TermQuery.of(termBuilder -> termBuilder.field(DELETED).value(FieldValue.of(false))))).build()))).sort(sortBuilder -> sortBuilder.field(FieldSort.of(fieldBuilder -> fieldBuilder.field(QUERY_NAME).order(SortOrder.Asc)))).sort(sortBuilder -> sortBuilder.field(FieldSort.of(fieldBuilder -> fieldBuilder.field(CREATED_AT).order(SortOrder.Desc)))).size(perPage.orElse(configService.epcis().perPageDefault()));
      return pitContext.searchResponse(QuerySubscription.class);
    }).chain(searchResponse -> pitContext.applyPaginationSupport(searchResponse, querySubscriptionResults, QuerySubscription.class)).map(searchResponse -> {
      querySubscriptionResults.setQuerySubscriptions(OpenSearchJSONUtil.convert(searchResponse.hits().hits(), QuerySubscription.class));
      return querySubscriptionResults;
    }).onFailure().recoverWithUni(throwable -> {
      log.error(ERROR_WHILE_FETCHING_RESOURCE, throwable);
      return Uni.createFrom().failure(new ImplementationException(ERROR_WHILE_FETCHING_RESOURCE, throwable));
    });
  }

  private Uni<NamedQuerySubscription> getQuerySubscriptionByID(final String subscriptionID) {
    final BoolQuery query = QueryBuilders.bool().filter(q1 -> q1.term(t1 -> t1.field(SUBSCRIPTION_ID).value(FieldValue.of(subscriptionID)))).filter(q2 -> q2.term(t2 -> t2.field(DELETED).value(FieldValue.of(false)))).build();
    SearchRequest searchRequest = new SearchRequest.Builder().index(configService.epcis().subscription().index()).query(Query.of(q -> q.bool(query))).build();
    try {
      return Uni.createFrom().completionStage(superUserAsyncClient.search(searchRequest, NamedQuerySubscriptionES.class)).map(searchResponse -> searchResponse.hits().hits().stream().findFirst().map(Hit::source).map(NamedQuerySubscriptionES::mapToNamedQuerySubscription).orElseThrow(() -> new NoSubscriptionFoundException(String.format("No subscription found with ID: %s", subscriptionID))));
    } catch (Exception e) {
      log.error(ERROR_WHILE_PROCESSING_QUERY, e);
      return Uni.createFrom().failure(new QueryExecutionException(e.getMessage(), e));
    }
  }

  @Override
  public void updateSubscriptionStatus(String subscriptionID, OffsetDateTime updateDate, String status, Optional<String> errorDescription) {
    try {
      final IdsQuery idsQuery = IdsQuery.of(builder -> builder.values(List.of(subscriptionID)));
      final SearchRequest searchRequest = new SearchRequest.Builder().index(configService.epcis().subscription().index()).query(builder -> builder.ids(idsQuery)).build();
      var builder = NamedQuerySubscriptionES.builder().status(status).errorDescription(errorDescription.orElse(""));
      if (updateDate != null) {
        builder.minRecordTime(updateDate);
      }
      var namedQuerySubscriptionES = builder.build();
      Uni.createFrom().completionStage(() -> {
        try {
          return superUserAsyncClient.search(searchRequest, NamedQuerySubscriptionES.class);
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }).onFailure().invoke(throwable -> {
        log.error("Error while searching namedQuerySubscription for status update for subscriptionId: {}", subscriptionID, throwable);
        throw new QueryExecutionException("Error while searching namedQuerySubscription", throwable);
      }).onItem().transformToUni(searchResponse -> {
        Optional<Hit<NamedQuerySubscriptionES>> firstHit = searchResponse.hits().hits().stream().filter(Objects::nonNull).findFirst();
        if (firstHit.isPresent()) {
          String indexName = firstHit.get().index();
          try {
            return updateSubscriptionStatus(subscriptionID, namedQuerySubscriptionES, indexName);
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        } else {
          throw new QueryExecutionException("No subscription found with ID: " + subscriptionID);
        }
      }).subscribe().with(updateResponse -> log.info("Status updated for subscriptionId={} minRecordTime={} status={}", subscriptionID, updateDate, status), throwable -> {
        log.error("Error while updating subscription status for subscriptionId: {}", subscriptionID, throwable);
        throw new PersistenceException(ExceptionMessages.ERROR_WHILE_UPDATING_QUERY_SUBSCRIPTION, throwable);
      });
    } catch (Exception e) {
      throw new PersistenceException(ExceptionMessages.ERROR_WHILE_UPDATING_QUERY_SUBSCRIPTION, e);
    }
  }

  private Uni<UpdateResponse<NamedQuerySubscriptionES>> updateSubscriptionStatus(String subscriptionID, NamedQuerySubscriptionES namedQuerySubscriptionES, String indexName) throws IOException {
    return Uni.createFrom().completionStage(superUserAsyncClient.update(u -> u.index(indexName).id(subscriptionID).doc(namedQuerySubscriptionES), NamedQuerySubscriptionES.class)).onFailure().recoverWithUni(throwable -> {
      log.error("Error while updating subscription status for subscriptionId: {}", subscriptionID, throwable);
      return Uni.createFrom().failure(throwable);
    });
  }

  private <T> Uni<List<Hit<T>>> getHitsFromNextPageToken(final String nextPageToken, Optional<Integer> perPage, final PaginationSupport paginationSupport, Class<T> responseType) {
    final PitPaginationProvider.Context pitContext = pitPaginationProvider.loadContext(nextPageToken, perPage);
    return pitContext.newSearchRequest().chain(searchRequestBuilder -> {
      perPage.ifPresent(searchRequestBuilder::size);
      return pitContext.searchResponse(responseType);
    }).chain(searchResponse -> pitContext.applyPaginationSupport(searchResponse, paginationSupport, responseType).map(r -> r.hits().hits()));
  }

  public SubscriptionRepositoryImpl(RepositoryConfiguration configService, PitPaginationProvider pitPaginationProvider, UserDetailsExtractor userDetailsExtractor, ObjectMapper objectMapper, OpenSearchConfig openSearchConfig, ContextAwareOpenSearchClient contextAwareClient) {
    this.configService = configService;
    this.pitPaginationProvider = pitPaginationProvider;
    this.userDetailsExtractor = userDetailsExtractor;
    this.objectMapper = objectMapper;
    this.openSearchConfig = openSearchConfig;
    this.contextAwareClient = contextAwareClient;
  }
}
