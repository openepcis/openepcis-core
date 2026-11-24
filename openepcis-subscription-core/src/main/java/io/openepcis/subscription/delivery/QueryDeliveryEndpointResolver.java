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

import io.openepcis.core.exception.InvalidURIException;
import jakarta.enterprise.context.ApplicationScoped;
import java.net.URI;

@ApplicationScoped
public class QueryDeliveryEndpointResolver {
  private final HttpQueryDeliveryEndpoint httpQueryDeliveryEndpoint;

  public QueryDeliveryEndpoint resolveClient(URI uri) {
    switch (uri.getScheme()) {
    case "http": 
    case "https": 
      return httpQueryDeliveryEndpoint;
    default: 
      throw new InvalidURIException(String.format("Unsupported URI scheme: %s", uri.getScheme()));
    }
  }

  public QueryDeliveryEndpointResolver(HttpQueryDeliveryEndpoint httpQueryDeliveryEndpoint) {
    this.httpQueryDeliveryEndpoint = httpQueryDeliveryEndpoint;
  }
}
