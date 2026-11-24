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

import io.openepcis.capture.context.message.EventSavedMessage;
import io.openepcis.model.epcis.EpcisQueryResult;
import io.openepcis.model.epcis.NamedQuerySubscription;
import io.openepcis.model.epcis.QueryResultsBody;
import io.openepcis.model.epcis.exception.PersistenceException;
import io.openepcis.opentelemetry.logging.OpenEPCISLogger;
import io.openepcis.repository.api.QueryRepository;
import io.openepcis.repository.api.SubscriptionRepository;
import io.openepcis.repository.model.EPCISEventES;
import io.openepcis.repository.model.StreamingSubscriptionQueryES;
import io.openepcis.service.api.QueryDelivery;
import io.openepcis.service.util.Constants;
import io.openepcis.service.util.JacksonConverter;
import io.openepcis.subscription.runner.WebHookDelivery;
import io.openepcis.subscription.runner.WebSocketDelivery;
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.quarkus.arc.Unremovable;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

@ApplicationScoped
@Unremovable
public class StreamingQueryJobService {
  private final SubscriptionRepository subscriptionRepository;
  private final QueryRepository queryRepository;
  private final QueryDelivery queryDelivery;
  private final JacksonConverter jacksonConverter;
  private final ManagedExecutor managedExecutor;
  private static final OpenEPCISLogger log = OpenEPCISLogger.getLogger(StreamingQueryJobService.class);
  @Inject
  @Channel(WebSocketDelivery.CHANNEL)
  Emitter<String> websocketEmitter;

  @Incoming("event-saved-in")
  public void consumeAndPublish(EventSavedMessage eventSavedMessage) {
    final String eventId = eventSavedMessage.getEventID();
    final String defaultGroup = eventSavedMessage.getDefaultGroup();
    Baggage baggage = Baggage.builder().put(Constants.TRACE_ID, eventSavedMessage.getTraceId()).put(Constants.SPAN_ID, eventSavedMessage.getSpanId()).build();
    Context contextWithBaggage = Context.current().with(baggage);
    try (Scope scope = contextWithBaggage.makeCurrent()) {
      log.debug("received message for percolation for event with eventId {}", eventId);
      queryRepository.getSavedEvent(eventSavedMessage.getIndex(), eventSavedMessage.getDocumentId(), eventId).onItem().ifNull().failWith(new PersistenceException("Event with eventId " + eventId + " is not found")).onFailure().retry().withBackOff(Duration.of(5, ChronoUnit.SECONDS)).withJitter(0.2).atMost(3).chain(event -> percolate(event, defaultGroup)).runSubscriptionOn(managedExecutor).subscribe().with(eventES -> {
        if (eventES != null) {
          log.debug("running percolated subscriptions for eventId = {}", eventId);
        }
      }, failure -> log.error(failure.getMessage()));
    }
  }

  private Uni<EPCISEventES> percolate(EPCISEventES event, String defaultGroup) {
    return queryRepository.percolateEvent(event, defaultGroup).collect().asList().chain(streamingSubscriptionQuerys -> processSubscription(streamingSubscriptionQuerys, event, defaultGroup));
  }

  private Uni<EPCISEventES> processSubscription(List<StreamingSubscriptionQueryES> streamingSubscriptions, EPCISEventES event, String defaultGroup) {
    if (Objects.nonNull(streamingSubscriptions)) {
      streamingSubscriptions.stream().map(StreamingSubscriptionQueryES::getQueryName).forEach(queryName -> queryRepository.getNamedSubscriptionFromQueryName(queryName, defaultGroup).runSubscriptionOn(managedExecutor).filter(namedQuerySubscription -> checkRolesAllowed(namedQuerySubscription, event)).subscribe().with(namedQuerySubscription -> {
        final EpcisQueryResult queryResult = populateEpcisQueryResultObject(namedQuerySubscription, event);
        final Consumer<EpcisQueryResult> subscriptionDeliveryFunction = Objects.nonNull(namedQuerySubscription.getDest()) ? new WebHookDelivery(namedQuerySubscription.getDest(), namedQuerySubscription.getSignatureToken(), queryDelivery, subscriptionRepository, namedQuerySubscription.getEpcisVersion()) : new WebSocketDelivery(jacksonConverter, queryRepository, websocketEmitter);
        subscriptionDeliveryFunction.accept(queryResult);
      }, throwable -> log.error(throwable.getMessage(), throwable)));
    }
    return Uni.createFrom().item(event);
  }

  private boolean checkRolesAllowed(NamedQuerySubscription namedQuerySubscription, EPCISEventES event) {
    // Any capturedBy entry: an event may be captured for further groups (Constants.ALSO_CAPTURED_FOR).
    if (event.getMetadata() != null && event.getMetadata().getCapturedBy() != null && !event.getMetadata().getCapturedBy().isEmpty()
        && StringUtils.isNotEmpty(event.getMetadata().getCapturedBy().get(0).getDefaultGroup()) && StringUtils.isNotEmpty(namedQuerySubscription.getDefaultGroup())) {
      return event.getMetadata().getCapturedBy().stream()
          .anyMatch(c -> c != null && namedQuerySubscription.getDefaultGroup().equals(c.getDefaultGroup()));
    }
    return true;
  }

  private EpcisQueryResult populateEpcisQueryResultObject(NamedQuerySubscription querySubscription, EPCISEventES event) {
    return EpcisQueryResult.builder().subscriptionID(querySubscription.getSubscriptionID().toString()).queryName(querySubscription.getQueryName()).resultsBody(new QueryResultsBody(List.of(event.getCoreModel()))).build();
  }

  public StreamingQueryJobService(SubscriptionRepository subscriptionRepository, QueryRepository queryRepository, QueryDelivery queryDelivery, JacksonConverter jacksonConverter, ManagedExecutor managedExecutor) {
    this.subscriptionRepository = subscriptionRepository;
    this.queryRepository = queryRepository;
    this.queryDelivery = queryDelivery;
    this.jacksonConverter = jacksonConverter;
    this.managedExecutor = managedExecutor;
  }
}
