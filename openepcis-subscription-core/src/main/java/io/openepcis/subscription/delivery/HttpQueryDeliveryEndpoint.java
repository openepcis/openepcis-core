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

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import io.openepcis.model.epcis.DeliveryInfo;
import io.openepcis.model.epcis.constants.CommonConstants;
import io.openepcis.query.service.validator.WebhookDestinationPolicy;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

@ApplicationScoped
public class HttpQueryDeliveryEndpoint implements QueryDeliveryEndpoint {
  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(HttpQueryDeliveryEndpoint.class);
  private static final String APPLICATION_PROBLEM_JSON = "application/problem+json";
  private final HttpClient client;

  @Override
  public void send(DeliveryInfo deliveryInfo) {
    // Checked again right before posting, not only when subscribing: the name may resolve to
    // another address by now (DNS rebinding), or the subscription predates the check.
    final java.util.Optional<String> refusal = destinationPolicy.refusal(deliveryInfo.getUri());
    if (refusal.isPresent()) {
      log.warn("Webhook delivery for subscriptionId={} skipped: {}", deliveryInfo.getSubscriptionId(), refusal.get());
      return;
    }
    final HttpRequest.Builder requestBuilder = HttpRequest.newBuilder().uri(deliveryInfo.getUri()).POST(HttpRequest.BodyPublishers.ofString(deliveryInfo.getResult())).header(HttpHeaders.ACCEPT, APPLICATION_PROBLEM_JSON).header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON);
    deliveryInfo.getSignatureToken().ifPresent(token -> requestBuilder.header(CommonConstants.GS1_SIGNATURE, getJwsSignature(deliveryInfo.getResult(), deliveryInfo.getSignatureToken().get())));
    Uni.createFrom().item(requestBuilder).map(rb -> {
      try {
        HttpResponse<String> response = client.send(rb.build(), HttpResponse.BodyHandlers.ofString());
        return response;
      } catch (IOException | InterruptedException e) {
        throw new RuntimeException(e);
      }
    }).onFailure().retry().atMost(3).subscribe().with(response -> {
      if (response.statusCode() > 400) {
        throw new RuntimeException("Subscriber rejected the result for subscriptionId=%s, statusCode=%s".formatted(deliveryInfo.getSubscriptionId(), response.statusCode()));
      }
    }, failure -> log.error(String.format("Failed to deliver results for subscriptionId: %s", deliveryInfo.getSubscriptionId()), failure.getCause()));
  }

  private String getJwsSignature(String payload, String signatureToken) {
    try {
      JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.HS256).build();
      JWSObject jwsObject = new JWSObject(header, new Payload(payload));
      byte[] secretKey = signatureToken.getBytes(StandardCharsets.UTF_8);
      JWSSigner signer = new MACSigner(secretKey);
      jwsObject.sign(signer);
      return jwsObject.getSignature().toString();
    } catch (JOSEException e) {
      log.info("Error while generating Signature from payload");
      throw new RuntimeException(e);
    }
  }

  private final WebhookDestinationPolicy destinationPolicy;

  /**
   * Deliveries use a client of their own that does not follow redirects: a destination that
   * passed the check could otherwise answer with a redirect into the internal network.
   */
  @jakarta.inject.Inject
  public HttpQueryDeliveryEndpoint(WebhookDestinationPolicy destinationPolicy) {
    this(HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_2)
        .followRedirects(HttpClient.Redirect.NEVER)
        .connectTimeout(java.time.Duration.ofSeconds(10))
        .build(), destinationPolicy);
  }

  HttpQueryDeliveryEndpoint(HttpClient client, WebhookDestinationPolicy destinationPolicy) {
    this.client = client;
    this.destinationPolicy = destinationPolicy;
  }
}
