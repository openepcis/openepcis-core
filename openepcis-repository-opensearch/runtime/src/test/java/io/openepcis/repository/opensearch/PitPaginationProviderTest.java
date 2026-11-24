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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.openepcis.core.model.PaginationSupport;
import io.openepcis.repository.api.KeyValueRepository;
import io.openepcis.repository.config.RepositoryConfiguration;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.subscription.Cancellable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.ShardStatistics;
import org.opensearch.client.opensearch.core.CreatePitRequest;
import org.opensearch.client.opensearch.core.CreatePitResponse;
import org.opensearch.client.opensearch.core.DeletePitRequest;
import org.opensearch.client.opensearch.core.DeletePitResponse;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.core.search.HitsMetadata;
import org.opensearch.client.opensearch.core.search.TotalHits;
import org.opensearch.client.opensearch.core.search.TotalHitsRelation;

/**
 * The life of a point in time, told against a client that only pretends: when one is opened, who
 * owns it, and that every ending of a step that did not hand it over closes it.
 */
class PitPaginationProviderTest {

  private static final Duration WAIT = Duration.ofSeconds(10);

  OpenSearchAsyncClient client;
  ContextAwareOpenSearchClient contextAware;
  final Map<String, String> kv = new ConcurrentHashMap<>();
  final AtomicInteger pitsCreated = new AtomicInteger();
  final List<String> pitsDeleted = new ArrayList<>();
  /** What the next search answers: how many hits, with these ids, and with this PIT id echoed. */
  final AtomicReference<Integer> hitsToAnswer = new AtomicReference<>(0);
  final AtomicReference<CompletableFuture<CreatePitResponse>> pendingCreate = new AtomicReference<>();
  PitPaginationProvider provider;

