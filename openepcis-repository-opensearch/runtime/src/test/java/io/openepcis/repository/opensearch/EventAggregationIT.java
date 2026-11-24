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
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openepcis.query.aggregation.EventAggregation;
import io.openepcis.query.aggregation.EventAggregation.Bucket;
import io.openepcis.query.aggregation.EventGroupBy;
import io.openepcis.query.data.QueryParam;
import io.openepcis.query.data.SimpleEventQueryRequest;
import io.openepcis.query.factory.FilterFactory;
import io.openepcis.query.factory.QueryFactory;
import io.openepcis.query.factory.QueryParamMapper;
import io.openepcis.repository.config.RepositoryConfiguration;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.hc.core5.http.HttpHost;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.Refresh;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * GET /events/aggregate against a real index: the same filters as /events, the tenant filter,
 * the URN with its as-captured fallback, and calendar buckets in UTC.
 */
class EventAggregationIT {

  private static final DockerImageName IMAGE =
      DockerImageName.parse("opensearchproject/opensearch:2.18.0");
  private static final String SHIPPING = "urn:epcglobal:cbv:bizstep:shipping";
  private static final String RECEIVING = "urn:epcglobal:cbv:bizstep:receiving";
  private static final String CUSTOM = "https://example.com/bizstep/repair";
  private static final String RP = "urn:epc:id:sgln:4012345.00001.0";

  private static GenericContainer<?> opensearch;
  private static OpenSearchClient client;
  private static QueryRepositoryImpl queries;
  private static ContextAwareOpenSearchClient contextAware;
  private static int sequence;

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
            .setMapper(new JacksonJsonpMapper())
            .build();
    client = new OpenSearchClient(transport);

    final String mappings;
    try (InputStream in =
        EventAggregationIT.class.getResourceAsStream(
            "/templates/opensearch-indices/epcis-event-template.json")) {
      final ObjectMapper json = new ObjectMapper();
      mappings = json.writeValueAsString(json.readTree(in).get("mappings"));
    }
    for (final String index : List.of("epcis-event-2026-10", "epcis-event-2026-11")) {
      client.indices().create(c -> c.index(index).mappings(m -> m.withJson(new StringReader(mappings))));
    }

    // tenant t1: three shipping, one receiving, one custom bizStep with no URN form, one without
    // a bizStep, one hidden (visible=false); tenant t2: two shipping
    index("epcis-event-2026-11", "t1", true, "ObjectEvent", "OBSERVE", "2026-11-19T10:00:00Z", cbv(SHIPPING), RP);
    index("epcis-event-2026-11", "t1", true, "ObjectEvent", "OBSERVE", "2026-11-19T23:30:00-02:00", cbv(SHIPPING), RP);
    index("epcis-event-2026-11", "t1", true, "AggregationEvent", "ADD", "2026-11-18T08:00:00Z", cbv(SHIPPING), null);
    index("epcis-event-2026-11", "t1", true, "ObjectEvent", "OBSERVE", "2026-11-18T09:00:00Z", cbv(RECEIVING), RP);
    index("epcis-event-2026-10", "t1", true, "ObjectEvent", "OBSERVE", "2026-10-31T12:00:00Z", Map.of("asCaptured", CUSTOM, "asURI", CUSTOM), null);
    index("epcis-event-2026-10", "t1", true, "TransformationEvent", null, "2026-10-02T12:00:00Z", null, null);
    index("epcis-event-2026-11", "t1", false, "ObjectEvent", "DELETE", "2026-11-19T11:00:00Z", cbv(SHIPPING), RP);
    index("epcis-event-2026-11", "t2", true, "ObjectEvent", "OBSERVE", "2026-11-19T12:00:00Z", cbv(SHIPPING), RP);
    index("epcis-event-2026-11", "t2", true, "ObjectEvent", "OBSERVE", "2026-11-19T13:00:00Z", cbv(SHIPPING), RP);
    client.indices().refresh(r -> r.index("epcis-event-*"));

