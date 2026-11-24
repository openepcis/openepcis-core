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
package io.openepcis.subscription.delivery;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openepcis.model.epcis.DeliveryInfo;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The destination policy is asked again right before each delivery. */
class HttpQueryDeliveryEndpointTest {

  @Test
  @DisplayName("a destination the policy refuses at delivery time is not posted to")
  @SuppressWarnings("unchecked")
  void refusedDestinationIsNotPostedTo() throws Exception {
    final AtomicReference<Optional<String>> verdict = new AtomicReference<>(Optional.of("refused"));
    final HttpClient client = mock(HttpClient.class);
    final HttpResponse<Object> ok = mock(HttpResponse.class);
    when(ok.statusCode()).thenReturn(202);
    when(client.send(any(HttpRequest.class), any())).thenReturn(ok);
    final HttpQueryDeliveryEndpoint endpoint = new HttpQueryDeliveryEndpoint(client, destination -> verdict.get());
    final DeliveryInfo info =
        new DeliveryInfo("sub-1", Optional.empty(), URI.create("https://hooks.example.com/epcis"), "{}");

    endpoint.send(info);
    verify(client, never()).send(any(HttpRequest.class), any());

    verdict.set(Optional.empty());
    endpoint.send(info);
    verify(client, timeout(5000)).send(any(HttpRequest.class), any());
  }
}