  @BeforeEach
  void fakes() throws Exception {
    pitsCreated.set(0);
    pitsDeleted.clear();
    kv.clear();
    pendingCreate.set(null);
    client = mock(OpenSearchAsyncClient.class);
    contextAware = mock(ContextAwareOpenSearchClient.class);
    when(contextAware.getAsyncClient()).thenReturn(client);
    when(contextAware.getAsyncClient(any())).thenReturn(client);
    when(client.createPit(any(CreatePitRequest.class))).thenAnswer(inv -> {
      final CompletableFuture<CreatePitResponse> pending = pendingCreate.get();
      if (pending != null) {
        return pending;
      }
      return CompletableFuture.completedFuture(pit("pit-" + pitsCreated.incrementAndGet()));
    });
    when(client.deletePit(any(DeletePitRequest.class))).thenAnswer(inv -> {
      final DeletePitRequest req = inv.getArgument(0);
      synchronized (pitsDeleted) {
        pitsDeleted.addAll(req.pitId());
      }
      return CompletableFuture.completedFuture(DeletePitResponse.of(b -> b.pits(req.pitId().stream()
          .map(id -> org.opensearch.client.opensearch.core.pit.DeletedPit.of(d -> d.pitId(id).successful(true))).toList())));
    });
    when(client.search(any(SearchRequest.class), any(Class.class))).thenAnswer(inv -> {
      final SearchRequest req = inv.getArgument(0);
      final int n = Math.min(hitsToAnswer.get(), req.size() == null ? Integer.MAX_VALUE : req.size());
      final List<Hit<Map>> hits = new ArrayList<>();
      for (int i = 0; i < n; i++) {
        final int seq = i;
        hits.add(Hit.of(h -> h.index("events").id("e" + seq).sort(List.of(FieldValue.of(1000 - seq), FieldValue.of("e" + seq)))));
      }
      return CompletableFuture.completedFuture(new SearchResponse.Builder<Map>()
          .took(1).timedOut(false)
          .shards(ShardStatistics.of(s -> s.total(1).successful(1).failed(0)))
          .hits(HitsMetadata.of(h -> h.hits(hits).total(TotalHits.of(t -> t.value(hitsToAnswer.get()).relation(TotalHitsRelation.Eq)))))
          .pitId(req.pit() == null ? null : req.pit().id())
          .build());
    });

    final KeyValueRepository store = mock(KeyValueRepository.class);
    when(store.save(any())).thenAnswer(inv -> {
      final String token = "tok-" + (kv.size() + 1);
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
    when(config.epcis().nextPageTokenTTL()).thenReturn("1m");
    when(config.epcis().perPageDefault()).thenReturn(10);
    when(config.epcis().perPageMax()).thenReturn(100);
    when(config.epcis().pit().lazy()).thenReturn(true);
    when(config.epcis().pit().sweepInterval()).thenReturn("15s");
    provider = new PitPaginationProvider(config, store, contextAware, new SimpleMeterRegistry());
  }

  private static CreatePitResponse pit(final String id) {
    return CreatePitResponse.of(b -> b.pitId(id).creationTime(System.currentTimeMillis())
        .shards(ShardStatistics.of(s -> s.total(1).successful(1).failed(0))));
  }

  /** One page as a repository method runs it: request, search, pagination. */
  private PaginationSupport page(final PitPaginationProvider.Context ctx, final Integer limit) {
    final PaginationSupport support = new PaginationSupport();
    ctx.guard(ctx.newSearchRequest()
            .chain(b -> ctx.limit(limit).searchResponse(Map.class))
            .chain(r -> ctx.applyPaginationSupport(r, support, Map.class)))
        .await().atMost(WAIT);
    return support;
  }

  @Test
  @DisplayName("a result that fits in one page opens no point in time and leaves no token")
  void onePageNoPit() throws Exception {
    hitsToAnswer.set(7);
    final PaginationSupport support = page(provider.createContext(Optional.of(10), "events*"), null);
    assertEquals(0, pitsCreated.get());
    assertNull(support.getNextPageToken());
    assertNull(support.getRel());
    assertTrue(kv.isEmpty());
    verify(client, times(1)).search(any(SearchRequest.class), any(Class.class));
  }

  @Test
  @DisplayName("the first N (eventCountLimit) is complete by definition: no probe, no PIT, no token")
  void limitNeverTouchesThePool() {
    hitsToAnswer.set(500);
    final PitPaginationProvider.Context ctx = provider.createContext(Optional.of(1), "events*");
    final PaginationSupport support = page(ctx, 1);
    assertEquals(0, pitsCreated.get());
    assertNull(support.getNextPageToken());
    assertFalse(ctx.hasMore());
    assertEquals(1, ctx.requestedSize(), "exactly the limit, no extra hit");
  }

  @Test
  @DisplayName("a next page opens the PIT after the search and the token takes it over; the page is trimmed")
  void nextPageOpensAndHandsOver() throws Exception {
    hitsToAnswer.set(500);
    final PitPaginationProvider.Context ctx = provider.createContext(Optional.of(10), "events*");
    final PaginationSupport support = new PaginationSupport();
    final SearchResponse<Map> response = ctx.newSearchRequest()
        .chain(b -> ctx.searchResponse(Map.class))
        .chain(r -> ctx.applyPaginationSupport(r, support, Map.class))
        .await().atMost(WAIT);
    assertEquals(10, response.hits().hits().size(), "the probe hit is not returned");
    assertEquals(1, pitsCreated.get(), "opened only once a next page was known");
    assertEquals("next", support.getRel());
    assertNotNull(support.getNextPageToken());
    assertTrue(kv.get(support.getNextPageToken()).contains("pit-1"), "the token carries the PIT");
    assertTrue(pitsDeleted.isEmpty(), "handed over, not closed");
    final List<FieldValue> after = ctx.searchAfter().orElseThrow();
    assertEquals(991L, after.get(0).longValue(), "after the last kept hit, not the probe hit");
    assertEquals("e9", after.get(1).stringValue());
  }

  @Test
  @DisplayName("a walk to the end closes the PIT on the last page")
  void walkToTheEndCloses() {
    hitsToAnswer.set(25);
    PaginationSupport support = page(provider.createContext(Optional.of(10), "events*"), null);
    assertEquals("next", support.getRel());
    hitsToAnswer.set(25); // the fake does not move the cursor; the page sizes do the talking
    support = page(provider.loadContext(support.getNextPageToken(), Optional.of(10)), null);
    assertEquals("next", support.getRel());
    hitsToAnswer.set(5);
    support = page(provider.loadContext(support.getNextPageToken(), Optional.of(10)), null);
    assertNull(support.getRel());
    assertEquals(1, pitsCreated.get(), "one PIT for the whole walk");
    assertEquals(List.of("pit-1"), pitsDeleted);
    assertTrue(kv.isEmpty(), "every token consumed");
  }

  @Test
  @DisplayName("a later page that fails, or whose caller throws, closes the PIT it owns")
  void failureOnALaterPageCloses() throws Exception {
    hitsToAnswer.set(25);
    final PaginationSupport first = page(provider.createContext(Optional.of(10), "events*"), null);
    // the search itself fails
    when(client.search(any(SearchRequest.class), any(Class.class))).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("boom")));
    final PitPaginationProvider.Context ctx = provider.loadContext(first.getNextPageToken(), Optional.of(10));
    assertThrows(Exception.class, () -> page(ctx, null));
    assertEquals(List.of("pit-1"), pitsDeleted);

    // the caller throws between search and pagination: the guard closes it
    pitsDeleted.clear();
    fakes();
    hitsToAnswer.set(25);
    final PaginationSupport again = page(provider.createContext(Optional.of(10), "events*"), null);
    final PitPaginationProvider.Context ctx2 = provider.loadContext(again.getNextPageToken(), Optional.of(10));
    assertThrows(Exception.class, () -> ctx2.guard(ctx2.newSearchRequest()
        .chain(b -> ctx2.searchResponse(Map.class))
        .map(r -> { throw new IllegalStateException("caller failed"); }))
        .await().atMost(WAIT));
    assertEquals(List.of("pit-1"), pitsDeleted);
  }

