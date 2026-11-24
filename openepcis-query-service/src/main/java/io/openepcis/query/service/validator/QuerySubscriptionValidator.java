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
package io.openepcis.query.service.validator;

import static io.openepcis.model.epcis.constants.QueryParameterConstants.*;
import static io.openepcis.model.epcis.constants.QueryParameterConstants.DAY_OF_WEEK;
import io.openepcis.model.epcis.NamedQuerySubscription;
import io.openepcis.model.epcis.exception.ExceptionMessages;
import io.openepcis.model.epcis.exception.SubscriptionControlsException;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;

@ApplicationScoped
public class QuerySubscriptionValidator {
  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(QuerySubscriptionValidator.class);

  public void validateWebhookSubscription(NamedQuerySubscription namedQuerySubscription) {
    if (StringUtils.isNotBlank(namedQuerySubscription.getSignatureToken()) && (namedQuerySubscription.getSignatureToken().length() < 32)) {
      throw new SubscriptionControlsException(ExceptionMessages.SIGNATURE_TOKEN_MUST_BE_256BITS_LONG);
    }
    if (namedQuerySubscription.getDest() == null || StringUtils.isBlank(namedQuerySubscription.getDest().toString())) throw new SubscriptionControlsException(ExceptionMessages.DESTINATION_URL_IS_MUST_TO_SUBSCRIBE_QUERY);
    destinationPolicy.refusal(namedQuerySubscription.getDest()).ifPresent(reason -> {
      throw new SubscriptionControlsException("Webhook destination refused: " + reason + ".");
    });
    if (BooleanUtils.isFalse(isStream(namedQuerySubscription)) && Objects.isNull(namedQuerySubscription.getSchedule())) throw new SubscriptionControlsException(ExceptionMessages.SCHEDULE_IS_MUST_TO_SUBSCRIBE_QUERY);
  }

  public boolean isStream(NamedQuerySubscription namedQuerySubscription) {
    if (BooleanUtils.isTrue(namedQuerySubscription.getStream()) && Objects.isNull(namedQuerySubscription.getSchedule())) {
      return true;
    } else if (BooleanUtils.isTrue(namedQuerySubscription.getStream()) && Objects.nonNull(namedQuerySubscription.getSchedule())) {
      throw new SubscriptionControlsException(ExceptionMessages.SUBSCRIPTION_STREAM_AND_SCHEDULE_ARE_MUTUALLY_EXCLUSIVE);
    }
    return false;
  }

  public boolean isWsStream(Map<String, Object> params) {
    final List<String> scheduleParams = List.of(SECOND, MINUTE, HOUR, DAY_OF_MONTH, MONTH, DAY_OF_WEEK);
    if (params.containsKey(STREAM) && "true".equalsIgnoreCase(params.get(STREAM).toString())) {
      if (params.keySet().stream().anyMatch(key -> scheduleParams.stream().anyMatch(scheduleParam -> scheduleParam.equals(key)))) {
        throw new SubscriptionControlsException(ExceptionMessages.SUBSCRIPTION_STREAM_AND_SCHEDULE_ARE_MUTUALLY_EXCLUSIVE);
      }
      return true;
    }
    return false;
  }

  private final WebhookDestinationPolicy destinationPolicy;

  public QuerySubscriptionValidator(WebhookDestinationPolicy destinationPolicy) {
    this.destinationPolicy = destinationPolicy;
  }
}
