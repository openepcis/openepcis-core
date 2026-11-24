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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openepcis.query.data.QueryParam;
import io.openepcis.query.factory.FilterFactory;
import io.openepcis.query.factory.QueryFactory;
import io.openepcis.query.factory.QueryParamMapper;
import io.openepcis.repository.opensearch.util.QueryConverterOpenSearchJC;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.time.Duration;
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
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.Refresh;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * The error-declaration query parameters as the repository builds them, against the index
 * template the repository installs.
 *
 * <p>{@link ErrorDeclarationVisibilityTest} hand-builds its queries, so it never saw that the real
 * {@code EXISTS_errorDeclaration} was a root-level exists on a nested field, which matches nothing,
 * nor that its value failed validation as "not a number" whether it was empty (EPCIS 2.0: Void) or
 * {@code true} (GS1 OpenAPI). Here the parameter goes through QueryParamMapper and QueryFactory
 * the way a request does.
 */
class ErrorDeclarationQueryBuilderTest {

  private static final DockerImageName IMAGE =
      DockerImageName.parse("opensearchproject/opensearch:2.18.0");
  private static final String INDEX = "epcis-event-minecraft-2026-11";
  private static final String EVENT_ID = "ni:///sha-256;a3a4?ver=CBV2.0";
  private static final String CORRECTIVE = "ni:///sha-256;f7cd?ver=CBV2.0";

  private static GenericContainer<?> opensearch;
  private static OpenSearchClient client;

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
    client =
        new OpenSearchClient(
            ApacheHttpClient5TransportBuilder.builder(
                    new HttpHost("http", opensearch.getHost(), opensearch.getMappedPort(9200)))
                .setMapper(new JacksonJsonpMapper())
                .build());

    final String mappings;
    try (InputStream in =
        ErrorDeclarationQueryBuilderTest.class.getResourceAsStream(
            "/templates/opensearch-indices/epcis-event-template.json")) {
      final ObjectMapper json = new ObjectMapper();
      mappings = json.writeValueAsString(json.readTree(in).get("mappings"));
    }
    client.indices().create(c -> c.index(INDEX).mappings(m -> m.withJson(new StringReader(mappings))));

    final Map<String, Object> metadata =
        Map.of("visible", true, "capturedBy", Map.of("defaultGroup", "minecraft"));
    // the erroneous event, its declaration, and an event that has nothing to do with either
    index(Map.of("eventID", EVENT_ID, "hash", EVENT_ID, "recordTime", "2026-11-16T12:00:00Z", "metadata", List.of(metadata)));
    index(
        Map.of(
            "eventID", EVENT_ID,
            "hash", EVENT_ID,
            "recordTime", "2026-11-16T14:45:02Z",
            "metadata", List.of(metadata),
            "errorDeclaration",
                Map.of(
                    "declarationTime", "2026-11-11T16:00:00Z",
                    "reason",
                        Map.of(
                            "asCaptured", "incorrect_data",
                            "asURN", "urn:epcglobal:cbv:er:incorrect_data",
                            "asURI", "https://ref.gs1.org/cbv/ER-incorrect_data"),
                    "correctiveEventIDs", List.of(CORRECTIVE))));
    index(Map.of("eventID", CORRECTIVE, "hash", CORRECTIVE, "recordTime", "2026-11-16T14:45:02Z", "metadata", List.of(metadata)));
    // the same content captured by another tenant
    index(
        Map.of(
            "eventID", EVENT_ID,
            "hash", EVENT_ID,
            "recordTime", "2026-11-16T12:00:00Z",
            "metadata", List.of(Map.of("visible", true, "capturedBy", Map.of("defaultGroup", "other")))));
  }

  private static void index(Map<String, Object> document) throws IOException {
    client.index(
        new IndexRequest.Builder<Map<String, Object>>()
            .index(INDEX)
            .document(document)
            .refresh(Refresh.True)
            .build());
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

  @SuppressWarnings("rawtypes")
  private static long hits(Query query) throws IOException {
    final SearchResponse<Map> response = client.search(s -> s.index(INDEX).query(query), Map.class);
    assertEquals(0, response.shards().failed(), "shards failed: " + response.shards().failures());
    return response.hits().total().value();
  }

  /** One parameter, through the mapper and the factory, as a request would send it. */
  private static long hits(String name, Object value) throws IOException {
    final QueryParam param =
        new QueryParamMapper(true, null, Optional.empty()).apply(Map.entry(name, value));
    final QueryFactory factory = new QueryFactory(new FilterFactory());
    return hits(QueryConverterOpenSearchJC.convert(factory.createMainQuery(List.of(param)).build()));
  }

  @Test
  @DisplayName("EXISTS_errorDeclaration finds the declaration, with any value or none")
  void existsFindsTheDeclaration() throws IOException {
    assertEquals(1, hits("EXISTS_errorDeclaration", List.of("")), "Void, as EPCIS 2.0 types it");
    assertEquals(1, hits("EXISTS_errorDeclaration", List.of("true")), "true, as the GS1 OpenAPI sends it");
    assertEquals(4, hits("EXISTS_errorDeclaration", List.of("false")), "false: regardless of a declaration");
  }

  @Test
  @DisplayName("EQ_errorReason, EQ_correctiveEventID and GE_errorDeclaration_Time find only the declaration")
  void theOtherThreeFindOnlyTheDeclaration() throws IOException {
    assertEquals(1, hits("EQ_errorReason", List.of("incorrect_data")));
    assertEquals(0, hits("EQ_errorReason", List.of("did_not_occur")));
    assertEquals(1, hits("EQ_correctiveEventID", List.of(CORRECTIVE)));
    assertEquals(1, hits("GE_errorDeclaration_Time", List.of("2026-11-11T00:00:00Z")));
    assertEquals(0, hits("GE_errorDeclaration_Time", List.of("2026-11-12T00:00:00Z")));
  }

  @Test
  @DisplayName("the lookup a declaration is matched with sees the tenant's own events only")
  void tenantFilterKeepsOtherTenantsOut() throws IOException {
    final Query byHash =
        Query.of(q -> q.term(t -> t.field("hash").value(FieldValue.of(EVENT_ID))));
    assertEquals(3, hits(byHash), "both tenants");
    assertEquals(
        2,
        hits(
            Query.of(
                q ->
                    q.bool(
                        b ->
                            b.filter(byHash)
                                .filter(QueryRepositoryImpl.tenantFilter("minecraft"))))),
        "the original and its declaration, not the other tenant's copy");
  }
}
