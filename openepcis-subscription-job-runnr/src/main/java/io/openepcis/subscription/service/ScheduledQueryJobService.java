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
package io.openepcis.subscription.service;

import io.openepcis.model.epcis.EpcisQueryResult;
import io.openepcis.model.epcis.format.CBVFormat;
import io.openepcis.model.epcis.format.EPCFormat;
import io.openepcis.model.epcis.format.FormatPreference;
import io.openepcis.opentelemetry.logging.OpenEPCISLogger;
import io.openepcis.query.data.QueryParam;
import io.openepcis.query.data.SimpleEventQueryRequest;
import io.openepcis.query.factory.QueryParamMapper;
import io.openepcis.query.util.UnitConverter;
import io.openepcis.repository.api.QueryRepository;
import io.openepcis.repository.api.SubscriptionRepository;
import io.openepcis.service.api.QueryDelivery;
import io.openepcis.service.util.Constants;
import io.openepcis.service.util.JacksonConverter;
import io.openepcis.subscription.runner.WebHookDelivery;
import io.openepcis.subscription.runner.WebSocketDelivery;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.quarkus.arc.Unremovable;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;

@ApplicationScoped
@Unremovable
public class ScheduledQueryJobService {
  private final SubscriptionRepository subscriptionRepository;
  private final QueryRepository queryRepository;
  private final QueryDelivery queryDelivery;
  private final UnitConverter unitConverter;
  private final JacksonConverter jacksonConverter;
  private final ManagedExecutor managedExecutor;
  @ConfigProperty(name = "app.subscription.per-page")
  int perPage;
  @Inject
  @Channel(WebSocketDelivery.CHANNEL)
  Emitter<String> websocketEmitter;
  private final Tracer tracer = GlobalOpenTelemetry.getTracer("scheduledQueryJobService");
  private static final OpenEPCISLogger log = OpenEPCISLogger.getLogger(ScheduledQueryJobService.class);

  public void execute(String subscriptionID) {
    Span span = tracer.spanBuilder("ScheduledTask.execute").startSpan();
    Baggage baggage = Baggage.builder().put(Constants.TRACE_ID, span.getSpanContext().getTraceId()).put(Constants.SPAN_ID, span.getSpanContext().getSpanId()).build();
    Context contextWithBaggage = Context.current().with(baggage);
    try (Scope scope = contextWithBaggage.makeCurrent()) {
      subscriptionRepository.getSubscriptionFromSubscriptionID(subscriptionID).chain(namedQuerySubscription -> {
        final EPCFormat epcFormat = EPCFormat.fromString(namedQuerySubscription.getEpcFormat()).orElse(EPCFormat.No_Preference);
        final CBVFormat cbvFormat = CBVFormat.fromString(namedQuerySubscription.getCbvFormat()).orElse(CBVFormat.No_Preference);
        final FormatPreference formatPreference = FormatPreference.getInstance(epcFormat, cbvFormat);
        final Consumer<EpcisQueryResult> subscriptionDeliveryFunction = Objects.nonNull(namedQuerySubscription.getDest()) ? new WebHookDelivery(namedQuerySubscription.getDest(), namedQuerySubscription.getSignatureToken(), queryDelivery, subscriptionRepository, namedQuerySubscription.getEpcisVersion()) : new WebSocketDelivery(jacksonConverter, queryRepository, websocketEmitter);
        return queryRepository.getQueryByName(namedQuerySubscription.getQueryName(), true).chain(namedQueryMetaData -> {
          final Map<String, Object> query = namedQueryMetaData.getEpcisQuery();
          query.put(Constants.GE_RECORD_TIME_INTERNAL, namedQuerySubscription.getMinRecordTime());
          if (StringUtils.isNotEmpty(namedQuerySubscription.getDefaultGroup())) {
            query.put(Constants.DEFAULT_GROUP, namedQuerySubscription.getDefaultGroup());
          }
          final SimpleEventQueryRequest simpleEventQueryRequest = new SimpleEventQueryRequest(getListOfQueryParams(query, unitConverter));
          return queryRepository.executeEventsQuery(simpleEventQueryRequest, perPage, formatPreference, Boolean.TRUE, Optional.of(namedQuerySubscription.getDefaultGroup()));
        }).chain(queryResult -> {
          if (isNotEmpty(queryResult.getQueryResults().getResultsBody().getEventList()) || (isEmpty(queryResult.getQueryResults().getResultsBody().getEventList()) && BooleanUtils.isTrue(namedQuerySubscription.getReportIfEmpty()))) {
            final String nextPageToken = queryResult.getNextPageToken();
            searchRecursive(queryResult.getQueryResults(), subscriptionDeliveryFunction, nextPageToken, formatPreference, perPage, namedQuerySubscription.getSubscriptionID().toString(), namedQuerySubscription.getQueryName());
            return Uni.createFrom().item(Boolean.TRUE);
          }
          return Uni.createFrom().item(Boolean.FALSE);
        });
      }).runSubscriptionOn(managedExecutor).subscribe().with(reported -> {
        if (reported) {
          log.info("sending data for subscriptionID {}", subscriptionID);
        }
      });
    }
  }

  private void searchRecursive(final EpcisQueryResult queryResult, final Consumer<EpcisQueryResult> subscriptionDeliveryFunction, final String nextPageToken, final FormatPreference formatPreference, final Integer perPage, final String subscriptionID, final String queryName) {
    queryResult.setSubscriptionID(subscriptionID);
    queryResult.setQueryName(queryName);
    subscriptionDeliveryFunction.accept(queryResult);
    if (!StringUtils.isEmpty(nextPageToken)) {
      queryRepository.getEventsFromNextPageToken(nextPageToken, formatPreference, Optional.ofNullable(perPage)).runSubscriptionOn(managedExecutor).subscribe().with(result -> {
        final String token = result.getNextPageToken();
        searchRecursive(result.getQueryResults(), subscriptionDeliveryFunction, token, formatPreference, perPage, subscriptionID, queryName);
      });
    }
  }

  private List<QueryParam> getListOfQueryParams(Map<String, Object> params, UnitConverter unitConverter) {
    return params.entrySet().stream().map(new QueryParamMapper(true, unitConverter, Optional.empty())).toList();
  }

  public ScheduledQueryJobService(SubscriptionRepository subscriptionRepository, QueryRepository queryRepository, QueryDelivery queryDelivery, UnitConverter unitConverter, JacksonConverter jacksonConverter, ManagedExecutor managedExecutor) {
    this.subscriptionRepository = subscriptionRepository;
    this.queryRepository = queryRepository;
    this.queryDelivery = queryDelivery;
    this.unitConverter = unitConverter;
    this.jacksonConverter = jacksonConverter;
    this.managedExecutor = managedExecutor;
  }
}
