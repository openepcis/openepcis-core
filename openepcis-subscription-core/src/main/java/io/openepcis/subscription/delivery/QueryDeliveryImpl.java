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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openepcis.model.epcis.DeliveryInfo;
import io.openepcis.model.epcis.EPCISQueryDocument;
import jakarta.enterprise.context.ApplicationScoped;
import java.net.URI;
import java.util.Optional;

@ApplicationScoped
public class QueryDeliveryImpl implements io.openepcis.service.api.QueryDelivery {
  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(QueryDeliveryImpl.class);
  private final ObjectMapper objectMapper;
  private final QueryDeliveryEndpointResolver queryDeliveryEndpointResolver;

  @Override
  public void deliverResults(EPCISQueryDocument epcisQueryDocument, URI destination, Optional<String> signatureToken) {
    String resultJson;
    try {
      resultJson = objectMapper.writeValueAsString(epcisQueryDocument);
    } catch (JsonProcessingException jpe) {
      throw new RuntimeException(jpe);
    }
    final QueryDeliveryEndpoint client = queryDeliveryEndpointResolver.resolveClient(destination);
    final DeliveryInfo info = new DeliveryInfo(epcisQueryDocument.getEpcisBody().getQueryResults().getSubscriptionID(), signatureToken, destination, resultJson);
    client.send(info);
  }

  public QueryDeliveryImpl(ObjectMapper objectMapper, QueryDeliveryEndpointResolver queryDeliveryEndpointResolver) {
    this.objectMapper = objectMapper;
    this.queryDeliveryEndpointResolver = queryDeliveryEndpointResolver;
  }
}
