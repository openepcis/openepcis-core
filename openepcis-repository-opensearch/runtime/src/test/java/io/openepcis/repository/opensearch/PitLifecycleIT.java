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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.openepcis.core.model.PaginationSupport;
import io.openepcis.repository.api.KeyValueRepository;
import io.openepcis.repository.config.RepositoryConfiguration;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.hc.core5.http.HttpHost;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.Refresh;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch.core.GetAllPitsRequest;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * The point-in-time pool against a real cluster: a walk leaves nothing behind, a page that fits
 * opens nothing, an abandoned walk is swept once its keep-alive is over, and with the pool capped
 * at three contexts thirty single-page queries at once still all answer.
 */
class PitLifecycleIT {

  private static final Duration WAIT = Duration.ofSeconds(30);
  private static final String INDEX = "pit-lifecycle";
  private static final int DOCS = 50;

  private static GenericContainer<?> opensearch;
  private static OpenSearchClient blocking;
  private static OpenSearchAsyncClient async;

  PitPaginationProvider provider;
  final Map<String, String> kv = new ConcurrentHashMap<>();

  @BeforeAll
  static void start() throws IOException {
    Assumptions.assumeTrue(dockerAvailable(), "no container runtime available — skipping");
    opensearch = new GenericContainer<>(DockerImageName.parse("opensearchproject/opensearch:2.18.0"))
        .withExposedPorts(9200)
        .withEnv("discovery.type", "single-node")
        .withEnv("DISABLE_SECURITY_PLUGIN", "true")
        .withEnv("DISABLE_INSTALL_DEMO_CONFIG", "true")
        .withEnv("OPENSEARCH_JAVA_OPTS", "-Xms512m -Xmx512m")
        .waitingFor(Wait.forHttp("/").forStatusCode(200))
        .withStartupTimeout(Duration.ofMinutes(5));
    opensearch.start();
    final var transport = ApacheHttpClient5TransportBuilder
        .builder(new HttpHost("http", opensearch.getHost(), opensearch.getMappedPort(9200)))
        .setMapper(new JacksonJsonpMapper())
        .build();
    blocking = new OpenSearchClient(transport);
    async = new OpenSearchAsyncClient(transport);
    blocking.indices().create(c -> c.index(INDEX)
        .settings(s -> s.numberOfShards(1).numberOfReplicas(0))
        .mappings(m -> m.properties("eventTime", p -> p.long_(l -> l)).properties("eventID", p -> p.keyword(k -> k))));
    for (int i = 0; i < DOCS; i++) {
      final int n = i;
      blocking.index(new IndexRequest.Builder<Map<String, Object>>().index(INDEX).id("e" + n)
          .document(Map.of("eventTime", 1000 + n, "eventID", "e" + n)).build());
    }
    blocking.indices().refresh(r -> r.index(INDEX));
  }

  @AfterAll
  static void stop() {
    if (opensearch != null) {
      opensearch.stop();
    }
  }

  private static boolean dockerAvailable() {
    try {
      return DockerClientFactory.instance().isDockerAvailable();
    } catch (RuntimeException e) {
      return false;
    }
  }

  @BeforeEach
  void provider() throws IOException {
    kv.clear();
    deleteAllPits();
    maxOpenPitContexts(1000);
    final ContextAwareOpenSearchClient contextAware = mock(ContextAwareOpenSearchClient.class);
    when(contextAware.getAsyncClient()).thenReturn(async);
    when(contextAware.getAsyncClient(any())).thenReturn(async);
    final KeyValueRepository store = mock(KeyValueRepository.class);
    when(store.save(any())).thenAnswer(inv -> {
      final String token = UUID.randomUUID().toString();
      kv.put(token, inv.getArgument(0));
      return Uni.createFrom().item(token);
    });
    when(store.getAndLease(any())).thenAnswer(inv -> {
      final String v = kv.get(inv.<String>getArgument(0));
      return v == null ? Uni.createFrom().failure(new IllegalStateException("no such token")) : Uni.createFrom().item(v);
    });
    when(store.get(any())).thenAnswer(inv -> Uni.createFrom().item(kv.get(inv.<String>getArgument(0))));
    when(store.delete(any())).thenAnswer(inv -> Uni.createFrom().item(kv.remove(inv.<String>getArgument(0))));
    final RepositoryConfiguration config = mock(RepositoryConfiguration.class, Mockito.RETURNS_DEEP_STUBS);
    when(config.epcis().nextPageTokenTTL()).thenReturn("2s");
    when(config.epcis().perPageDefault()).thenReturn(10);
    when(config.epcis().perPageMax()).thenReturn(100);
    when(config.epcis().pit().lazy()).thenReturn(true);
    when(config.epcis().pit().sweepInterval()).thenReturn("15s");
    provider = new PitPaginationProvider(config, store, contextAware, new SimpleMeterRegistry());
  }

  private static void maxOpenPitContexts(final int n) throws IOException {
    blocking.cluster().putSettings(s -> s.persistent("search.max_open_pit_context", JsonData.of(n)));
  }

  private static int openPits() throws IOException {
    return blocking.getAllPits(GetAllPitsRequest.of(b -> b)).pits().size();
  }

