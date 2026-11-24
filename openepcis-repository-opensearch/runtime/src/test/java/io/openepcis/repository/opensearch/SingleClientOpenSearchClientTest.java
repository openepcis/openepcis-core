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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch.OpenSearchClient;

/** The Community Edition answers every client request with the one configured client. */
class SingleClientOpenSearchClientTest {

  private final OpenSearchAsyncClient async = mock(OpenSearchAsyncClient.class);
  private final OpenSearchClient blocking = mock(OpenSearchClient.class);
  private final SingleClientOpenSearchClient subject =
      new SingleClientOpenSearchClient(async, blocking);

  @Test
  @DisplayName("the default clients are returned as they are")
  void defaultClients() {
    assertSame(async, subject.getAsyncClient());
    assertSame(blocking, subject.getClient());
  }

  @Test
  @DisplayName("a client name does not select a different client")
  void namedClientsCollapse() {
    assertSame(async, subject.getAsyncClient("admin"));
    assertSame(blocking, subject.getClient("admin"));
    assertSame(blocking, subject.getClient("anything"));
  }

  @Test
  @DisplayName("outside a CDI container there is no request context, and asking does not fail")
  void noContainerNoContext() {
    assertFalse(subject.isRequestContextActive());
  }
}