  @Test
  @DisplayName("a client that hangs up mid-page cancels the chain and the PIT is closed")
  void cancellationCloses() throws Exception {
    hitsToAnswer.set(25);
    final PaginationSupport first = page(provider.createContext(Optional.of(10), "events*"), null);
    final CompletableFuture<SearchResponse<Map>> neverAnswers = new CompletableFuture<>();
    when(client.search(any(SearchRequest.class), any(Class.class))).thenReturn(neverAnswers);
    final PitPaginationProvider.Context ctx = provider.loadContext(first.getNextPageToken(), Optional.of(10));
    final Cancellable subscription = ctx.guard(ctx.newSearchRequest().chain(b -> ctx.searchResponse(Map.class)))
        .subscribe().with(r -> { }, err -> { });
    Thread.sleep(50);
    assertTrue(pitsDeleted.isEmpty());
    subscription.cancel();
    Thread.sleep(50);
    assertEquals(List.of("pit-1"), pitsDeleted);
  }

  @Test
  @DisplayName("a PIT whose creation was cancelled in flight is closed when the cluster answers")
  void cancelledCreationCloses() throws Exception {
    hitsToAnswer.set(25);
    final CompletableFuture<CreatePitResponse> slow = new CompletableFuture<>();
    pendingCreate.set(slow);
    final PitPaginationProvider.Context ctx = provider.createContext(Optional.of(10), "events*");
    final Cancellable subscription = ctx.newSearchRequest()
        .chain(b -> ctx.searchResponse(Map.class))
        .chain(r -> ctx.applyPaginationSupport(r, new PaginationSupport(), Map.class))
        .subscribe().with(r -> { }, err -> { });
    Thread.sleep(50);
    subscription.cancel();
    slow.complete(pit("pit-late"));
    Thread.sleep(50);
    assertEquals(List.of("pit-late"), pitsDeleted);
    assertTrue(kv.isEmpty(), "no token for a cancelled request");
  }

