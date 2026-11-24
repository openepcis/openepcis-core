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
package io.openepcis.query.service.subscription;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openepcis.core.exception.MarshallingException;
import io.openepcis.model.dto.QuerySubscription;
import io.openepcis.model.dto.QuerySubscriptionResults;
import io.openepcis.model.epcis.NamedQueryMetaData;
import io.openepcis.model.epcis.NamedQuerySubscription;
import io.openepcis.model.epcis.Schedule;
import io.openepcis.model.epcis.exception.NoSubscriptionFoundException;
import io.openepcis.model.epcis.exception.NoSuchNameException;
import io.openepcis.model.epcis.exception.SubscriptionControlsException;
import io.openepcis.model.epcis.format.FormatPreference;
import io.openepcis.query.context.SubscriptionContext;
import io.openepcis.query.service.validator.QuerySubscriptionValidator;
import io.openepcis.repository.api.SubscriptionRepository;
import io.openepcis.repository.util.JSONUtil;
import io.openepcis.service.api.QueryService;
import io.openepcis.service.api.SubscriptionService;
import io.openepcis.subscription.service.ScheduledQueryJobService;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.jobrunr.scheduling.JobScheduler;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Stream;
import static io.openepcis.model.epcis.constants.QueryParameterConstants.*;

@ApplicationScoped
public class SubscriptionServiceImpl implements SubscriptionService {
  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SubscriptionServiceImpl.class);
  private final QueryService queryService;
  private final SubscriptionRepository subscriptionRepository;
  private final JobScheduler jobScheduler;
  private final QuerySubscriptionValidator querySubscriptionValidator;
  private final ObjectMapper objectMapper;

  @Override
  public Uni<Map<String, Object>> subscribeViaWebSocket(final String queryName, final String subscriptionControls, final String wsSessionID, final FormatPreference preference, final SubscriptionContext subscriptionContext, final String epcisVersion) {
    if (StringUtils.isBlank(subscriptionControls)) throw new SubscriptionControlsException("subscription controls missing");
    String[] subscriptionControlParams = subscriptionControls.split("&");
    Map<String, Object> params = new HashMap<>();
    Stream.of(subscriptionControlParams).forEach(s -> {
      String[] pair = s.split("=");
      params.put(pair[0], pair[1]);
    });
    final boolean isWsStream = querySubscriptionValidator.isWsStream(params);
    final Optional<String> cronExpression = createCronExpression(params);
    if (cronExpression.isEmpty() && !isWsStream) {
      throw new SubscriptionControlsException("Schedule parameters are missing");
    } else {
      return queryService.getQueryByName(queryName).chain(namedQueryMetaData -> {
        final NamedQuerySubscription namedQuerySubscription;
        try {
          namedQuerySubscription = isWsStream ? getNamedQuerySubscription(params, namedQueryMetaData, wsSessionID, StringUtils.EMPTY, subscriptionContext, true, epcisVersion) : getNamedQuerySubscription(params, namedQueryMetaData, wsSessionID, cronExpression.get(), subscriptionContext, false, epcisVersion);
        } catch (Exception e) {
          return Uni.createFrom().failure(e);
        }
        final UUID subscriptionID = UUID.randomUUID();
        namedQuerySubscription.setSubscriptionID(subscriptionID);
        namedQuerySubscription.setEpcFormat(preference.getEpcFormat().name());
        namedQuerySubscription.setCbvFormat(preference.getCbvFormat().name());
        if (!isWsStream) {
          jobScheduler.<ScheduledQueryJobService>scheduleRecurrently(subscriptionID.toString(), cronExpression.get(), x -> x.execute(subscriptionID.toString()));
        }
        saveSubscriptionControls(namedQuerySubscription, subscriptionContext);
        return Uni.createFrom().item(Map.of(SUBSCRIPTION_ID, subscriptionID.toString()));
      });
    }
  }

  private void saveSubscriptionControls(final NamedQuerySubscription subscription, SubscriptionContext subscriptionContext) {
    if (subscription.getCreatedAt() == null) subscription.setCreatedAt(OffsetDateTime.now());
    subscriptionRepository.subscribeNamedQuery(subscription, subscriptionContext).subscribe().with(response -> log.info("Query successfully scheduled with Subscription ID: {}", subscription.getSubscriptionID().toString()));
  }

  private NamedQuerySubscription getNamedQuerySubscription(final Map<String, Object> params, final NamedQueryMetaData namedQueryMetaData, final String sessionID, final String cronExpression, final SubscriptionContext subscriptionContext, boolean isWsStream, final String epcisVersion) {
    final NamedQuerySubscription namedQuerySubscription = new NamedQuerySubscription();
    namedQuerySubscription.setWsSessionID(sessionID);
    namedQuerySubscription.setQueryName(namedQueryMetaData.getName());
    namedQuerySubscription.setEpcisVersion(epcisVersion);
    namedQuerySubscription.setDeleted(false);
    namedQuerySubscription.setSubscribedBy(subscriptionContext.getSubscribedBy());
    namedQuerySubscription.setDefaultGroup(subscriptionContext.getDefaultGroup());
    namedQuerySubscription.setMinRecordTime(params.containsKey(INITIAL_RECORD_TIME) ? ZonedDateTime.parse(params.get(INITIAL_RECORD_TIME).toString(), DateTimeFormatter.ISO_ZONED_DATE_TIME).toOffsetDateTime() : OffsetDateTime.now());
    namedQuerySubscription.setInitialRecordTime(params.containsKey(INITIAL_RECORD_TIME) ? ZonedDateTime.parse(params.get(INITIAL_RECORD_TIME).toString(), DateTimeFormatter.ISO_ZONED_DATE_TIME).toOffsetDateTime() : OffsetDateTime.now());
    if (params.containsKey(REPORT_IF_EMPTY)) {
      namedQuerySubscription.setReportIfEmpty(Boolean.parseBoolean(params.get(REPORT_IF_EMPTY).toString()));
    } else {
      namedQuerySubscription.setReportIfEmpty(false);
    }
    if (isWsStream) {
      namedQuerySubscription.setStream(true);
    } else {
      namedQuerySubscription.setSchedule(new Schedule(cronExpression));
    }
    return namedQuerySubscription;
  }

  private Optional<String> createCronExpression(final Map<String, Object> params) {
    final List<String> cronParameters = List.of(SECOND, MINUTE, HOUR, DAY_OF_MONTH, MONTH, DAY_OF_WEEK);
    if (params.keySet().stream().anyMatch(key -> cronParameters.stream().anyMatch(cronParameter -> cronParameter.equals(key)))) {
      return Optional.of(CronExpressionGenerator.getInstance().generateCronExpression(params));
    }
    return Optional.empty();
  }

  @Override
  public Uni<Boolean> unsubscribeByWebSocketSession(String sessionID, SubscriptionContext subscriptionContext) {
    return subscriptionRepository.getSubscriptionIdFromSessionId(sessionID).chain(subscriptionId -> {
      if (StringUtils.isNotBlank(subscriptionId)) {
        return unsubscribe(subscriptionId, subscriptionContext);
      } else {
        log.info("Subscription with id={} not found for websocket sessionID={}", subscriptionId, sessionID);
      }
      return Uni.createFrom().item(false);
    });
  }

  @Override
  public Uni<Boolean> unsubscribe(String subscriptionID, SubscriptionContext subscriptionContext) {
    return subscriptionRepository.getSubscriptionFromSubscriptionID(subscriptionID).chain(namedQuerySubscription -> {
      if (namedQuerySubscription.getWsSessionID() != null || namedQuerySubscription.getStream() == null || !namedQuerySubscription.getStream()) {
        jobScheduler.deleteRecurringJob(subscriptionID);
      }
      return Uni.createFrom().item(namedQuerySubscription);
    }).chain(namedQuerySubscription -> subscriptionRepository.deleteSubscription(List.of(subscriptionID), subscriptionContext));
  }

  @Override
  public Uni<QuerySubscription> subscribeViaWebhook(final String queryName, final String querySubscription, final FormatPreference preference, final SubscriptionContext subscriptionContext, String epcisVersion) {
    return queryService.getQueryByName(queryName).chain(response -> {
      if (Objects.nonNull(response)) {
        final NamedQuerySubscription namedQuerySubscription = JSONUtil.deserializeJson(querySubscription, NamedQuerySubscription.class);
        namedQuerySubscription.setEpcFormat(preference.getEpcFormat().name());
        namedQuerySubscription.setCbvFormat(preference.getCbvFormat().name());
        querySubscriptionValidator.validateWebhookSubscription(namedQuerySubscription);
        final UUID subscriptionID = UUID.randomUUID();
        namedQuerySubscription.setSubscriptionID(subscriptionID);
        namedQuerySubscription.setInitialRecordTime(namedQuerySubscription.getInitialRecordTime() != null ? namedQuerySubscription.getInitialRecordTime() : OffsetDateTime.now());
        namedQuerySubscription.setMinRecordTime(namedQuerySubscription.getInitialRecordTime());
        namedQuerySubscription.setQueryName(queryName);
        // the version of the documents this subscriber receives belongs to the subscription, not
        // to the question it subscribes to: a query is what is asked, a subscription is how the
        // answers are delivered, and two subscribers of one query may want different versions
        namedQuerySubscription.setEpcisVersion(epcisVersion);
        namedQuerySubscription.setCreatedAt(OffsetDateTime.now());
        Uni<UUID> uniSubscriptionID = subscriptionRepository.subscribeNamedQuery(namedQuerySubscription, subscriptionContext);
        if (!querySubscriptionValidator.isStream(namedQuerySubscription)) {
          final String cronExpression = CronExpressionGenerator.getInstance().generateCronExpression(namedQuerySubscription.getSchedule());
          return uniSubscriptionID.map(subscriptionId -> {
            jobScheduler.<ScheduledQueryJobService>scheduleRecurrently(subscriptionID.toString(), cronExpression, x -> x.execute(subscriptionID.toString()));
            log.info("Query successfully scheduled with Subscription ID: {}", subscriptionID);
            return asSubscription(namedQuerySubscription);
          });
        }
        return uniSubscriptionID.map(ignored -> asSubscription(namedQuerySubscription));
      } else {
        // Subscribing to a query that is not there is a request for something that does not
        // exist, which is a 404. This used to raise a subscription control problem and answer
        // 400, telling the client its controls were wrong when they were fine.
        log.error(String.format("Named Query %s do not exist", queryName));
        throw new NoSuchNameException(String.format("No EPCIS Query exists with the name: %s", queryName));
      }
    }).onFailure().recoverWithUni(throwable -> Uni.createFrom().failure(throwable));
  }

  /**
   * The subscription as a subscriber is allowed to see it.
   *
   * <p>Everything the standard puts in a subscription response, and nothing else: the
   * signatureToken is the subscriber's own secret and QuerySubscription never writes it out.
   */
  private static QuerySubscription asSubscription(final NamedQuerySubscription stored) {
    final QuerySubscription subscription = new QuerySubscription();
    subscription.setSubscriptionID(stored.getSubscriptionID());
    subscription.setQueryName(stored.getQueryName());
    subscription.setDest(stored.getDest());
    subscription.setInitialRecordTime(stored.getInitialRecordTime());
    subscription.setReportIfEmpty(stored.getReportIfEmpty());
    subscription.setStream(stored.getStream());
    // a stream has no schedule, and a schedule is not a stream
    subscription.setSchedule(BooleanUtils.isTrue(stored.getStream()) ? null : stored.getSchedule());
    subscription.setCreatedAt(stored.getCreatedAt());
    return subscription;
  }

  @Override
  public Uni<String> getSubscriptionByID(String subscriptionID, String queryName) {
    final Uni<QuerySubscription> querySubscriptionUni = subscriptionRepository.getSubscriptionEventFromSubscriptionID(subscriptionID);
    return querySubscriptionUni.map(querySubscription -> {
      if (BooleanUtils.isTrue(querySubscription.getStream())) {
        querySubscription.setSchedule(null);
      }
      if (querySubscription.getQueryName().equals(queryName)) {
        return mapAsJSONString(querySubscription);
      } else {
        throw new NoSubscriptionFoundException("subscription not found");
      }
    }).onFailure().recoverWithUni(throwable -> {
      log.error("Error while fetching subscription with id {}", subscriptionID);
      return Uni.createFrom().failure(throwable);
    });
  }

  @Override
  public Uni<QuerySubscriptionResults> getSubscriptionOfNextPage(String scrollID) {
    return subscriptionRepository.getSubscriptionOfNextPage(scrollID);
  }

  @Override
  public Uni<QuerySubscriptionResults> getSubscriptionFromQueryName(String queryName, Integer perPage) {
    return subscriptionRepository.getSubscriptionFromQueryName(queryName, Optional.ofNullable(perPage)).invoke(subscriptionQueryEvent -> subscriptionQueryEvent.getQuerySubscriptions().forEach(subscription -> {
      if (BooleanUtils.isTrue(subscription.getStream())) {
        subscription.setSchedule(null);
      }
    }));
  }

  private String mapAsJSONString(Object object) {
    try {
      return objectMapper.writeValueAsString(object);
    } catch (JsonProcessingException e) {
      throw new MarshallingException("Error while converting data to string.");
    }
  }

  public SubscriptionServiceImpl(QueryService queryService, SubscriptionRepository subscriptionRepository, JobScheduler jobScheduler, QuerySubscriptionValidator querySubscriptionValidator, ObjectMapper objectMapper) {
    this.queryService = queryService;
    this.subscriptionRepository = subscriptionRepository;
    this.jobScheduler = jobScheduler;
    this.querySubscriptionValidator = querySubscriptionValidator;
    this.objectMapper = objectMapper;
  }
}
