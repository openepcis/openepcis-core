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

import static io.openepcis.repository.Constants.NEXT;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.openepcis.core.model.PaginationSupport;
import io.openepcis.model.epcis.exception.EPCISException;
import io.openepcis.model.epcis.exception.NotAcceptedException;
import io.openepcis.model.epcis.exception.QueryExecutionException;
import io.openepcis.model.epcis.exception.QueryTooLargeException;
import io.openepcis.opentelemetry.logging.OpenEPCISLogger;
import io.openepcis.repository.Constants;
import io.openepcis.repository.api.KeyValueRepository;
import io.openepcis.repository.config.RepositoryConfiguration;
import io.openepcis.repository.opensearch.util.SearchSourceUtil;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.Time;
import org.opensearch.client.opensearch.core.CreatePitRequest;
import org.opensearch.client.opensearch.core.CreatePitResponse;
import org.opensearch.client.opensearch.core.DeletePitRequest;
import org.opensearch.client.opensearch.core.GetAllPitsRequest;
import org.opensearch.client.opensearch.core.GetAllPitsResponse;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.pit.PitDetail;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.core.search.HitsMetadata;
import org.opensearch.client.opensearch.core.search.Pit;
import org.opensearch.client.transport.TransportException;
import org.opensearch.client.transport.httpclient5.ResponseException;

/**
 * Pagination over OpenSearch with points in time (PIT), opened as late as possible and closed as
 * early as possible.
 *
 * <p>A PIT costs one search context on every shard it covers, and the cluster allows
 * {@code search.max_open_pit_context} of them per node (default 300). On dev one PIT over
 * {@code epcis-event*} took 85 contexts, so about fourteen queries in flight exhausted the pool, and
 * every further query failed with 429 before a single hit was read. Hence:
 *
 * <ul>
 *   <li><b>Lazy.</b> The first page is a plain search asking for one hit more than the page holds;
 *       that extra hit says whether there is a next page. Only then is a PIT opened, and the token
 *       of the next page takes it over. A query whose result fits in one page, or that asks for the
 *       first N ({@code eventCountLimit}), never touches the pool. Later pages run on the PIT and use
 *       the same one-more-hit probe, so no second search is needed to know whether to go on.
 *   <li><b>Owned.</b> A {@link Context} holds its PIT until it hands it over to a token. Whatever
 *       ends a step (an item, a failure, a cancelled subscription because the client hung up)
 *       releases a PIT that was not handed over. A PIT whose creation was cancelled in flight is
 *       closed as soon as the cluster answers. Deletes go through the admin client, since the
 *       request that owned the PIT may be gone.
 *   <li><b>Swept.</b> A token that is never followed leaves its PIT until the keep-alive (the token
 *       TTL) runs out. The cluster does not always reclaim an expired PIT at once, so a sweeper
 *       closes expired ones every {@code repository.epcis.pit.sweep-interval}. The cluster's own
 *       list ({@code GET _search/point_in_time/_all}) is the only state; nothing is kept locally,
 *       so several replicas never disagree. Note that OpenSearch keeps a PIT for at least
 *       {@code point_in_time.init.keep_alive} (30 s) and reports that floor as the keep-alive when
 *       less was asked for; a TTL below it does not shorten anything.
 *   <li><b>Relieved.</b> When the pool is still exhausted, expired PITs are closed and the creation
 *       is retried with a short backoff. A PIT that is not expired belongs to a walk that may still
 *       be going on, possibly on another replica, and is never taken away.
 * </ul>
 *
 * Metrics: {@code openepcis_pit_created_total}, {@code openepcis_pit_deleted_total{reason}},
 * {@code openepcis_pit_create_failed_total{reason}}, {@code openepcis_pit_open} and
 * {@code openepcis_pit_shard_contexts}.
 */
@Singleton
public class PitPaginationProvider {

  private static final OpenEPCISLogger log = OpenEPCISLogger.getLogger(PitPaginationProvider.class);

  /** Retries of a PIT creation after the pool was found exhausted, with these pauses between. */
  static final long[] RELIEF_BACKOFF_MS = {100, 300, 1000};

