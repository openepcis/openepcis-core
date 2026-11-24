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
import io.openepcis.repository.model.EPCISEventES;
import io.openepcis.repository.model.ErrorDeclarationES;
import java.io.IOException;
import java.io.StringReader;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.apache.hc.core5.http.HttpHost;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
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
 * Whether a stored correction can be found again.
 *
 * <p>Four EPCIS query parameters exist for corrections —
 * {@code EXISTS_errorDeclaration}, {@code GE/LT_errorDeclaration_Time},
 * {@code EQ_errorReason}, {@code EQ_correctiveEventID} — and all four address
 * {@code errorDeclaration.*}, the name the index template maps. The Java field
 * is called {@code errorDeclarationES} and carried no {@code @JsonProperty}, so
 * that is the name every stored correction went in under. Nothing matched, and
 * a search that matches nothing answers "no results" rather than "I could not
 * look": the correction path was blind and said nothing about it.
 *
 * <p>Both halves are pinned here. The second test indexes a document under the
 * old name and shows all four queries missing it — so if the annotation is ever
 * dropped, this fails instead of the field quietly disappearing again.
 */
class ErrorDeclarationVisibilityTest {

    private static final DockerImageName IMAGE =
            DockerImageName.parse("opensearchproject/opensearch:2.18.0");

    // Named the way RollingIndexUtil names it: prefix, tenant group, year, month.
    // The group is part of the index name, and it is also what the tenant's DLS
    // rule matches on — both halves have to be in the test or it is not the
    // query the repository actually runs.
    private static final String TENANT = "acme";
    private static final String INDEX = "epcis-event-" + TENANT + "-2026-08";
    /** The same month as a displaced template would have left it. */
    private static final String FLAT_INDEX = "epcis-event-" + TENANT + "-2026-07";

    /**
     * The error-declaration slice of epcis-event-template.json, verbatim, plus
     * the {@code metadata} shape the tenant's DLS rule needs.
     *
     * <p>{@code metadata} is <b>nested</b> here because the role's DLS rule is a
     * nested query on {@code metadata.capturedBy.defaultGroup}. Where a
     * displaced template left it mapped as a plain object, that query fails at
     * the shard and the search answers 200 with no hits — which is how four
     * months of events went missing without an error anywhere.
     */
    private static final String MAPPING = """
            {"properties":{
              "eventID":{"type":"keyword"},
              "metadata":{"type":"nested","properties":{
                "capturedBy":{"type":"object","properties":{
                  "defaultGroup":{"type":"keyword","store":true}}}}},
              "errorDeclaration":{"type":"nested","properties":{
                "correctiveEventIDs":{"type":"keyword","store":true},
                "declarationTime":{"type":"date","store":true},
                "reason":{"type":"object","properties":{
                  "asCaptured":{"type":"keyword","store":true},
                  "asURN":{"type":"keyword","store":true},
                  "asURI":{"type":"keyword","store":true},
                  "asBareString":{"type":"keyword","store":true}}}}}}}""";

    private static final String CORRECTED = "ni:///sha-256;"
            + "f895a478db42352042ceb07ac96a607765697ca0a8e6c3fe76cbdefc537d185a?ver=CBV2.0";
    private static final String CORRECTIVE = "ni:///sha-256;"
            + "5262521a4ee6da013088502fc2a67275e8cec723b804adf597f886cda2c4a15a?ver=CBV2.0";
    private static final OffsetDateTime DECLARED = OffsetDateTime.parse("2026-08-29T10:00:00Z");

    private static GenericContainer<?> opensearch;
    private static OpenSearchClient client;
    // The dates have to leave as ISO-8601, the way the repository's own mapper
    // writes them (JSONUtil): as epoch numbers a date-mapped field reads them
    // as milliseconds and puts every declaration in 1970.
    private static final ObjectMapper JSON = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
            .configure(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS,
                    false);

