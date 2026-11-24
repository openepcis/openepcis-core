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
package io.openepcis.service.util;

import jakarta.json.JsonObject;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link JsonLDUtil} — primarily a regression test for the
 * thread-safety race that caused captures to silently drop with
 * {@code INVALID_TERM_DEFINITION} errors when two expansions ran concurrently.
 *
 * <p>Running tests without Quarkus CDI, so the {@code ManagedExecutor} is
 * built via its MicroProfile builder and injected by reflection. Everything
 * else lives inside the class under test.
 */
class JsonLDUtilTest {

  private static final String MINIMAL_EPCIS_DOC_TEMPLATE =
      "{" +
      "\"@context\":[\"https://ref.gs1.org/standards/epcis/epcis-context.jsonld\"]," +
      "\"type\":\"EPCISDocument\"," +
      "\"schemaVersion\":\"2.0\"," +
      "\"creationDate\":\"2026-04-23T00:00:00.000Z\"," +
      "\"epcisBody\":{\"eventList\":[{" +
      "\"type\":\"ObjectEvent\"," +
      "\"eventID\":\"urn:uuid:%s\"," +
      "\"eventTime\":\"2026-04-23T00:00:00.000Z\"," +
      "\"eventTimeZoneOffset\":\"+00:00\"," +
      "\"epcList\":[\"https://id.example.com/255/9521234000010\"]," +
      "\"action\":\"ADD\"," +
      "\"bizStep\":\"commissioning\"," +
      "\"disposition\":\"active\"" +
      "}]}" +
      "}";

  private JsonLDUtil util;

  @BeforeEach
  void setUp() throws Exception {
    util = new JsonLDUtil();
    Field f = JsonLDUtil.class.getDeclaredField("managedExecutor");
    f.setAccessible(true);
    f.set(util, ManagedExecutor.builder().build());
  }

  private static String minimalDoc(String eventIdSuffix) {
    return String.format(MINIMAL_EPCIS_DOC_TEMPLATE, eventIdSuffix);
  }

  @Test
  void expand_minimalDocument_succeeds() {
    JsonObject expanded = util.expandJsonLDDocument(minimalDoc("single"))
        .await().atMost(java.time.Duration.ofSeconds(30));
    assertNotNull(expanded, "expansion must return a non-null JsonObject");
    // Top-level key after JSON-LD expansion is the epcisBody IRI.
    assertTrue(expanded.containsKey("https://ns.gs1.org/epcis/epcisBody")
            || expanded.containsKey("https://ref.gs1.org/epcis/epcisBody"),
        "expanded document should carry an epcisBody key, got: " + expanded.keySet());
  }

  /**
   * The regression case: 20 expansions running concurrently must all succeed.
   * Before the fix, one or more would throw {@code INVALID_TERM_DEFINITION}
   * because Titanium's internal state (ActiveContextBuilder / the cached
   * context Document) raced across threads.
   */
  @Test
  void expand_concurrentDocuments_allSucceed() throws Exception {
    final int N = 20;
    List<CompletableFuture<JsonObject>> futures = new ArrayList<>(N);
    for (int i = 0; i < N; i++) {
      String suffix = "concurrent-" + i;
      futures.add(util.expandJsonLDDocument(minimalDoc(suffix))
          .subscribeAsCompletionStage().toCompletableFuture());
    }
    AtomicInteger ok = new AtomicInteger();
    List<Throwable> failures = new ArrayList<>();
    for (CompletableFuture<JsonObject> f : futures) {
      try {
        JsonObject result = f.get(60, TimeUnit.SECONDS);
        assertNotNull(result);
        ok.incrementAndGet();
      } catch (Exception e) {
        failures.add(e.getCause() != null ? e.getCause() : e);
      }
    }
    assertEquals(N, ok.get(),
        "expected all " + N + " concurrent expansions to succeed, but "
            + failures.size() + " failed; first failure: "
            + (failures.isEmpty() ? "n/a" : failures.get(0).toString()));
  }

  /**
   * Second-run safety: after many expansions through the same JsonLDUtil
   * instance, each subsequent call must still succeed. Before the fix, once
   * the cached context Document got mutated by Titanium, every later
   * expansion failed for the lifetime of the JVM.
   */
  @Test
  void expand_manySequentialDocuments_allSucceed() {
    final int N = 50;
    for (int i = 0; i < N; i++) {
      JsonObject result = util.expandJsonLDDocument(minimalDoc("seq-" + i))
          .await().atMost(java.time.Duration.ofSeconds(30));
      assertNotNull(result, "expansion #" + i + " must return a non-null result");
    }
  }

  /**
   * Expansion failures (malformed JSON-LD, broken @context terms, Titanium
   * library bugs) must NOT abort the surrounding capture pipeline — the
   * event still needs to land in OpenSearch even if user-extension
   * extraction can't run. expandJsonLDDocument is contractually graceful:
   * it returns an empty JsonObject on any JsonLdError instead of failing
   * the Uni.
   */
  @Test
  void expand_invalidContext_returnsEmptyObjectInsteadOfFailing() {
    String docWithBadContext =
        "{\"@context\":{\"\":\"oops-empty-prefix\"}," +
        "\"type\":\"EPCISDocument\"," +
        "\"epcisBody\":{\"eventList\":[{\"type\":\"ObjectEvent\"}]}}";
    JsonObject result = util.expandJsonLDDocument(docWithBadContext)
        .await().atMost(java.time.Duration.ofSeconds(30));
    assertNotNull(result, "graceful-degrade path must still complete with a JsonObject");
    assertTrue(result.isEmpty(),
        "on expansion failure, result is an empty JsonObject so downstream extraction is a no-op");
  }
}
