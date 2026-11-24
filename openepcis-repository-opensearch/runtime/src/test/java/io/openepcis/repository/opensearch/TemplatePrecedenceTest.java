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
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openepcis.repository.opensearch.configuration.OpenSearchInitializer;
import java.io.IOException;
import java.io.StringReader;
import java.time.Duration;
import org.apache.hc.core5.http.HttpHost;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * Why these templates are composable rather than legacy.
 *
 * <p>A cluster grows a catch-all composable template sooner or later, for
 * perfectly innocent reasons — the one that caused this was a {@code *} template
 * at priority 1 whose entire content was {@code number_of_replicas: 0}. The
 * moment it appeared, every legacy template stopped applying, because a matching
 * composable template does not merge with legacy ones, it replaces them. New
 * monthly indices then came out with dynamic mappings: {@code metadata} an object
 * instead of nested. The document-level security rule is a nested query on
 * {@code metadata}, so every search against those indices failed at the shard
 * level, and four months of captures went into indices nothing could read.
 *
 * <p>These two tests are the before and after of that, run against a real
 * OpenSearch, so that a future move back to the legacy API fails here.
 */
class TemplatePrecedenceTest {

    private static final DockerImageName IMAGE =
            DockerImageName.parse("opensearchproject/opensearch:2.18.0");

    /** The mapping that matters: metadata has to be nested for the DLS rule to work. */
    private static final String NESTED_METADATA_MAPPING = """
            {"properties":{"metadata":{"type":"nested",
             "properties":{"visible":{"type":"boolean"}}}}}""";

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

        // What a cluster acquires without anybody thinking about EPCIS: a template
        // matching everything, whose entire content is a replica count.
        client.indices().putIndexTemplate(b -> b
                .name("catch-all-defaults")
                .indexPatterns("*")
                .priority(1)
                .template(t -> t.settings(settings -> settings.numberOfReplicas(0))));
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

    /** How the field ends up mapped on a freshly created index. */
    private static String metadataTypeOf(final String index) throws IOException {
        client.indices().create(c -> c.index(index));
        final var properties = client.indices().getMapping(m -> m.index(index))
                .get(index).mappings().properties();
        final var metadata = properties.get("metadata");
        return metadata == null ? "absent" : metadata._kind().jsonValue();
    }

    @Test
    @DisplayName("a legacy template loses to the catch-all — this is the bug that hid for four months")
    void legacyTemplateIsSuppressed() throws IOException {
        client.indices().putTemplate(b -> b
                .name("epcis-event-legacy")
                .indexPatterns("legacy-probe-*")
                .mappings(m -> m.withJson(new StringReader(NESTED_METADATA_MAPPING))));

        assertEquals("absent", metadataTypeOf("legacy-probe-2026-08"),
                "if this ever says nested, this OpenSearch has changed how legacy templates rank "
                        + "and the composable switch can be reconsidered");
    }

    @Test
    @DisplayName("a legacy template of the same name does not stand in for a composable one")
    void legacyDoesNotSatisfyTheComposableCheck() throws IOException {
        // The trap that made the whole switch a no-op on every existing cluster:
        // the initializer asked whether the template existed, got "yes" from the
        // legacy namespace, and skipped creating the composable one. The two
        // namespaces are separate, and only the composable one decides a mapping
        // once any composable template matches the pattern.
        final String name = "namespace-probe";
        client.indices().putTemplate(b -> b
                .name(name)
                .indexPatterns("namespace-probe-*")
                .mappings(m -> m.withJson(new StringReader(NESTED_METADATA_MAPPING))));

        assertTrue(client.indices().existsTemplate(b -> b.name(name)).value(),
                "the legacy template is there");
        assertFalse(client.indices().existsIndexTemplate(b -> b.name(name)).value(),
                "and the composable one is not - asking the wrong namespace is what hid this");
    }

    @Test
    @DisplayName("a composable template at our priority wins, and metadata comes out nested")
    void composableTemplateWins() throws IOException {
        client.indices().putIndexTemplate(b -> b
                .name("epcis-event-composable")
                .indexPatterns("composable-probe-*")
                // The production constant, not a literal: lowering it there has to fail here.
                .priority(OpenSearchInitializer.TEMPLATE_PRIORITY)
                .template(t -> t.mappings(m -> m.withJson(new StringReader(NESTED_METADATA_MAPPING)))));

        assertEquals("nested", metadataTypeOf("composable-probe-2026-08"),
                "without this the document-level security rule cannot query the index at all");
    }
}
