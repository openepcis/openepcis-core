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

import io.openepcis.repository.util.EventDocuments;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openepcis.model.dto.InvalidEPCISEventInfo;
import io.openepcis.model.epcis.ObjectEvent;
import io.openepcis.model.epcis.extension.OpenEPCISExtension;
import io.openepcis.repository.Constants;
import io.openepcis.repository.config.RepositoryConfiguration;
import io.openepcis.repository.model.CapturedByES;
import io.openepcis.repository.model.EPCISEventES;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.apache.hc.core5.http.HttpHost;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * Capturing what the tenant captured before is an idempotent replay: the event is not stored
 * again, it counts under the new capture (the capture job compares its processed events with
 * {@code getEventCountByCaptureID}), and it is left out of the event-saved list of that capture
 * ({@code getEventIDsByCaptureID}), so subscribers do not get it twice. Each event is persisted
 * on its own, as the capture pipeline does.
 */
class EventReplayIT {

  private static final DockerImageName IMAGE =
      DockerImageName.parse("opensearchproject/opensearch:2.18.0");
  private static final OffsetDateTime EVENT_TIME = OffsetDateTime.parse("2026-10-15T10:00:00Z");
  private static final String INDEX = "epcis-event-2026-10";

  private static GenericContainer<?> opensearch;
  private static OpenSearchClient client;
  private static EventRepositoryImpl repository;
  private static QueryRepositoryImpl queries;

  @BeforeAll
  static void start() throws IOException {
    Assumptions.assumeTrue(dockerAvailable(), "no container runtime available, skipping");
    opensearch =
        new GenericContainer<>(IMAGE)
            .withExposedPorts(9200)
            .withEnv("discovery.type", "single-node")
            .withEnv("DISABLE_SECURITY_PLUGIN", "true")
            .withEnv("DISABLE_INSTALL_DEMO_CONFIG", "true")
            .withEnv("OPENSEARCH_JAVA_OPTS", "-Xms512m -Xmx512m")
            .waitingFor(Wait.forHttp("/").forStatusCode(200))
            .withStartupTimeout(Duration.ofMinutes(5));
    opensearch.start();
    final OpenSearchTransport transport =
        ApacheHttpClient5TransportBuilder.builder(
                new HttpHost("http", opensearch.getHost(), opensearch.getMappedPort(9200)))
            .setMapper(
                new JacksonJsonpMapper(
                    new ObjectMapper()
                        .findAndRegisterModules()
                        .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)))
            .build();
    client = new OpenSearchClient(transport);

    final String mappings;
    try (InputStream in =
        EventReplayIT.class.getResourceAsStream(
            "/templates/opensearch-indices/epcis-event-template.json")) {
      final ObjectMapper json = new ObjectMapper();
      mappings = json.writeValueAsString(json.readTree(in).get("mappings"));
    }
    // with the alias of the template: the rollback addresses it
    client
        .indices()
        .create(
            c ->
                c.index(INDEX)
                    .mappings(m -> m.withJson(new StringReader(mappings)))
                    .aliases("epcis-event", a -> a));

    final RepositoryConfiguration config = mock(RepositoryConfiguration.class, RETURNS_DEEP_STUBS);
    when(config.epcis().event().index()).thenReturn("epcis-event");
    repository = new EventRepositoryImpl(config, null);
    repository.superUserAsyncClient = new OpenSearchAsyncClient(transport);
    final ContextAwareOpenSearchClient contextAware = mock(ContextAwareOpenSearchClient.class);
    when(contextAware.getAsyncClient()).thenReturn(new OpenSearchAsyncClient(transport));
    final ManagedExecutor executor = mock(ManagedExecutor.class);
    when(executor.runAsync(any()))
        .thenAnswer(i -> CompletableFuture.runAsync(i.getArgument(0, Runnable.class)));
    queries = new QueryRepositoryImpl(new ObjectMapper(), null, config, contextAware, null, executor, null);
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

  private static ObjectEvent event(final String hash, final int sequence) {
    final ObjectEvent event = new ObjectEvent();
    event.setType("ObjectEvent");
    event.setEventID(hash);
    event.setEventTime(EVENT_TIME);
    event.setEventTimeZoneOffset("+00:00");
    event.setRecordTime(OffsetDateTime.now());
    event.setAction(io.openepcis.model.epcis.Action.OBSERVE);
    event.setEpcList(List.of("urn:epc:id:sgtin:9521234.000300." + hash.hashCode()));
    final OpenEPCISExtension extension = new OpenEPCISExtension();
    extension.setHash(hash);
    extension.setSequenceInEPCISDoc(sequence);
    event.setOpenEPCISExtension(extension);
    return event;
  }

  private static Map<String, Object> capturedBy(final String tenant) {
    return new HashMap<>(
        Map.of("capturedBy", Map.of("defaultGroup", tenant, "userID", tenant + "-user")));
  }

