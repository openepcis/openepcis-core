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
package io.openepcis.service.api;

import io.openepcis.model.dto.QuerySubscriptionResults;
import io.openepcis.model.epcis.format.FormatPreference;
import io.openepcis.model.dto.QuerySubscription;
import io.openepcis.query.context.SubscriptionContext;
import io.smallrye.mutiny.Uni;
import java.util.Map;

public interface SubscriptionService {
  Uni<Map<String, Object>> subscribeViaWebSocket(
      String queryName,
      String subscriptionControls,
      String wsSessionID,
      FormatPreference preference,
      SubscriptionContext subscriptionContext,
      String epcisVersion);

  Uni<Boolean> unsubscribe(String subscriptionID, SubscriptionContext subscriptionContext);

  Uni<Boolean> unsubscribeByWebSocketSession(
      String sessionID, SubscriptionContext subscriptionContext);

  /**
   * Creates a webhook subscription and answers with the subscription as it was stored.
   *
   * <p>Returns the subscription rather than its id alone because that is the body EPCIS 2.0 asks
   * a successful subscription to carry, down to the createdAt the repository stamped on it. The
   * signatureToken the subscriber sent is not part of it and never travels back out.
   */
  Uni<QuerySubscription> subscribeViaWebhook(
          String queryName,
          String querySubscription,
          FormatPreference preference,
          SubscriptionContext subscriptionContext, String epcisVersion);

  Uni<String> getSubscriptionByID(String subscriptionID, String queryName);

  Uni<QuerySubscriptionResults> getSubscriptionOfNextPage(String scrollID);

  Uni<QuerySubscriptionResults> getSubscriptionFromQueryName(String queryName, Integer perPage);
}