  /**
   * Transforms OpenSearch TransportException/ResponseException into EPCISException preserving the
   * HTTP status code (e.g. 403 Forbidden).
   */
  static final Function<Throwable, Throwable> TRANSPORT_EXCEPTION_MAPPER = throwable -> {
    if (TransportException.class.isAssignableFrom(throwable.getClass())) {
      final TransportException tpe = (TransportException) throwable;
      if (tpe.getCause() != null && ResponseException.class.isAssignableFrom(tpe.getCause().getClass())) {
        final ResponseException rpe = (ResponseException) tpe.getCause();
        return new EPCISException(rpe.getMessage(), rpe.status(), rpe);
      }
    }
    return throwable;
  };

  final RepositoryConfiguration configService;
  private final KeyValueRepository keyValueRepository;
  // Context-aware client that automatically selects request-scoped or admin client based on CDI context
  final ContextAwareOpenSearchClient contextAwareClient;
  private final MeterRegistry meterRegistry;
  private final DistributionSummary shardContexts;
  private final AtomicInteger openPits = new AtomicInteger();
  /** One listing of the cluster's PITs at a time; concurrent askers share it for a moment. */
  private final Uni<List<PitDetail>> sharedListing;
  private ScheduledExecutorService sweeper;

  @Inject
  public PitPaginationProvider(
      RepositoryConfiguration configService,
      KeyValueRepository keyValueRepository,
      ContextAwareOpenSearchClient contextAwareClient,
      Instance<MeterRegistry> meterRegistry) {
    this(configService, keyValueRepository, contextAwareClient,
        meterRegistry != null && meterRegistry.isResolvable() ? meterRegistry.get() : new SimpleMeterRegistry());
  }

  public PitPaginationProvider(
      RepositoryConfiguration configService,
      KeyValueRepository keyValueRepository,
      ContextAwareOpenSearchClient contextAwareClient,
      MeterRegistry meterRegistry) {
    this.configService = configService;
    this.keyValueRepository = keyValueRepository;
    this.contextAwareClient = contextAwareClient;
    this.meterRegistry = meterRegistry;
    this.shardContexts = DistributionSummary.builder("openepcis_pit_shard_contexts")
        .description("shard contexts one point in time occupies").register(meterRegistry);
    meterRegistry.gauge("openepcis_pit_open", openPits);
    this.sharedListing = listAllPits().memoize().atLeast(Duration.ofSeconds(2));
  }

  @PostConstruct
  void startSweeper() {
    sweeper = Executors.newSingleThreadScheduledExecutor(r -> {
      Thread t = new Thread(r, "pit-sweeper");
      t.setDaemon(true);
      return t;
    });
    final long sweepSeconds = sweepIntervalSeconds();
    sweeper.scheduleWithFixedDelay(this::sweepExpiredPits, sweepSeconds, sweepSeconds, TimeUnit.SECONDS);
    log.debug("PIT sweeper started; cadence {}s, lazy={}", sweepSeconds, lazy());
  }

  @PreDestroy
  void stopSweeper() {
    if (sweeper != null) {
      sweeper.shutdownNow();
    }
  }

  boolean lazy() {
    try {
      return configService.epcis().pit().lazy();
    } catch (RuntimeException e) {
      return true;
    }
  }

  /** Resolve {@code repository.epcis.next-page-token-ttl} (e.g. {@code 1m}) to seconds. */
  long configuredKeepAliveSeconds() {
    try {
      return Duration.parse(Constants.PARSE_TIME + configService.epcis().nextPageTokenTTL().toUpperCase()).getSeconds();
    } catch (Exception e) {
      log.warn("could not parse repository.epcis.next-page-token-ttl, defaulting to 60s", e);
      return 60;
    }
  }

  long sweepIntervalSeconds() {
    try {
      return Math.max(5, Duration.parse(Constants.PARSE_TIME + configService.epcis().pit().sweepInterval().toUpperCase()).getSeconds());
    } catch (Exception e) {
      return 15;
    }
  }

  private String keepAliveTime() {
    return configuredKeepAliveSeconds() + Constants.SECONDS;
  }

  // ---------------------------------------------------------------------------------------------
  // sweeper and relief: the cluster's list is the only state

  /**
   * Closes the PITs the cluster considers expired ({@code creationTime + keepAlive < now}) but has
   * not reclaimed yet. Every page extends a PIT's keep-alive, so an expired one belongs to no walk
   * that is still going on, on any replica. Deleting one twice is harmless.
   */
  void sweepExpiredPits() {
    sharedListing
        .invoke(pits -> openPits.set(pits.size()))
        .map(PitPaginationProvider::expired)
        .chain(ids -> deletePitBatch(ids, "sweeper"))
        .subscribe()
        .with(
            n -> { if (n != null && n > 0) log.info("sweeper closed {} expired PITs", n); },
            err -> log.debug("sweeper failed (will retry on next tick)", err));
  }