  /** One event persisted as the capture pipeline does, with captureID and sequence on it. */
  private static Map<String, Object> persist(
      final String hash, final int sequence, final String tenant, final String captureID) {
    final ObjectEvent event = event(hash, sequence);
    event.getOpenEPCISExtension().setCaptureID(captureID);
    final Map<String, Object> metadata = capturedBy(tenant);
    final List<InvalidEPCISEventInfo> invalid = new ArrayList<>();
    final Boolean saved =
        repository
            .bulkSaveOrUpdate(
                List.of(event), invalid, false, captureID, ZonedDateTime.now(), metadata, tenant, List.of(), false)
            .await()
            .atMost(Duration.ofSeconds(30));
    assertTrue(saved, "saved: " + invalid);
    assertTrue(invalid.isEmpty(), "no errors: " + invalid);
    return metadata;
  }

  private static void refresh() throws IOException {
    client.indices().refresh(r -> r.index(INDEX));
  }

  private static long documentsWithHash(final String hash) throws IOException {
    refresh();
    return client.count(c -> c.index(INDEX).query(q -> q.term(t -> t.field("hash").value(FieldValue.of(hash))))).count();
  }

  private static long countFor(final String captureID) throws IOException {
    refresh();
    return queries.getEventCountByCaptureID(captureID).await().atMost(Duration.ofSeconds(10));
  }

  private static List<String> eventSavedFor(final String captureID) throws IOException {
    refresh();
    return queries.getEventIDsByCaptureID(captureID).collect().asList().await().atMost(Duration.ofSeconds(20));
  }

  private static EPCISEventES get(final String id) {
    return queries.getEPCISEventByDocument(INDEX, id).await().atMost(Duration.ofSeconds(10));
  }

  @Test
  @DisplayName("the same document twice: one stored event, the second capture counts it and sends no event-saved for it")
  void sameDocumentTwice() throws IOException {
    final String hash = "ni:///sha-256;a1?ver=CBV2.0";
    final Map<String, Object> first = persist(hash, 1, "acme", "r1");
    assertNull(first.get(Constants.IS_REPLAY));

    final Map<String, Object> second = persist(hash, 1, "acme", "r2");
    assertEquals(Boolean.TRUE, second.get(Constants.IS_REPLAY), "the second is a replay");

    assertEquals(1, documentsWithHash(hash), "one document");
    assertEquals(1, countFor("r2"), "the replaying job finds as many events as it processed");
    assertEquals(List.of(), eventSavedFor("r2"), "no second event-saved");
    assertEquals(List.of(hash), eventSavedFor("r1"));
    assertEquals(
        List.of("r1", "r2"),
        get(hash).getMetadata().getCapturedBy().stream().map(CapturedByES::getCaptureID).toList());
  }

  @Test
  @DisplayName("a document mixing new and replayed events stores the new ones and counts all")
  void mixedDocument() throws IOException {
    final String old = "ni:///sha-256;b1?ver=CBV2.0";
    final String fresh = "ni:///sha-256;b2?ver=CBV2.0";
    persist(old, 1, "acme", "m1");

    final Map<String, Object> replayed = persist(old, 1, "acme", "m2");
    final Map<String, Object> stored = persist(fresh, 2, "acme", "m2");
    assertEquals(Boolean.TRUE, replayed.get(Constants.IS_REPLAY));
    assertNull(stored.get(Constants.IS_REPLAY));

    assertEquals(1, documentsWithHash(old));
    assertEquals(1, documentsWithHash(fresh), "the new event is stored");
    assertEquals(2, countFor("m2"), "both events count under the second capture");
    assertEquals(List.of(fresh), eventSavedFor("m2"), "event-saved only for the new one");
  }

  @Test
  @DisplayName("another tenant is added to capturedBy of the shared document and gets its event-saved")
  void otherTenant() throws IOException {
    final String hash = "ni:///sha-256;c1?ver=CBV2.0";
    persist(hash, 1, "acme", "t1");
    final Map<String, Object> other = persist(hash, 1, "globex", "t2");
    assertNull(other.get(Constants.IS_REPLAY), "news for the other tenant");

    assertEquals(1, documentsWithHash(hash));
    final List<CapturedByES> capturedBy = get(hash).getMetadata().getCapturedBy();
    assertEquals(List.of("acme", "globex"), capturedBy.stream().map(CapturedByES::getDefaultGroup).toList());
    assertEquals(1, countFor("t2"));
    assertEquals(List.of(hash), eventSavedFor("t2"), "the other tenant's subscribers get it");
  }

