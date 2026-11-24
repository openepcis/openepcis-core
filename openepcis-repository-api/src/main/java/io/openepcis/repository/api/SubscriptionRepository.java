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
package io.openepcis.repository.api;

import io.openepcis.model.dto.QuerySubscription;
import io.openepcis.model.dto.QuerySubscriptionResults;
import io.openepcis.model.epcis.NamedQuerySubscription;
import io.openepcis.query.context.SubscriptionContext;
import io.smallrye.mutiny.Uni;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionRepository {
  Uni<UUID> subscribeNamedQuery(
      NamedQuerySubscription subscription, SubscriptionContext subscriptionContext);

  Uni<String> getSubscriptionIdFromSessionId(String sessionID);

  Uni<NamedQuerySubscription> getSubscriptionFromSubscriptionID(String subscriptionID);

  Uni<Boolean> deleteSubscription(List<String> subscriptionIDs);

  Uni<Boolean> deleteSubscription(
      List<String> subscriptionIDs, SubscriptionContext subscriptionContext);

  Uni<QuerySubscription> getSubscriptionEventFromSubscriptionID(String subscriptionID);

  Uni<QuerySubscriptionResults> getSubscriptionOfNextPage(String scrollID);

  Uni<QuerySubscriptionResults> getSubscriptionFromQueryName(
      String queryName, Optional<Integer> perPage);

  void updateSubscriptionStatus(
      String subscriptionID,
      OffsetDateTime updateDate,
      String status,
      Optional<String> errorDescription);
}