  @Test
  @DisplayName("the real 429 of a full pool is recognised, wherever the text sits")
  void exhaustionIsRecognised() {
    final String body = "{\"error\":{\"root_cause\":[{\"type\":\"rejected_execution_exception\",\"reason\":"
        + "\"Trying to create too many Point In Time contexts. Must be less than or equal to: [300]. "
        + "This limit can be set by changing the [search.max_open_pit_context] setting.\"}]},\"status\":429}";
    assertTrue(PitPaginationProvider.isPitPoolExhausted(new RuntimeException("status line [HTTP/2.0 429 Too Many Requests] " + body)));
    assertTrue(PitPaginationProvider.isPitPoolExhausted(new RuntimeException("wrapped", new RuntimeException(body))));
    assertFalse(PitPaginationProvider.isPitPoolExhausted(new RuntimeException("index_not_found_exception")));
    assertFalse(PitPaginationProvider.isPitPoolExhausted(null));
  }

  @Test
  @DisplayName("a full pool closes expired PITs and retries with a pause; a live PIT is never taken")
  void reliefClosesOnlyExpired() throws Exception {
    hitsToAnswer.set(25);
    final AtomicInteger attempts = new AtomicInteger();
    when(client.createPit(any(CreatePitRequest.class))).thenAnswer(inv -> attempts.incrementAndGet() == 1
        ? CompletableFuture.failedFuture(new RuntimeException("rejected_execution_exception: Trying to create too many Point In Time contexts"))
        : CompletableFuture.completedFuture(pit("pit-after-relief")));
    final long now = System.currentTimeMillis();
    when(client.getAllPits(any(org.opensearch.client.opensearch.core.GetAllPitsRequest.class))).thenReturn(CompletableFuture.completedFuture(
        org.opensearch.client.opensearch.core.GetAllPitsResponse.of(b -> b.pits(List.of(
            org.opensearch.client.opensearch.core.pit.PitDetail.of(p -> p.pitId("expired").creationTime(now - 120_000).keepAlive(60_000L)),
            org.opensearch.client.opensearch.core.pit.PitDetail.of(p -> p.pitId("live").creationTime(now - 1_000).keepAlive(60_000L)))))));
    final PaginationSupport support = page(provider.createContext(Optional.of(10), "events*"), null);
    assertEquals("next", support.getRel());
    assertEquals(2, attempts.get());
    assertEquals(List.of("expired"), pitsDeleted, "the live PIT belongs to a walk that may still be going on");
  }

  @Test
  @DisplayName("eager mode still opens the PIT first, and still closes it when there is no next page")
  void eagerModeCloses() throws Exception {
    final RepositoryConfiguration config = mock(RepositoryConfiguration.class, Mockito.RETURNS_DEEP_STUBS);
    when(config.epcis().nextPageTokenTTL()).thenReturn("1m");
    when(config.epcis().perPageDefault()).thenReturn(10);
    when(config.epcis().perPageMax()).thenReturn(100);
    when(config.epcis().pit().lazy()).thenReturn(false);
    when(config.epcis().pit().sweepInterval()).thenReturn("15s");
    final KeyValueRepository store = mock(KeyValueRepository.class);
    when(store.save(any())).thenReturn(Uni.createFrom().item("tok"));
    provider = new PitPaginationProvider(config, store, contextAware, new SimpleMeterRegistry());
    hitsToAnswer.set(3);
    page(provider.createContext(Optional.of(10), "events*"), null);
    assertEquals(1, pitsCreated.get());
    assertEquals(List.of("pit-1"), pitsDeleted);
  }
}