  static List<String> expired(final List<PitDetail> pits) {
    final long now = System.currentTimeMillis();
    return pits.stream()
        .filter(p -> p.creationTime() != null && p.keepAlive() != null && p.creationTime() + p.keepAlive() < now)
        .map(PitDetail::pitId)
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  private Uni<List<PitDetail>> listAllPits() {
    return Uni.createFrom()
        .completionStage(() -> {
          try {
            return contextAwareClient.getAsyncClient("admin").getAllPits(GetAllPitsRequest.of(b -> b));
          } catch (java.io.IOException e) {
            return java.util.concurrent.CompletableFuture.<GetAllPitsResponse>failedStage(e);
          }
        })
        .onFailure().transform(TRANSPORT_EXCEPTION_MAPPER)
        .map(GetAllPitsResponse::pits);
  }

  /** Best-effort batch delete with the admin client; returns the count successfully closed. */
  private Uni<Integer> deletePitBatch(final List<String> ids, final String reason) {
    if (ids == null || ids.isEmpty()) {
      return Uni.createFrom().item(0);
    }
    return Uni.createFrom()
        .completionStage(() -> {
          try {
            return contextAwareClient.getAsyncClient("admin").deletePit(new DeletePitRequest.Builder().pitId(ids).build());
          } catch (java.io.IOException e) {
            return java.util.concurrent.CompletableFuture.<org.opensearch.client.opensearch.core.DeletePitResponse>failedStage(e);
          }
        })
        .map(resp -> (int) resp.pits().stream().filter(p -> Boolean.TRUE.equals(p.successful())).count())
        .invoke(n -> countDeleted(reason, n))
        .onFailure().recoverWithItem(0);
  }

  /**
   * Whether a failure says the pool of PIT contexts is full. OpenSearch answers 429 with a
   * {@code rejected_execution_exception} naming the setting; the text may sit in a cause.
   */
  static boolean isPitPoolExhausted(Throwable t) {
    for (Throwable c = t; c != null; c = c.getCause() == c ? null : c.getCause()) {
      final String m = c.getMessage();
      if (m != null && (m.contains("max_open_pit_context") || m.contains("too many Point In Time")
          || m.contains("rejected_execution_exception"))) {
        return true;
      }
    }
    return false;
  }

  // ---------------------------------------------------------------------------------------------
  // contexts

  /**
   * create new Pit Context for targetIndexes
   *
   * @param targetIndexes search indexes
   * @return new Context to start with
   */
  public final Context createContext(String... targetIndexes) {
    return new Context(this, null, targetIndexes);
  }

  public final Context createContext(Optional<Integer> perPage, String... targetIndexes) {
    return new Context(this, null, perPage, targetIndexes);
  }

  public final Context createContext(Integer maxPerPage, Optional<Integer> perPage, String... targetIndexes) {
    return new Context(this, null, maxPerPage, perPage, targetIndexes);
  }

  /**
   * load existing Pit Context from key-value store
   *
   * @param token token used for loading SearchQuery
   * @return existing Context for next page
   */
  @Deprecated(forRemoval = true, since = "1.0.0")
  public final Context loadContext(final String token) {
    return new Context(this, token, new String[0]);
  }

  /**
   * load existing Pit Context from key-value store
   *
   * @param token token used for loading SearchQuery
   * @param perPage elements per page
   * @return existing Context for next page
   */
  public final Context loadContext(final String token, Optional<Integer> perPage) {
    return new Context(this, token, perPage, new String[0]);
  }

  public final Context loadContext(final String token, Integer maxPerPage, Optional<Integer> perPage) {
    return new Context(this, token, maxPerPage, perPage, new String[0]);
  }

  /**
   * One paginated query, first page or a later one. The context owns its PIT until a token takes
   * it over; see the class comment.
   */
  public static final class Context {

    private final PitPaginationProvider provider;
    private final String token;
    private final String[] targetIndexes;
    private SearchRequest searchRequest = null;
    private SearchRequest.Builder searchRequestBuilder = null;
    private Pit pit = null;
    private List<FieldValue> searchAfter = null;
    private Integer perPage = null;
    private Integer maxPerPage = null;
    /** The first N the caller wants ({@code eventCountLimit}); such a result has no next page. */
    private Integer limit = null;
    /** Whether the last search found a hit beyond the page. */
    private boolean hasMore = false;
    /** Set once a token holds the PIT; from then on the context does not close it. */
    private final AtomicBoolean handedOver = new AtomicBoolean(false);
    private final AtomicBoolean released = new AtomicBoolean(false);

    @Deprecated(forRemoval = true)
    private Context(final PitPaginationProvider provider, String token, String[] targetIndexes) {
      this(provider, token, provider.configService.epcis().perPageMax(),
          Optional.of(provider.configService.epcis().perPageDefault()), targetIndexes);
    }

    private Context(final PitPaginationProvider provider, String token, Optional<Integer> perPage, String[] targetIndexes)
        throws QueryTooLargeException {
      this(provider, token, provider.configService.epcis().perPageMax(), perPage, targetIndexes);
    }

    private Context(final PitPaginationProvider provider, String token, Integer maxPerPage, Optional<Integer> perPage,
        String[] targetIndexes) throws QueryTooLargeException {
      this.provider = provider;
      this.token = token;
      this.maxPerPage = maxPerPage;
      this.targetIndexes = targetIndexes;
      perPage(perPage);
    }

    public Context perPage(final Optional<Integer> perPage) {
      if (perPage != null && perPage.isPresent() && perPage.get() > maxPerPage) {
        throw new QueryTooLargeException(String.format("requested %d perPage, max %d is allowed", perPage.get(), maxPerPage));
      }
      this.perPage = perPage.orElse(provider.configService.epcis().perPageDefault());
      return this;
    }

    public Uni<Context> perPageUni(final Optional<Integer> perPage) {
      return Uni.createFrom().item(perPage(perPage)).onFailure().recoverWithUni(t -> Uni.createFrom().failure(t));
    }

    public Integer perPage() {
      return perPage;
    }

    /**
     * The caller wants the first {@code limit} events and nothing after them (EPCIS
     * {@code eventCountLimit}). The search asks for exactly that many; the result has no next page,
     * so no PIT and no token.
     */
    public Context limit(final Integer limit) {
      this.limit = limit != null && limit > 0 ? limit : null;
      return this;
    }

    /** Hits one search asks for: the page, plus one to learn whether there is a next page. */
    int requestedSize() {
      return limit != null ? limit : perPage + 1;
    }

    public SearchRequest searchRequest() {
      if (searchRequest == null) {
        searchRequest = searchRequestBuilder().build();
        log.debug("searchRequest: {}", SearchSourceUtil.toString(searchRequest));
      }
      return searchRequest;
    }

    public SearchRequest.Builder searchRequestBuilder() {
      if (searchRequestBuilder == null) {
        throw new IllegalStateException("SearchRequest.Builder not initialized");
      }
      return searchRequestBuilder;
    }

    public Optional<Pit> pit() {
      return Optional.ofNullable(pit);
    }

    public void pit(Pit pit) {
      this.pit = pit;
    }

    public Optional<List<FieldValue>> searchAfter() {
      return Optional.ofNullable(searchAfter);
    }

    public void searchAfter(List<FieldValue> searchAfter) {
      this.searchAfter = searchAfter;
    }

    /** Whether the last search saw a hit beyond the page. */
    public boolean hasMore() {
      return hasMore;
    }

    /**
     * The request of the first page, or the stored request of a later one. A first page gets a
     * PIT only when {@code repository.epcis.pit.lazy} is off; a later page runs on the PIT its
     * token carries, which this context then owns until it hands it over again.
     */
    public Uni<SearchRequest.Builder> newSearchRequest() {
      if (token == null) {
        if (provider.lazy()) {
          this.searchRequestBuilder = new SearchRequest.Builder().index(List.of(targetIndexes));
          return Uni.createFrom().item(searchRequestBuilder);
        }
        return provider.createPit(targetIndexes)
            .map(created -> {
              pit(Pit.of(b -> b.id(created.pitId()).keepAlive(provider.keepAliveTime())));
              this.searchRequestBuilder = new SearchRequest.Builder().pit(pit);
              return searchRequestBuilder();
            })
            .onTermination().invoke((item, failure, cancelled) -> {
              if (failure != null || cancelled) {
                release(cancelled ? "cancelled" : "failure");
              }
            });
      }
      return provider.keyValueRepository
          .getAndLease(token)
          .chain(searchSourceStr -> {
            final SearchRequest nextSearchRequest = SearchSourceUtil.searchRequest(searchSourceStr);
            this.searchRequestBuilder = SearchSourceUtil.searchRequestBuilder(nextSearchRequest);
            this.pit = nextSearchRequest.pit();
            return provider.keyValueRepository.delete(token).map(d -> searchRequestBuilder());
          })
          .onFailure().transform(throwable -> new NotAcceptedException("next page token invalid", throwable));
    }

    /**
     * Runs the page's search: one hit more than the page, which decides {@link #hasMore()} and is
     * not returned. A later page's PIT is released if the search fails or the caller goes away.
     */
    public <T> Uni<SearchResponse<T>> searchResponse(Class<T> responseType, OpenSearchAsyncClient superUserAsyncClient) {
      try {
        searchRequestBuilder().ccsMinimizeRoundtrips(Boolean.FALSE).size(requestedSize());
        if (pit != null) {
          // every page pushes the keep-alive out again
          pit(Pit.of(b -> b.id(pit.id()).keepAlive(provider.keepAliveTime())));
          searchRequestBuilder().pit(pit);
        }
        searchRequest = null;
        // SPI automatically injects JWT auth headers via contextAwareClient.getAsyncClient()
        // superUserAsyncClient is only passed when admin client (no DLS) is needed
        final OpenSearchAsyncClient clientToUse = superUserAsyncClient != null ? superUserAsyncClient : provider.contextAwareClient.getAsyncClient();
        final SearchRequest request = searchRequest();
        return Uni.createFrom()
            .completionStage(() -> {
              try {
                return clientToUse.search(request, responseType);
              } catch (java.io.IOException e) {
                return java.util.concurrent.CompletableFuture.<SearchResponse<T>>failedStage(e);
              }
            })
            .onFailure().transform(TRANSPORT_EXCEPTION_MAPPER)
            .map(this::keepThePage)
            .onTermination().invoke((item, failure, cancelled) -> {
              if (failure != null || cancelled) {
                release(cancelled ? "cancelled" : "failure");
              }
            });
      } catch (Exception e) {
        log.error("error while making search request in ES", e);
        release("failure");
        return Uni.createFrom().failure(new QueryExecutionException("error while making search request in ES", e));
      }
    }

    public <T> Uni<SearchResponse<T>> searchResponse(Class<T> responseType) {
      return searchResponse(responseType, null);
    }

    /** Drops the probe hit, remembers whether there was one and where the page ends. */
    private <T> SearchResponse<T> keepThePage(final SearchResponse<T> response) {
      final List<Hit<T>> hits = response.hits().hits();
      final int page = limit != null ? limit : perPage;
      hasMore = limit == null && hits.size() > page;
      final List<Hit<T>> kept = hits.size() > page ? hits.subList(0, page) : hits;
      if (CollectionUtils.isNotEmpty(kept)) {
        final Hit<T> last = kept.get(kept.size() - 1);
        if (CollectionUtils.isNotEmpty(last.sort())) {
          searchAfter(last.sort());
        }
      }
      if (kept == hits) {
        return response;
      }
      // the client's list setters add to what is there, so the metadata is rebuilt, not copied
      final List<Hit<T>> trimmed = new ArrayList<>(kept);
      final HitsMetadata<T> original = response.hits();
      return new SearchResponse.Builder<T>()
          .took(response.took())
          .timedOut(response.timedOut())
          .shards(response.shards())
          .hits(HitsMetadata.of(h -> {
            h.hits(trimmed);
            if (original.total() != null) {
              h.total(original.total());
            }
            if (original.maxScore() != null) {
              h.maxScore(original.maxScore());
            }
            return h;
          }))
          .pitId(response.pitId())
          .aggregations(response.aggregations())
          .build();
    }

    /**
     * Decides what the response says about a next page. Without one, a PIT this context still owns
     * is closed. With one, the PIT is opened now if there is none yet, the next request is stored
     * under a token, and the token takes the PIT over.
     */
    public <T> Uni<SearchResponse<T>> applyPaginationSupport(final SearchResponse<T> searchResponse,
        final PaginationSupport paginationSupport, final Class<T> responseType) {
      try {
        if (!hasMore) {
          log.debug("Response Container: {}", paginationSupport);
          return releaseUni("last_page").replaceWith(searchResponse);
        }
        return ensurePit()
            .chain(ignored -> {
              final SearchRequest.Builder next = SearchSourceUtil.searchRequestBuilder(searchRequest(), false);
              next.ccsMinimizeRoundtrips(Boolean.FALSE).pit(pit).size(perPage);
              searchAfter().ifPresent(next::searchAfter);
              final String nextSearchAsString = SearchSourceUtil.toString(next.build());
              return provider.keyValueRepository.save(nextSearchAsString);
            })
            .map(savedToken -> {
              handedOver.set(true);
              paginationSupport.setRel(NEXT);
              paginationSupport.setTokenExpiryTime(OffsetDateTime.now().plusSeconds(provider.configuredKeepAliveSeconds()));
              paginationSupport.setNextPageToken(savedToken);
              log.debug("Response Container: {}", paginationSupport);
              return searchResponse;
            })
            .onTermination().invoke((item, failure, cancelled) -> {
              if (failure != null || cancelled) {
                release(cancelled ? "cancelled" : "failure");
              }
            });
      } catch (Throwable throwable) {
        release("failure");
        return Uni.createFrom().failure(throwable);
      }
    }

    /** The PIT of this walk, opened now when the first page found a next one. */
    private Uni<Pit> ensurePit() {
      if (pit != null) {
        return Uni.createFrom().item(pit);
      }
      return provider.createPit(targetIndexes).map(created -> {
        pit(Pit.of(b -> b.id(created.pitId()).keepAlive(provider.keepAliveTime())));
        return pit;
      });
    }

    /**
     * Wraps a caller's whole chain so that a PIT this context still owns when the chain ends in a
     * failure or a cancelled subscription is closed, wherever in the chain that happened.
     */
    public <R> Uni<R> guard(final Uni<R> chain) {
      return chain.onTermination().invoke((item, failure, cancelled) -> {
        if (failure != null || cancelled) {
          release(cancelled ? "cancelled" : "failure");
        }
      });
    }

    /** Closes the PIT unless a token holds it; safe to call more than once. */
    public void release(final String reason) {
      releaseUni(reason).subscribe().with(ok -> { }, err -> log.debug("PIT release ({}) failed", reason, err));
    }

    private Uni<Boolean> releaseUni(final String reason) {
      if (pit == null || handedOver.get() || !released.compareAndSet(false, true)) {
        return Uni.createFrom().item(Boolean.FALSE);
      }
      return provider.deletePit(pit.id(), reason);
    }
  }

  // ---------------------------------------------------------------------------------------------
  // create and delete

  /** Opens a PIT over the indices, complete or not at all; relieved and retried when the pool is full. */
  Uni<CreatePitResponse> createPit(String... targetIndexes) {
    final CreatePitRequest pitRequest = new CreatePitRequest.Builder()
        .index(List.of(targetIndexes))
        .keepAlive(new Time.Builder().time(keepAliveTime()).build())
        // OpenSearch defaults to true: a PIT then covers only the shards that answered, and
        // every search on it reports zero failed shards while missing their hits. Under a
        // few parallel queries on dev that returned 0, 1 or 2 events where there were 5.
        .allowPartialPitCreation(false)
        .build();
    return createPitWithRelief(pitRequest, 0)
        .chain(this::requireCompletePit)
        .invoke(created -> {
          meterRegistry.counter("openepcis_pit_created_total").increment();
          if (created.shards() != null) {
            shardContexts.record(created.shards().total());
          }
          log.debug("PIT {} opened over {} ({} shard contexts)", created.pitId(), List.of(targetIndexes),
              created.shards() == null ? "?" : created.shards().total());
        });
  }

  /**
   * A PIT that missed shards is closed again and reported as a failure, so a search on it can never
   * pass a partial answer off as the whole one.
   */
  Uni<CreatePitResponse> requireCompletePit(final CreatePitResponse response) {
    if (response == null || response.shards() == null || response.shards().failed() == 0) {
      return Uni.createFrom().item(response);
    }
    final String reason = response.shards().failures().isEmpty() || response.shards().failures().get(0).reason() == null
        ? "no reason given"
        : response.shards().failures().get(0).reason().reason();
    final QueryExecutionException failure = new QueryExecutionException(String.format(
        "the point in time could not be opened: %d of %d shards failed (%s)",
        response.shards().failed(), response.shards().total(), reason));
    return deletePit(response.pitId(), "partial")
        .onFailure().recoverWithItem(false)
        .chain(ignored -> Uni.createFrom().failure(failure));
  }

  /**
   * One creation attempt. A creation the caller cancels while it is in flight still happens on the
   * cluster; its PIT is closed as soon as the answer arrives. A full pool closes the expired PITs
   * and tries again after a pause, {@link #RELIEF_BACKOFF_MS} times.
   */
  private Uni<CreatePitResponse> createPitWithRelief(final CreatePitRequest req, final int attempt) {
    final AtomicReference<CompletionStage<CreatePitResponse>> inFlight = new AtomicReference<>();
    return Uni.createFrom()
        .<CreatePitResponse>completionStage(() -> {
          try {
            final CompletionStage<CreatePitResponse> stage = contextAwareClient.getAsyncClient().createPit(req);
            inFlight.set(stage);
            // a copy: Mutiny cancels the future it is given, and a cancelled future never tells
            // which PIT the cluster opened anyway
            return stage.thenApply(Function.identity());
          } catch (java.io.IOException e) {
            return java.util.concurrent.CompletableFuture.failedStage(e);
          }
        })
        .onCancellation().invoke(() -> {
          final CompletionStage<CreatePitResponse> stage = inFlight.get();
          if (stage != null) {
            stage.thenAccept(created -> deletePit(created.pitId(), "cancelled")
                .subscribe().with(ok -> { }, err -> log.debug("closing a PIT created after cancellation failed", err)));
          }
        })
        .onFailure().transform(TRANSPORT_EXCEPTION_MAPPER)
        .onFailure().recoverWithUni(t -> {
          if (!isPitPoolExhausted(t)) {
            meterRegistry.counter("openepcis_pit_create_failed_total", "reason", "other").increment();
            return Uni.createFrom().failure(t);
          }
          if (attempt >= RELIEF_BACKOFF_MS.length) {
            meterRegistry.counter("openepcis_pit_create_failed_total", "reason", "exhausted").increment();
            return Uni.createFrom().failure(t);
          }
          log.warn("PIT pool exhausted; closing expired PITs and retrying in {} ms (attempt {} of {})",
              RELIEF_BACKOFF_MS[attempt], attempt + 1, RELIEF_BACKOFF_MS.length);
          return sharedListing
              .invoke(pits -> openPits.set(pits.size()))
              .map(PitPaginationProvider::expired)
              .chain(ids -> deletePitBatch(ids, "evicted"))
              .invoke(closed -> { if (closed > 0) log.info("closed {} expired PITs to relieve pool pressure", closed); })
              .onItem().delayIt().by(Duration.ofMillis(RELIEF_BACKOFF_MS[attempt]))
              .chain(ignored -> createPitWithRelief(req, attempt + 1));
        });
  }

  /** Closes one PIT with the admin client; the request that owned it may already be gone. */
  public Uni<Boolean> deletePit(final String pitID, final String reason) {
    if (StringUtils.isBlank(pitID)) {
      return Uni.createFrom().item(Boolean.FALSE);
    }
    return Uni.createFrom()
        .completionStage(() -> {
          try {
            return contextAwareClient.getAsyncClient("admin")
                .deletePit(new DeletePitRequest.Builder().pitId(Collections.singletonList(pitID)).build());
          } catch (java.io.IOException e) {
            return java.util.concurrent.CompletableFuture.<org.opensearch.client.opensearch.core.DeletePitResponse>failedStage(e);
          }
        })
        .map(resp -> resp.pits() != null && !resp.pits().isEmpty() && Boolean.TRUE.equals(resp.pits().get(0).successful()))
        .invoke(ok -> {
          if (ok) {
            countDeleted(reason, 1);
            log.debug("PIT {} closed ({})", pitID, reason);
          } else {
            log.warn("PIT {} was not closed ({})", pitID, reason);
          }
        })
        .onFailure().invoke(err -> log.warn("closing PIT {} ({}) failed: {}", pitID, reason, err.toString()));
  }

  public Uni<Boolean> deletePit(final String pitID) {
    return deletePit(pitID, "caller");
  }

  public Runnable deletePitRunnable(final Pit pit) {
    return () -> {
      if (pit != null) {
        deletePit(pit.id(), "caller").subscribe().with(b -> { }, err -> { });
      }
    };
  }

  private void countDeleted(final String reason, final int n) {
    if (n > 0) {
      meterRegistry.counter("openepcis_pit_deleted_total", "reason", reason).increment(n);
    }
  }
}