  private static void deleteAllPits() throws IOException {
    final List<String> ids = blocking.getAllPits(GetAllPitsRequest.of(b -> b)).pits().stream().map(p -> p.pitId()).toList();
    if (!ids.isEmpty()) {
      blocking.deletePit(d -> d.pitId(ids));
    }
  }

  /** One page as the repository runs it; first page without a token, later ones with. */
  private PaginationSupport page(final String token, final int perPage) {
    final PitPaginationProvider.Context ctx = token == null
        ? provider.createContext(Optional.of(perPage), INDEX)
        : provider.loadContext(token, Optional.of(perPage));
    final PaginationSupport support = new PaginationSupport();
    ctx.guard(ctx.newSearchRequest()
            .chain(b -> {
              if (token == null) {
                b.query(q -> q.matchAll(m -> m))
                    .sort(so -> so.field(f -> f.field("eventTime").order(SortOrder.Desc)))
                    .sort(so -> so.field(f -> f.field("eventID").order(SortOrder.Desc)));
              }
              return ctx.searchResponse(Map.class);
            })
            .chain(r -> ctx.applyPaginationSupport(r, support, Map.class).map(x -> r)))
        .invoke(r -> support.setRel(support.getRel()))
        .await().atMost(WAIT);
    return support;
  }

  @Test
  @DisplayName("a walk to the end: one PIT for the walk, closed on the last page")
  void walkToTheEnd() throws IOException {
    PaginationSupport support = page(null, 10);
    assertEquals("next", support.getRel());
    assertEquals(1, openPits(), "opened once the first page knew there was more");
    int pages = 1;
    while (support.getNextPageToken() != null) {
      support = page(support.getNextPageToken(), 10);
      pages++;
    }
    assertEquals(5, pages);
    assertEquals(0, openPits(), "nothing left behind");
    assertTrue(kv.isEmpty());
  }

  @Test
  @DisplayName("a result that fits in one page, and a first-N query, open no PIT at all")
  void onePageNoPit() throws IOException {
    assertNull(page(null, 60).getNextPageToken());
    assertEquals(0, openPits());
    final PitPaginationProvider.Context ctx = provider.createContext(Optional.of(1), INDEX);
    final SearchResponse<Map> response = ctx.newSearchRequest()
        .chain(b -> ctx.limit(1).searchResponse(Map.class))
        .chain(r -> ctx.applyPaginationSupport(r, new PaginationSupport(), Map.class))
        .await().atMost(WAIT);
    assertEquals(1, response.hits().hits().size());
    assertEquals(0, openPits());
  }

  @Test
  @DisplayName("with the pool capped at three, thirty concurrent single-page queries all answer")
  void burstOfSinglePageQueriesNeedsNoPool() throws IOException {
    maxOpenPitContexts(3);
    final List<Integer> sizes = Multi.createFrom().range(0, 30)
        .onItem().transformToUniAndMerge(i -> {
          final PitPaginationProvider.Context ctx = provider.createContext(Optional.of(5), INDEX);
          return ctx.guard(ctx.newSearchRequest()
              .chain(b -> ctx.limit(5).searchResponse(Map.class))
              .chain(r -> ctx.applyPaginationSupport(r, new PaginationSupport(), Map.class)))
              .map(r -> r.hits().hits().size());
        })
        .collect().asList().await().atMost(WAIT);
    assertEquals(30, sizes.size());
    assertTrue(sizes.stream().allMatch(n -> n == 5));
    assertEquals(0, openPits());
  }

  /**
   * OpenSearch keeps a PIT for at least {@code point_in_time.init.keep_alive} (30 s), whatever
   * shorter keep-alive was asked for, and {@code _all} reports that floor. So an abandoned walk
   * lives 30 s here although the TTL says 2 s, and the sweeper, which trusts the cluster, waits for
   * it. On dev the TTL is 1m, above the floor.
   */
  @Test
  @DisplayName("a full pool takes nothing live; abandoned walks are swept once the cluster lets them go")
  void fullPoolTakesNothingLiveAndAbandonedWalksAreSwept() throws Exception {
    maxOpenPitContexts(5);
    final List<PaginationSupport> live = List.of(page(null, 10), page(null, 10), page(null, 10));
    live.forEach(support -> assertNotNull(support.getNextPageToken()));
    final int open = openPits();
    assertTrue(open >= 3, "three walks hold their PITs, saw " + open);
    // more walks until the pool is full: the one that fails must not have taken a live PIT away
    boolean refused = false;
    for (int i = 0; i < 10 && !refused; i++) {
      try {
        page(null, 10);
      } catch (Exception e) {
        refused = true;
        assertTrue(PitPaginationProvider.isPitPoolExhausted(e), "refused for the pool, not for anything else: " + e);
      }
    }
    assertTrue(refused, "the pool never filled up at a limit of five");
    assertTrue(openPits() >= open, "no live PIT was taken away to make room");
    // the cluster's floor: after it the sweeper closes every abandoned walk
    Thread.sleep(31_000);
    provider.sweepExpiredPits();
    Thread.sleep(1_000);
    assertEquals(0, openPits(), "nothing abandoned survives the sweep");
    // and the pool is open again
    assertNotNull(page(null, 10).getNextPageToken());
  }
}
