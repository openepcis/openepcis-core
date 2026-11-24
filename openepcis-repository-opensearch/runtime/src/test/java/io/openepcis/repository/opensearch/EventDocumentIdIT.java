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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openepcis.model.epcis.ErrorDeclaration;
import io.openepcis.model.epcis.ObjectEvent;
import io.openepcis.model.epcis.extension.OpenEPCISExtension;
import io.openepcis.model.dto.InvalidEPCISEventInfo;
import io.openepcis.repository.config.RepositoryConfiguration;
import io.openepcis.repository.model.EPCISEventES;
import io.openepcis.repository.util.EventDocuments;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.hc.core5.http.HttpHost;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * Events stored under their content hash, in the month index of their eventTime, with op_type
 * create (EventDocuments): readable by GET at once, one document per content whoever captures it,
 * one error declaration per event.
 */
class EventDocumentIdIT {

  private static final DockerImageName IMAGE =
      DockerImageName.parse("opensearchproject/opensearch:2.18.0");
  private static final String HASH = "ni:///sha-256;0f0e?ver=CBV2.0";
  private static final OffsetDateTime EVENT_TIME = OffsetDateTime.parse("2026-08-31T23:30:00-02:00");
  /** 2026-09-01T01:30Z: the month is taken in UTC, not in the event's own offset. */
  private static final String INDEX = "epcis-event-2026-09";

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
        EventDocumentIdIT.class.getResourceAsStream(
            "/templates/opensearch-indices/epcis-event-template.json")) {
      final ObjectMapper json = new ObjectMapper();
      mappings = json.writeValueAsString(json.readTree(in).get("mappings"));
    }
    client.indices().create(c -> c.index(INDEX).mappings(m -> m.withJson(new StringReader(mappings))));

    final RepositoryConfiguration config = mock(RepositoryConfiguration.class, RETURNS_DEEP_STUBS);
    when(config.epcis().event().index()).thenReturn("epcis-event");
    repository = new EventRepositoryImpl(config, null);
    repository.superUserAsyncClient = new OpenSearchAsyncClient(transport);
    final ContextAwareOpenSearchClient contextAware = mock(ContextAwareOpenSearchClient.class);
    when(contextAware.getAsyncClient()).thenReturn(new OpenSearchAsyncClient(transport));
    queries = new QueryRepositoryImpl(new ObjectMapper(), null, config, contextAware, null, null, null);
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

  private static ObjectEvent event(final boolean declaration) {
    final ObjectEvent event = new ObjectEvent();
    event.setType("ObjectEvent");
    event.setEventID(HASH);
    event.setEventTime(EVENT_TIME);
    event.setEventTimeZoneOffset("-02:00");
    event.setRecordTime(OffsetDateTime.now());
    event.setAction(io.openepcis.model.epcis.Action.OBSERVE);
    event.setEpcList(List.of("urn:epc:id:sgtin:9521234.000300.AUR-1"));
    final OpenEPCISExtension extension = new OpenEPCISExtension();
    extension.setHash(HASH);
    extension.setSequenceInEPCISDoc(1);
    event.setOpenEPCISExtension(extension);
    if (declaration) {
      final ErrorDeclaration errorDeclaration = new ErrorDeclaration();
      errorDeclaration.setDeclarationTime(OffsetDateTime.parse("2026-11-11T16:00:00Z"));
      errorDeclaration.setReason("incorrect_data");
      event.setErrorDeclaration(errorDeclaration);
    }
    return event;
  }

  private static Map<String, Object> capturedBy(final String tenant) {
    return new java.util.HashMap<>(
        Map.of("capturedBy", Map.of("defaultGroup", tenant, "userID", tenant + "-user")));
  }

  private static boolean write(final ObjectEvent event, final String tenant, final String captureID) {
    return write(event, tenant, captureID, capturedBy(tenant));
  }

  private static boolean write(
      final ObjectEvent event, final String tenant, final String captureID, final Map<String, Object> metadata) {
    final List<InvalidEPCISEventInfo> invalid = new ArrayList<>();
    return repository
        .bulkSaveOrUpdate(
            List.of(event), invalid, true, captureID, ZonedDateTime.now(), metadata, tenant, List.of(), false)
        .await()
        .atMost(Duration.ofSeconds(30));
  }

  private static EPCISEventES get(final String id) {
    return queries.getEPCISEventByDocument(INDEX, id).await().atMost(Duration.ofSeconds(10));
  }

  @Test
  @DisplayName("stored under its hash in the month of its eventTime, readable by GET at once; a second tenant shares the document; the same tenant again is a replay; one declaration")
  void oneDocumentPerContent() {
    assertEquals(INDEX, repository.eventIndexName(EVENT_TIME));

    assertTrue(write(event(false), "minecraft", "c1"));
    final EPCISEventES first = get(HASH);
    assertEquals(HASH, first.getHash(), "found by GET without a refresh");
    assertEquals(1, first.getMetadata().getCapturedBy().size());

    assertTrue(write(event(false), "acme", "c2"), "a second tenant is settled, not refused");
    final EPCISEventES shared = get(HASH);
    assertEquals(2, shared.getMetadata().getCapturedBy().size(), "both tenants on the one document");

    final Map<String, Object> replay = capturedBy("minecraft");
    assertTrue(write(event(false), "minecraft", "c3", replay), "the same tenant again is a replay, saved");
    assertEquals(Boolean.TRUE, replay.get(io.openepcis.repository.Constants.IS_REPLAY));
    assertEquals(3, get(HASH).getMetadata().getCapturedBy().size(), "the replay's capture is listed");

    assertTrue(write(event(true), "minecraft", "c4"));
    final EPCISEventES declaration = get(EventDocuments.documentId(HASH, true));
    assertEquals(HASH, declaration.getEventID(), "the declaration carries the event's eventID");
    assertTrue(declaration.getErrorDeclarationES() != null);
    final Map<String, Object> declarationReplay = capturedBy("minecraft");
    assertTrue(
        write(event(true), "minecraft", "c5", declarationReplay),
        "the same declaration again is a replay, saved");
    assertEquals(Boolean.TRUE, declarationReplay.get(io.openepcis.repository.Constants.IS_REPLAY));
    final ObjectEvent other = event(true);
    other.getErrorDeclaration().setDeclarationTime(OffsetDateTime.parse("2026-11-12T09:00:00Z"));
    assertThrows(
        RuntimeException.class,
        () -> write(other, "minecraft", "c6"),
        "a second declaration that says something else is refused");
  }

  @Test
  @DisplayName("a record that is not there reads as null, so the caller falls back to the eventID")
  void missingDocumentIsNull() {
    assertEquals(null, get("ni:///sha-256;nothing?ver=CBV2.0"));
  }
}
