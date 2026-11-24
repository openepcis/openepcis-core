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

import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch.OpenSearchClient;

/**
 * Picks the OpenSearch client a repository call runs with.
 *
 * <p>Which edition is running decides the implementation. The Community Edition is single-tenant
 * and ships {@link SingleClientOpenSearchClient}, which always answers with the one configured
 * client. The Business edition adds a bean that forwards the caller's credentials so that the
 * OpenSearch security plugin can apply document-level security; being a plain bean it replaces the
 * {@code @DefaultBean} without any further switch.
 */
public interface ContextAwareOpenSearchClient {

  /** The async client for the current call. */
  OpenSearchAsyncClient getAsyncClient();

  /** The blocking client for the current call. */
  OpenSearchClient getClient();

  /** The async client registered under {@code clientName}, as far as the edition distinguishes them. */
  OpenSearchAsyncClient getAsyncClient(String clientName);

  /** The blocking client registered under {@code clientName}, as far as the edition distinguishes them. */
  OpenSearchClient getClient(String clientName);

  /** Whether a CDI request context is active, i.e. the call serves an HTTP request. */
  boolean isRequestContextActive();
}
