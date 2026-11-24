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

import io.openepcis.model.epcis.EpcisQueryResult;
import io.openepcis.opentelemetry.logging.OpenEPCISLogger;
import io.openepcis.repository.api.QueryRepository;
import io.openepcis.service.util.JacksonConverter;
import io.openepcis.subscription.WebSocketRecord;
import java.util.function.Consumer;
import org.eclipse.microprofile.reactive.messaging.Emitter;

/**
 * Hands a query result to the WebSocket session that subscribed.
 *
 * <p>The session lives on one pod, and the result is usually produced on another: percolation runs
 * on whichever pod consumes {@code event-saved}, a scheduled query on whichever pod the scheduler
 * picked. So the result goes out on the Kafka channel {@value #CHANNEL}, which every pod reads with
 * a consumer group of its own, and each pod delivers to the sessions it holds. This used to be an
 * in-memory channel: with two pods, a session on the pod that did not percolate never got anything.
 */
public class WebSocketDelivery implements Consumer<EpcisQueryResult> {

  /** The outgoing channel; its topic is read by every pod (QueriesWebSocket). */
  public static final String CHANNEL = "websocket-subscription-out";

  private final JacksonConverter jacksonConverter;
  private final QueryRepository queryRepository;
  private final Emitter<String> webSocketEmitter;

  private static final OpenEPCISLogger log = OpenEPCISLogger.getLogger(WebSocketDelivery.class);

  public WebSocketDelivery(
      JacksonConverter jacksonConverter,
      QueryRepository queryRepository,
      Emitter<String> webSocketEmitter) {
    this.jacksonConverter = jacksonConverter;
    this.queryRepository = queryRepository;
    this.webSocketEmitter = webSocketEmitter;
  }

  @Override
  public void accept(EpcisQueryResult queryResult) {
    log.info("Publishing to WebSocket");
    queryRepository
        .getSessionIDFromSubscriptionID(queryResult.getSubscriptionID())
        .subscribe()
        .with(
            sessionID -> {
              if (sessionID == null || sessionID.isBlank()) {
                log.info(
                    "No WebSocket session for subscription {}; result not sent",
                    queryResult.getSubscriptionID());
                return;
              }
              webSocketEmitter.send(
                  jacksonConverter.convertToString(
                      new WebSocketRecord(
                          sessionID, jacksonConverter.convertToString(queryResult))));
            },
            failure -> {
              log.error("Failed to publish to WebSocket", failure.getCause());
              throw new RuntimeException(failure.getCause());
            });
  }
}
