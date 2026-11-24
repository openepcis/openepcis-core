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

import io.quarkus.arc.Arc;
import io.quarkus.arc.ArcContainer;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch.OpenSearchClient;

/**
 * The single-tenant client selection of the Community Edition: every call, whether it serves an
 * HTTP request or a Kafka Streams thread, runs with the one configured default client. There is no
 * per-caller credential forwarding and therefore no document-level security; all data in the
 * cluster is visible to every authenticated caller of the service.
 *
 * <p>Being the {@code @DefaultBean}, this implementation steps aside as soon as another
 * {@link ContextAwareOpenSearchClient} bean is on the classpath.
 */
@DefaultBean
@ApplicationScoped
public class SingleClientOpenSearchClient implements ContextAwareOpenSearchClient {

  private final OpenSearchAsyncClient asyncClient;
  private final OpenSearchClient client;

  @Inject
  public SingleClientOpenSearchClient(
      final OpenSearchAsyncClient asyncClient, final OpenSearchClient client) {
    this.asyncClient = asyncClient;
    this.client = client;
  }

  @Override
  public OpenSearchAsyncClient getAsyncClient() {
    return asyncClient;
  }

  @Override
  public OpenSearchClient getClient() {
    return client;
  }

  @Override
  public OpenSearchAsyncClient getAsyncClient(final String clientName) {
    return asyncClient;
  }

  @Override
  public OpenSearchClient getClient(final String clientName) {
    return client;
  }

  @Override
  public boolean isRequestContextActive() {
    final ArcContainer container = Arc.container();
    return container != null && container.requestContext().isActive();
  }
}