  @Test
  @DisplayName("a retried write of the same capture is saved once; the same event twice in one document is refused")
  void sameCapture() throws IOException {
    final String hash = "ni:///sha-256;d1?ver=CBV2.0";
    persist(hash, 1, "acme", "s1");
    final Map<String, Object> retry = persist(hash, 1, "acme", "s1");
    assertNull(retry.get(Constants.IS_REPLAY), "the retry is the first capture, not a replay");
    assertEquals(1, get(hash).getMetadata().getCapturedBy().size(), "listed once");

    final ObjectEvent twice = event(hash, 2);
    twice.getOpenEPCISExtension().setCaptureID("s1");
    final List<InvalidEPCISEventInfo> invalid = new ArrayList<>();
    assertThrows(
        RuntimeException.class,
        () ->
            repository
                .bulkSaveOrUpdate(
                    List.of(twice), invalid, false, "s1", ZonedDateTime.now(), capturedBy("acme"), "acme", List.of(), false)
                .await()
                .atMost(Duration.ofSeconds(30)));
    assertEquals(1, invalid.size(), "the duplicate is the one error, without an empty one beside it");
    assertTrue(invalid.get(0).getDetail().contains("Duplicate EPCIS Event"));
  }

  @Test
  @DisplayName("rolling back the replaying capture leaves the event and removes only that capture")
  void rollbackOfReplay() throws IOException {
    final String hash = "ni:///sha-256;e1?ver=CBV2.0";
    persist(hash, 1, "acme", "b1");
    persist(hash, 1, "acme", "b2");
    refresh();

    assertTrue(
        repository
            .removeEventsForCaptureID("b2", ZonedDateTime.now(), "acme")
            .await()
            .atMost(Duration.ofSeconds(30)));
    assertEquals(1, documentsWithHash(hash), "the event of the first capture stays");
    assertEquals(
        List.of("b1"),
        get(hash).getMetadata().getCapturedBy().stream().map(CapturedByES::getCaptureID).toList());
  }

  private static String doc(final String hash) {
    return INDEX + "/" + hash;
  }

  private static long visibleFor(final String captureID) throws IOException {
    refresh();
    return client
        .count(
            c ->
                c.index(INDEX)
                    .query(
                        q ->
                            q.nested(
                                n ->
                                    n.path("metadata")
                                        .query(
                                            nq ->
                                                nq.bool(
                                                    b ->
                                                        b.filter(f -> f.term(t -> t.field("metadata.visible").value(FieldValue.of(true))))
                                                            .filter(
                                                                f ->
                                                                    f.term(
                                                                        t ->
                                                                            t.field("metadata.capturedBy.captureID")
                                                                                .value(FieldValue.of(captureID)))))))))
        .count();
  }

  @Test
  @DisplayName("a job completes by its documents: found without a refresh, made visible in one bulk, capturedBy kept")
  void completionByDocuments() throws IOException {
    final String a = "ni:///sha-256;cd1?ver=CBV2.0";
    final String b = "ni:///sha-256;cd2?ver=CBV2.0";
    persist(a, 1, "acme", "cd1");
    persist(b, 2, "acme", "cd1");

    final Map<String, EPCISEventES> found =
        queries
            .getEventsByDocuments(List.of(doc(a), doc(b), doc("ni:///sha-256;nope?ver=CBV2.0")))
            .await()
            .atMost(Duration.ofSeconds(10));
    assertEquals(List.of(doc(a), doc(b)), List.copyOf(found.keySet()), "realtime: no refresh needed, the missing one is left out");
    assertEquals(a, found.get(doc(a)).getEventID());
    assertTrue(EventDocuments.newInCapture(found.get(doc(a)), "cd1"));

    assertTrue(repository.markVisible(List.of(doc(a), doc(b))).await().atMost(Duration.ofSeconds(30)));
    assertEquals(2, visibleFor("cd1"), "both made visible");
    assertEquals(
        List.of("cd1"),
        get(a).getMetadata().getCapturedBy().stream().map(CapturedByES::getCaptureID).toList(),
        "the partial update leaves the rest of metadata alone");
  }

  @Test
  @DisplayName("a replay found by its document is no news for the replaying capture")
  void replayByDocumentIsNoNews() {
    final String hash = "ni:///sha-256;ce1?ver=CBV2.0";
    persist(hash, 1, "acme", "ce1");
    persist(hash, 1, "acme", "ce2");
    final Map<String, EPCISEventES> found =
        queries.getEventsByDocuments(List.of(doc(hash))).await().atMost(Duration.ofSeconds(10));
    assertFalse(EventDocuments.newInCapture(found.get(doc(hash)), "ce2"));
    assertTrue(EventDocuments.newInCapture(found.get(doc(hash)), "ce1"));
  }

  @Test
  @DisplayName("a document reference splits at the first slash, the hash keeps its own")
  void locationParse() {
    final EventDocuments.Location at = EventDocuments.Location.parse(doc("ni:///sha-256;f1?ver=CBV2.0"));
    assertEquals(INDEX, at.index());
    assertEquals("ni:///sha-256;f1?ver=CBV2.0", at.id());
    assertEquals(doc("ni:///sha-256;f1?ver=CBV2.0"), at.toString());
  }
}
