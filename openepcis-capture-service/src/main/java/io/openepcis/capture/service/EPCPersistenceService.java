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
package io.openepcis.capture.service;

import io.openepcis.capture.context.message.EventSavedMessage;
import io.openepcis.model.epcis.exception.PersistenceException;
import io.openepcis.opentelemetry.logging.OpenEPCISLogger;
import io.openepcis.repository.api.QueryRepository;
import io.openepcis.repository.model.EpcES;
import io.openepcis.service.util.Constants;
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.quarkus.arc.Unremovable;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.eclipse.microprofile.reactive.messaging.Incoming;

@ApplicationScoped
@Unremovable
public class EPCPersistenceService {
  private final QueryRepository queryRepository;
  private static final OpenEPCISLogger log = OpenEPCISLogger.getLogger(EPCPersistenceService.class);
  private static final MessageDigest digest;

  static {
    try {
      digest = MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  }

  @Incoming("event-saved-in-epc-consumer")
  public void consumeAndPublish(EventSavedMessage eventSavedMessage) {
    final String eventId = eventSavedMessage.getEventID();
    // TODO: Remove Baggage if not needed
    Baggage baggage = Baggage.builder().put(Constants.TRACE_ID, eventSavedMessage.getTraceId()).put(Constants.SPAN_ID, eventSavedMessage.getSpanId()).put(io.openepcis.repository.Constants.DEFAULT_GROUP, eventSavedMessage.getDefaultGroup()).build();
    Context contextWithBaggage = Context.current().with(baggage);
    try (Scope scope = contextWithBaggage.makeCurrent()) {
      log.debug("Received message for EPC persistence for event with eventId {}", eventId);
      queryRepository.getSavedEvent(eventSavedMessage.getIndex(), eventSavedMessage.getDocumentId(), eventId).onItem().ifNull().failWith(() -> new PersistenceException("Event with eventId " + eventId + " is not found")).onFailure().retry().withBackOff(Duration.ofMillis(1000)).withJitter(0.2).atMost(3).chain(epcisEvent -> mapEPCByHash(epcisEvent.getEpcs(epcisEvent))).chain(this::saveEpcs).onFailure().invoke(th -> log.error("Error while persisting EPCs", th)).subscribe().with(success -> log.info("Successfully persisted EPCs for eventId {}", eventId), failure -> log.error("Failed to persist EPCs for eventId {}", eventId, failure));
    }
  }

  private Uni<Map<String, EpcES>> mapEPCByHash(Set<EpcES> epcs) {
    return Uni.createFrom().item(() -> {
      final Map<String, EpcES> epcHashMap = new HashMap<>();
      for (EpcES epc : epcs) {
        String hash = generateSHA256Hash(epc.getCanonicalDL());
        epcHashMap.put(hash, epc);
      }
      return epcHashMap;
    });
  }

  private String generateSHA256Hash(String epc) {
    byte[] hashBytes = digest.digest(epc.getBytes(StandardCharsets.UTF_8));
    StringBuilder hashString = new StringBuilder();
    for (byte b : hashBytes) {
      hashString.append(String.format("%02x", b));
    }
    return hashString.toString();
  }

  private Uni<Boolean> saveEpcs(Map<String, EpcES> epcHashMap) {
    return queryRepository.saveEpcs(epcHashMap);
  }

  public EPCPersistenceService(QueryRepository queryRepository) {
    this.queryRepository = queryRepository;
  }
}
