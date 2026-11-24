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

import java.io.IOException;
import org.apache.hc.core5.http.HttpHost;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
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
 * The one thing about capture that cannot be asserted against a stub: whether a
 * document is searchable by the time the write returns.
 *
 * <p>It matters because everything downstream of a capture <em>searches</em> for
 * what was just written. The visibility step counts the events of a capture job
 * and only then flips {@code metadata.visible}; {@code GET /capture/&#123;id&#125;} looks the
 * job up by id — also with a search. A search does not see an unrefreshed
 * document. Lose that race and the events stay invisible for good, because the
 * aggregation runs once per job and never asks again, while the job reports
 * success. That is exactly what was measured against the dev repository on
 * 2026-08-28: captures accepted and persisted since May, none of them findable.
 *
 * <p>The index here is given a refresh interval of a minute so the race is not a
 * race at all but a certainty — without a refresh policy the document is simply
 * not there, and with {@link Refresh#WaitFor} it is. Anyone who removes the
 * policy from the capture path will fail this test rather than discover it in
 * production three months later.
 */
class CaptureSearchableOnWriteTest {

    private static final DockerImageName IMAGE =
            DockerImageName.parse("opensearchproject/opensearch:2.18.0");

    private static final String INDEX = "capture-refresh-test";

    private static GenericContainer<?> opensearch;
    private static OpenSearchClient client;

    private static boolean dockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (RuntimeException e) {
            return false;
        }
    }

    @BeforeAll
    static void startOpenSearch() throws IOException {
        org.junit.jupiter.api.Assumptions.assumeTrue(
                dockerAvailable(), "no container runtime available — skipping");

        opensearch = new GenericContainer<>(IMAGE)
                .withExposedPorts(9200)
                .withEnv("discovery.type", "single-node")
                .withEnv("DISABLE_SECURITY_PLUGIN", "true")
                .withEnv("DISABLE_INSTALL_DEMO_CONFIG", "true")
                .withEnv("OPENSEARCH_JAVA_OPTS", "-Xms512m -Xmx512m")
                .waitingFor(Wait.forHttp("/").forStatusCode(200))
                .withStartupTimeout(java.time.Duration.ofMinutes(5));
        opensearch.start();

        client = new OpenSearchClient(
                ApacheHttpClient5TransportBuilder
                        .builder(new HttpHost("http", opensearch.getHost(), opensearch.getMappedPort(9200)))
                        .setMapper(new JacksonJsonpMapper())
                        .build());

        // Five seconds between refreshes. Long enough that a document written
        // without a policy is reliably not searchable a moment later — which is what
        // makes the first assertion a statement rather than a coin flip — and short
        // enough that WaitFor returns well inside the client's socket timeout.
        //
        // That second half is worth knowing in its own right: WaitFor does not force
        // a refresh, it rides the next scheduled one, so the write blocks for up to
        // one refresh interval. On the capture path that is paid by the background
        // pipeline rather than by the caller, who already has a 202 in hand. Raise an
        // index's refresh interval far enough, though, and it becomes visible in the
        // capture job write, which does sit in the request. A first draft of this
        // test used sixty seconds and ran straight into a thirty-second timeout.
        client.indices().create(c -> c.index(INDEX).settings(s -> s.refreshInterval(t -> t.time("5s"))));
    }

    @AfterAll
    static void stopOpenSearch() {
        if (opensearch != null) {
            opensearch.stop();
        }
    }

    private static long searchById(final String id) throws IOException {
        final SearchResponse<Map> response =
                client.search(s -> s.index(INDEX).query(q -> q.ids(i -> i.values(id))), Map.class);
        return response.hits().total().value();
    }

    @Test
    @DisplayName("without a refresh policy the document is not searchable yet — the hazard is real")
    void withoutRefreshPolicy() throws IOException {
        final String id = "plain-write";
        client.index(new IndexRequest.Builder<Map<String, Object>>()
                .index(INDEX).id(id).document(Map.of("captureID", id)).build());

        assertEquals(0L, searchById(id),
                "if this ever finds the document, the index is refreshing on its own and the "
                        + "rest of this test proves nothing — check the refresh interval above");
    }

    @Test
    @DisplayName("written the way capture writes, the document is searchable at once")
    void withTheCaptureRefreshPolicy() throws IOException {
        final String id = "capture-write";
        // EventRepositoryImpl.WRITE_REFRESH, not a literal: this test exists to hold
        // that decision, so weakening it there has to fail here.
        client.index(new IndexRequest.Builder<Map<String, Object>>()
                .index(INDEX).id(id).document(Map.of("captureID", id))
                .refresh(EventRepositoryImpl.WRITE_REFRESH).build());

        assertEquals(1L, searchById(id),
                "the guarantee the capture path depends on: what was just written can be found "
                        + "by the step that has to find it");
    }

    @Test
    @DisplayName("a get by id sees the capture job at once, refreshed or not — how GET /capture/{id} reads")
    void realtimeGetNeedsNoRefresh() throws IOException {
        final String id = "status-read";
        client.index(new IndexRequest.Builder<Map<String, Object>>()
                .index(INDEX).id(id).document(Map.of("captureID", id, "running", false)).build());

        assertEquals(0L, searchById(id), "not refreshed yet, so a search cannot see it");
        final var response = client.mget(m -> m
                .docs(d -> d.index(INDEX + "-missing").id(id))
                .docs(d -> d.index(INDEX).id(id)), Map.class);
        final long found = response.docs().stream()
                .filter(item -> item.isResult() && item.result().found())
                .count();
        assertEquals(1L, found,
                "a multi get is realtime and tolerates a month index that does not exist; "
                        + "the status a client polls right after capturing depends on both");
    }

    @Test
    @DisplayName("the capture job is looked up in this month's and last month's index of the tenant")
    void captureJobIndices() {
        assertEquals(List.of("capture-job-acme-2026-01", "capture-job-acme-2025-12"),
                QueryRepositoryImpl.captureJobIndices("capture-job", "acme",
                        java.time.OffsetDateTime.parse("2026-01-03T10:00:00Z")));
    }
}