    final RepositoryConfiguration config = mock(RepositoryConfiguration.class, RETURNS_DEEP_STUBS);
    when(config.epcis().event().index()).thenReturn("epcis-event");
    contextAware = mock(ContextAwareOpenSearchClient.class);
    when(contextAware.getAsyncClient()).thenReturn(new OpenSearchAsyncClient(transport));
    queries =
        new QueryRepositoryImpl(
            new ObjectMapper(), new QueryFactory(new FilterFactory()), config, contextAware, null, null, null);
  }

  private static Map<String, Object> cbv(final String urn) {
    return Map.of("asCaptured", urn, "asURN", urn);
  }

  private static void index(
      final String index,
      final String tenant,
      final boolean visible,
      final String type,
      final String action,
      final String eventTime,
      final Map<String, Object> bizStep,
      final String readPoint)
      throws IOException {
    final Map<String, Object> doc = new HashMap<>();
    doc.put("eventID", "ni:///sha-256;" + (++sequence) + "?ver=CBV2.0");
    doc.put("type", type);
    doc.put("eventTime", eventTime);
    if (action != null) doc.put("action", action);
    if (bizStep != null) doc.put("bizStep", bizStep);
    if (readPoint != null) doc.put("readPoint", Map.of("id", Map.of("asCaptured", readPoint, "asURN", readPoint)));
    doc.put("metadata", List.of(Map.of("visible", visible, "capturedBy", Map.of("defaultGroup", tenant))));
    client.index(
        new IndexRequest.Builder<Map<String, Object>>().index(index).document(doc).refresh(Refresh.False).build());
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

  /** The parameters as GET /events sends them: visible=true always, plus the caller's filters. */
  private static SimpleEventQueryRequest request(final Map<String, Object> params) {
    final Map<String, Object> all = new HashMap<>(params);
    all.put("visible", true);
    final List<QueryParam> list = new ArrayList<>();
    final QueryParamMapper mapper = new QueryParamMapper(true, null, Optional.empty());
    all.entrySet().forEach(e -> list.add(mapper.apply(e)));
    return new SimpleEventQueryRequest(list);
  }

  private static EventAggregation aggregate(
      final Map<String, Object> params, final EventGroupBy groupBy, final int size, final String tenant) {
    return queries
        .aggregateEvents(request(params), groupBy, size, false, Optional.ofNullable(tenant))
        .await()
        .atMost(Duration.ofSeconds(30));
  }

  @Test
  @DisplayName("bizStep: URN buckets, the as-captured fallback, hidden and other tenants left out")
  void bizStepForOneTenant() {
    final EventAggregation result = aggregate(Map.of(), EventGroupBy.bizStep, 20, "t1");
    assertEquals("bizStep", result.groupBy());
    assertEquals(6, result.total(), "six visible t1 events, the one without bizStep included");
    assertEquals(
        List.of(new Bucket(SHIPPING, 3), new Bucket(CUSTOM, 1), new Bucket(RECEIVING, 1)),
        result.buckets());
    assertEquals(0, result.otherCount());
    verify(contextAware, never()).getAsyncClient("admin");
  }

  @Test
  @DisplayName("size cuts the buckets and the rest is otherCount")
  void sizeAndOtherCount() {
    final EventAggregation result = aggregate(Map.of(), EventGroupBy.bizStep, 1, "t1");
    assertEquals(List.of(new Bucket(SHIPPING, 3)), result.buckets());
    assertEquals(2, result.otherCount());
  }

  @Test
  @DisplayName("without a tenant named, what the client may see is counted (DLS decides in production)")
  void allTenants() {
    final EventAggregation result = aggregate(Map.of(), EventGroupBy.bizStep, 20, null);
    assertEquals(8, result.total());
    assertEquals(new Bucket(SHIPPING, 5), result.buckets().get(0));
  }

  @Test
  @DisplayName("the /events filters apply: EQ_bizStep and GE_eventTime")
  void filtersApply() {
    assertEquals(
        List.of(new Bucket("ObjectEvent", 2), new Bucket("AggregationEvent", 1)),
        aggregate(Map.of("EQ_bizStep", List.of(SHIPPING)), EventGroupBy.eventType, 20, "t1").buckets());
    // two of t1 (the hidden one not), two of t2
    assertEquals(
        4,
        aggregate(Map.of("GE_eventTime", "2026-11-19T00:00:00Z"), EventGroupBy.eventType, 20, null).total());
  }

  @Test
  @DisplayName("eventType, action and readPoint group on their stored values")
  void otherTerms() {
    assertEquals(
        List.of(new Bucket("ObjectEvent", 4), new Bucket("AggregationEvent", 1), new Bucket("TransformationEvent", 1)),
        aggregate(Map.of(), EventGroupBy.eventType, 20, "t1").buckets());
    assertEquals(
        List.of(new Bucket("OBSERVE", 4), new Bucket("ADD", 1)),
        aggregate(Map.of(), EventGroupBy.action, 20, "t1").buckets());
    assertEquals(List.of(new Bucket(RP, 3)), aggregate(Map.of(), EventGroupBy.readPoint, 20, "t1").buckets());
    assertEquals(List.of(), aggregate(Map.of(), EventGroupBy.bizLocation, 20, "t1").buckets());
  }

  @Test
  @DisplayName("day and month are calendar buckets in UTC, oldest first, with no otherCount")
  void histograms() {
    // 2026-11-19T23:30-02:00 is 2026-11-20 in UTC
    final EventAggregation days = aggregate(Map.of(), EventGroupBy.day, 1, "t1");
    assertEquals(
        List.of(
            new Bucket("2026-10-02", 1),
            new Bucket("2026-10-31", 1),
            new Bucket("2026-11-18", 2),
            new Bucket("2026-11-19", 1),
            new Bucket("2026-11-20", 1)),
        days.buckets());
    assertEquals(0, days.otherCount());
    assertEquals(
        List.of(new Bucket("2026-10", 2), new Bucket("2026-11", 4)),
        aggregate(Map.of(), EventGroupBy.month, 20, "t1").buckets());
  }
}
