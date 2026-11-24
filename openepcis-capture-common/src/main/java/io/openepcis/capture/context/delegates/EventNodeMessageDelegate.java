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
package io.openepcis.capture.context.delegates;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openepcis.capture.context.CaptureContext;
import io.openepcis.constants.EPCIS;
import io.openepcis.model.dto.CaptureJobStatusMessage;
import io.openepcis.opentelemetry.logging.OpenEPCISLogger;
import io.opentelemetry.api.trace.Span;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

/**
 * Delegates EPCIS event to message queue for validation and persistence
 */
@ApplicationScoped
public class EventNodeMessageDelegate implements BiConsumer<CaptureContext, ObjectNode> {
  @Inject
  DocumentCaptureMessageEmitter documentCaptureMessageEmitter;
  @Inject
  CaptureJobStatusMessageDelegate captureJobStatusMessageDelegate;
  private final AtomicReference<CompletableFuture<Void>> updateFromKafkaFuture = new AtomicReference<>(null);
  private static final OpenEPCISLogger log = OpenEPCISLogger.getLogger(EventNodeMessageDelegate.class);

  @Override
  public void accept(CaptureContext context, ObjectNode event) {
    if (context.getEpcisDocumentRef().get() == null) {
      // Document header not yet processed - skip this event
      // The new ObjectNodePublisher handles early-eventList via retry mechanism
      return;
    }
    // This may happen when @context attribute in EPCISDocument is present after eventList
    // attribute. (out of sequence situation)
    // TODO: causing more issues than doing any good -
    //  event may contain an event specific context - failing for certain captures that are valid
    //if (event.has(EPCIS.CONTEXT)) {
    //  return;
    //}
    if (updateFromKafkaFuture.get() == null) {
      // call to KafkaQueries is blocking - uses Uni -
      Uni<CaptureJobStatusMessage> msg = captureJobStatusMessageDelegate.getCaptureJobStatusMessage(context.getCaptureID());
      msg.subscribe().with(m -> {
        if (m != null) {
          context.updateFromCaptureJobStatusMessage(m);
          log.debug("update from CaptureJobStatusMessage " + m.getCaptureID());
        }
      });
      if (context.getFinishedAt().get() == null) {
        context.getEventCount().incrementAndGet();
        context.toDocumentCapturedMessage(event, Span.current().getSpanContext().getTraceId(), Span.current().getSpanContext().getSpanId()).subscribe().with(documentCaptureMessage -> {
          Log.debug("Received DocumentCaptureMessage successfully after upload or string from DefaultCaptureContext");
          log.debug("message sent to document consumers");
          documentCaptureMessageEmitter.send(context.getCaptureID(), documentCaptureMessage);
        }, failure -> {
          Log.error("Failed to receive DocumentCaptureMessage after upload or string from DefaultCaptureContext");
          context.fail(failure);
        });
      } else {
        log.debug("do not sent message to document consumers for CaptureID = {} because its job already finished at: {}", context.getCaptureID(), context.getFinishedAt().get());
      }
    }
  }

  public EventNodeMessageDelegate() {
  }
}