    @BeforeAll
    static void startOpenSearch() throws IOException {
        Assumptions.assumeTrue(dockerAvailable(), "no container runtime available — skipping");

        opensearch = new GenericContainer<>(IMAGE)
                .withExposedPorts(9200)
                .withEnv("discovery.type", "single-node")
                .withEnv("DISABLE_SECURITY_PLUGIN", "true")
                .withEnv("DISABLE_INSTALL_DEMO_CONFIG", "true")
                .withEnv("OPENSEARCH_JAVA_OPTS", "-Xms512m -Xmx512m")
                .waitingFor(Wait.forHttp("/").forStatusCode(200))
                .withStartupTimeout(Duration.ofMinutes(5));
        opensearch.start();

        client = new OpenSearchClient(ApacheHttpClient5TransportBuilder
                .builder(new HttpHost("http", opensearch.getHost(), opensearch.getMappedPort(9200)))
                .setMapper(new JacksonJsonpMapper())
                .build());

        client.indices().create(c -> c.index(INDEX).mappings(m -> m.withJson(new StringReader(MAPPING))));
        // metadata as a plain object, which is what a catch-all template at a
        // higher priority leaves behind.
        client.indices().create(c -> c.index(FLAT_INDEX)
                .mappings(m -> m.withJson(new StringReader(MAPPING.replace("\"metadata\":{\"type\":\"nested\"",
                        "\"metadata\":{\"type\":\"object\"")))));

        // The model as it is serialised on the way into the index. Not a
        // hand-written document: the point is what the model actually writes.
        final EPCISEventES event = new EPCISEventES();
        event.setEventID(CORRECTED);
        final ErrorDeclarationES declaration = new ErrorDeclarationES();
        declaration.setDeclarationTime(DECLARED);
        declaration.setCorrectiveEventIDs(List.of(CORRECTIVE));
        event.setErrorDeclarationES(declaration);
        final Map<String, Object> document = new java.util.HashMap<>(JSON.convertValue(event, Map.class));
        document.put("metadata", List.of(Map.of("capturedBy", Map.of("defaultGroup", TENANT))));
        index(document);

        // And the shape corrections used to be stored in, for the second test.
        // The same correction again, in the month whose metadata is flat. It is
        // there, it belongs to the tenant, and no query will ever reach it.
        client.index(new IndexRequest.Builder<Map<String, Object>>()
                .index(FLAT_INDEX)
                .document(Map.of(
                        "eventID", "ni:///sha-256;lostmonth?ver=CBV2.0",
                        "metadata", List.of(Map.of("capturedBy", Map.of("defaultGroup", TENANT))),
                        "errorDeclaration", Map.of(
                                "declarationTime", DECLARED.toString(),
                                "correctiveEventIDs", List.of(CORRECTIVE))))
                .refresh(Refresh.True).build());

        index(Map.of(
                "eventID", "ni:///sha-256;old?ver=CBV2.0",
                "metadata", List.of(Map.of("capturedBy", Map.of("defaultGroup", TENANT))),
                "errorDeclarationES", Map.of(
                        "declarationTime", DECLARED.toString(),
                        "correctiveEventIDs", List.of(CORRECTIVE))));
    }

    @SuppressWarnings("unchecked")
    private static void index(Map<String, Object> document) throws IOException {
        client.index(new IndexRequest.Builder<Map<String, Object>>()
                .index(INDEX).document(document).refresh(Refresh.True).build());
    }

