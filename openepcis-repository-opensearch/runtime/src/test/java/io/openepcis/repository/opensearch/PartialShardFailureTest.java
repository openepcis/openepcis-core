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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.StringReader;
import java.time.Duration;
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
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * The shape of the answer that hid a four-month outage.
 *
 * <p>Search one index whose mapping suits the query and one whose does not, and
 * OpenSearch replies <em>200 OK</em> with the hits it managed and a quiet note
 * that some shards failed. Read only the hits and "I could not look" becomes
 * "there is nothing" — the two most different answers a search can give, told
 * apart by a field nobody was reading.
 *
 * <p>This reproduces exactly the production shape: a nested query on
 * {@code metadata}, one index where {@code metadata} is nested and one where it is
 * a plain object, searched together through a wildcard.
 */
class PartialShardFailureTest {

    private static final DockerImageName IMAGE =
            DockerImageName.parse("opensearchproject/opensearch:2.18.0");

    private static final String GOOD = "shardprobe-good";
    private static final String BAD = "shardprobe-bad";

    private static GenericContainer<?> opensearch;
    private static OpenSearchClient client;

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

        // The index as the template intends it.
        client.indices().create(c -> c.index(GOOD).mappings(m -> m.withJson(new StringReader("""
                {"properties":{"metadata":{"type":"nested","properties":{"visible":{"type":"boolean"}}}}}"""))));
        // And the index a displaced template leaves behind.
        client.indices().create(c -> c.index(BAD).mappings(m -> m.withJson(new StringReader("""
                {"properties":{"metadata":{"type":"object","properties":{"visible":{"type":"boolean"}}}}}"""))));

        client.index(new IndexRequest.Builder<Map<String, Object>>().index(GOOD)
                .document(Map.of("metadata", Map.of("visible", true))).refresh(Refresh.True).build());
        client.index(new IndexRequest.Builder<Map<String, Object>>().index(BAD)
                .document(Map.of("metadata", Map.of("visible", true))).refresh(Refresh.True).build());
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

    @Test
    @DisplayName("a nested query across both indices succeeds, partly — and says so only in _shards")
    void partialFailureLooksLikeSuccess() throws IOException {
        final SearchResponse<Map> response = client.search(s -> s
                .index("shardprobe-*")
                .query(q -> q.nested(n -> n.path("metadata")
                        .query(inner -> inner.term(t -> t.field("metadata.visible").value(v -> v.booleanValue(true))))))
                .ignoreUnavailable(true), Map.class);

        // This is the trap, stated as an assertion: hits present, failures present,
        // status 200. Anything reading only the hits calls this an answer.
        assertEquals(1, response.hits().hits().size(), "the healthy index still answers");
        assertTrue(response.shards().failed() > 0, "and the other one did not");
        assertTrue(response.shards().failures().get(0).reason().reason().contains("nested"),
                "the cluster names the reason precisely — it was never silent, we were");
    }

    @Test
    @DisplayName("requireAllShards refuses such an answer instead of passing it on as a result")
    void guardRefusesPartialResults() throws IOException {
        final SearchResponse<Map> response = client.search(s -> s
                .index("shardprobe-*")
                .query(q -> q.nested(n -> n.path("metadata")
                        .query(inner -> inner.term(t -> t.field("metadata.visible").value(v -> v.booleanValue(true))))))
                .ignoreUnavailable(true), Map.class);

        final var thrown = org.junit.jupiter.api.Assertions.assertThrows(
                io.openepcis.model.epcis.exception.QueryExecutionException.class,
                () -> QueryRepositoryImpl.requireAllShards(response.shards(), "the event query"));

        assertTrue(thrown.getMessage().contains("shards failed"),
                () -> "the message has to say what went wrong, was: " + thrown.getMessage());
    }

    @Test
    @DisplayName("a PIT over a shard that cannot answer is partial by default, and refused with allowPartialPitCreation(false)")
    void pitWithoutAllShards() throws IOException {
        // one healthy index and one whose only primary can never be allocated
        client.indices().create(c -> c.index("pitprobe-good"));
        client.index(new IndexRequest.Builder<Map<String, Object>>().index("pitprobe-good")
                .document(Map.of("n", 1)).refresh(Refresh.True).build());
        client.indices().create(c -> c.index("pitprobe-red").waitForActiveShards(w -> w.count(0))
                .settings(st -> st.numberOfReplicas(0)
                        .routing(r -> r.allocation(a -> a.include(i -> i.id("no-such-node"))))));

        final var partial = client.createPit(p -> p.index("pitprobe-*")
                .keepAlive(t -> t.time("30s")).allowPartialPitCreation(true));
        // the trap again: an id, a 200, and a shard count that is short
        assertTrue(partial.shards().failed() > 0 || partial.shards().successful() < partial.shards().total(),
                () -> "expected a partial PIT, shards were " + partial.shards());
        client.deletePit(d -> d.pitId(partial.pitId()));

        org.junit.jupiter.api.Assertions.assertThrows(Exception.class,
                () -> client.createPit(p -> p.index("pitprobe-*")
                        .keepAlive(t -> t.time("30s")).allowPartialPitCreation(false)),
                "without partial creation the PIT has to fail instead");
    }
}
