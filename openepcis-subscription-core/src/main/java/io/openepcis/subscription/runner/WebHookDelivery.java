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
package io.openepcis.subscription.runner;

import io.openepcis.model.epcis.EPCISQueryBody;
import io.openepcis.model.epcis.EPCISQueryDocument;
import io.openepcis.model.epcis.EpcisQueryResult;
import io.openepcis.opentelemetry.logging.OpenEPCISLogger;
import io.openepcis.repository.api.SubscriptionRepository;
import io.openepcis.service.api.QueryDelivery;
import io.openepcis.service.util.Constants;
import io.smallrye.mutiny.Uni;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

public class WebHookDelivery
    implements Function<EpcisQueryResult, Uni<Boolean>>, Consumer<EpcisQueryResult> {

  private final URI dest;

  private final String signatureToken;
  private final QueryDelivery queryDelivery;
  private final SubscriptionRepository subscriptionRepository;
  private final String epcisVersionMax;
  private static final OpenEPCISLogger log = OpenEPCISLogger.getLogger(WebHookDelivery.class);

  public WebHookDelivery(
          URI dest,
          String signatureToken,
          QueryDelivery queryDelivery,
          SubscriptionRepository subscriptionRepository, String epcisVersionMax) {
    this.dest = dest;
    this.signatureToken = signatureToken;
    this.queryDelivery = queryDelivery;
    this.subscriptionRepository = subscriptionRepository;
    this.epcisVersionMax = epcisVersionMax;
  }

  private void handleDeliveryResult(String subscriptionID, String status) {
    Optional<String> reason = Optional.empty();
    OffsetDateTime updatedMinRecordTime = OffsetDateTime.now();
    if (status.equalsIgnoreCase(Constants.JOB_STATUS_FAILURE)) {
      updatedMinRecordTime = null;
      status = Constants.JOB_STATUS_FAILURE;
      reason = Optional.of(String.format("Failed to handle subscription %s", subscriptionID));
    }
    subscriptionRepository.updateSubscriptionStatus(
        subscriptionID, updatedMinRecordTime, status, reason);
    log.info("Status of subscription job for subscriptionID={} is={}", subscriptionID, status);
  }

  @Override
  public void accept(EpcisQueryResult queryResult) {
    final EPCISQueryDocument epcisQueryDocument =
        new EPCISQueryDocument(new EPCISQueryBody(queryResult), Optional.of(epcisVersionMax));

    Uni.createFrom()
        .item(dest)
        .onItem()
        .invoke(
            destVal ->
                queryDelivery.deliverResults(
                    epcisQueryDocument, destVal, Optional.of(signatureToken)))
        .subscribe()
        .with(
            result -> {
              handleDeliveryResult(queryResult.getSubscriptionID(), "success");
              log.info("Subscription delivery completed successfully: {}", result);
            },
            failure -> {
              handleDeliveryResult(queryResult.getSubscriptionID(), "failure");
              log.error("Subscription delivery failed", failure);
            });
  }

  @Override
  public Uni<Boolean> apply(EpcisQueryResult epcisQueryResult) {
    return null;
  }
}