    @AfterAll
    static void stopOpenSearch() {
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

    /**
     * The tenant's document-level security rule, as the role carries it.
     *
     * <p>Every query below runs inside it, because on this platform no query
     * ever runs without it: OpenSearch ANDs the role's DLS onto whatever was
     * asked. A correction query that works on a bare index and not under DLS
     * would be green here and useless in production.
     */
    private static Query asTenant(Query query) {
        return Query.of(q -> q.bool(b -> b
                .filter(f -> f.nested(n -> n.path("metadata")
                        .query(i -> i.term(t -> t
                                .field("metadata.capturedBy.defaultGroup")
                                .value(v -> v.stringValue(TENANT))))))
                .filter(query)));
    }

    /** The four queries QueryParamName builds for a correction, as it builds them. */
    private static Query exists() {
        return Query.of(q -> q.nested(n -> n.path("errorDeclaration")
                .query(i -> i.exists(e -> e.field("errorDeclaration")))));
    }

    private static Query declaredFrom(String from) {
        return Query.of(q -> q.nested(n -> n.path("errorDeclaration")
                .query(i -> i.range(r -> r.field("errorDeclaration.declarationTime")
                        .gte(org.opensearch.client.json.JsonData.of(from))))));
    }

    private static Query correctiveEventId(String id) {
        return Query.of(q -> q.nested(n -> n.path("errorDeclaration")
                .query(i -> i.term(t -> t.field("errorDeclaration.correctiveEventIDs").value(v -> v.stringValue(id))))));
    }

    private static long hits(Query query) throws IOException {
        final SearchResponse<Map> response =
                client.search(s -> s.index(INDEX).query(asTenant(query)), Map.class);
        if (response.shards().failed() > 0) {
            throw new AssertionError("shards failed: " + response.shards().failures());
        }
        return response.hits().total().value();
    }

    @Test
    @DisplayName("a stored correction answers all four of the queries meant to find it")
    void aCorrectionIsFindable() throws IOException {
        assertEquals(1, hits(exists()), "EXISTS_errorDeclaration");
        assertEquals(1, hits(declaredFrom("2026-08-29T00:00:00Z")), "GE_errorDeclaration_Time");
        assertEquals(0, hits(declaredFrom("2026-08-30T00:00:00Z")),
                "and the range actually bites, or the line above proves nothing");
        assertEquals(1, hits(correctiveEventId(CORRECTIVE)), "EQ_correctiveEventID");
        assertEquals(0, hits(correctiveEventId(CORRECTED)),
                "the corrected event is not one of its own corrective events");
    }

    @Test
    @DisplayName("across months, one bad mapping loses its month and says so only in _shards")
    void aFlatMetadataMappingLosesItsMonthQuietly() throws IOException {
        // The second way a correction disappears, and it has nothing to do with
        // the field name. The tenant's DLS rule is a nested query on metadata,
        // and a month whose template left metadata as a plain object cannot
        // answer it. Asked for that month alone, OpenSearch says "all shards
        // failed" and the caller notices. Asked across months — which is how
        // the repository asks, epcis-event-<tenant>-* — the good months answer,
        // the bad one fails, and the reply is a cheerful 200 with fewer hits
        // than there are events. That is how four months went missing.
        final SearchResponse<Map> response = client.search(s -> s
                .index("epcis-event-" + TENANT + "-*")
                .query(asTenant(exists()))
                .ignoreUnavailable(true), Map.class);

        assertEquals(1, response.hits().total().value(),
                "the correction from the well-mapped month, and only that one");
        assertEquals(1, response.shards().failed(),
                "the other month failed, and only _shards says so");
    }

    @Test
    @DisplayName("stored under the field's Java name, the same correction answers none of them")
    void theOldShapeIsInvisible() throws IOException {
        // Exactly one document carries errorDeclaration; the other carries
        // errorDeclarationES and is counted by nothing above. That difference is
        // the whole bug: it was stored, and it could not be found.
        assertEquals(1, hits(exists()));
        assertEquals(1, hits(correctiveEventId(CORRECTIVE)));
        assertEquals(2,
                client.search(s -> s.index(INDEX).query(asTenant(Query.of(q -> q.matchAll(m -> m)))),
                        Map.class).hits().total().value(),
                "both documents are in the index and both belong to the tenant — "
                        + "only one of them can be asked about");
    }
}
