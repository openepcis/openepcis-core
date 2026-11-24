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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openepcis.capture.context.message.EventSavedMessage;
import io.openepcis.model.dto.Tracer;
import io.openepcis.constants.EPCIS;
import io.openepcis.core.exception.ValidationException;
import io.openepcis.model.dto.InvalidEPCISEventInfo;
import io.openepcis.model.epcis.EPCISEvent;
import io.openepcis.model.epcis.UserExtensionField;
import io.openepcis.model.epcis.constants.CommonConstants;
import io.openepcis.model.epcis.exception.PersistenceException;
import io.openepcis.opentelemetry.logging.OpenEPCISLogger;
import io.openepcis.repository.api.EventRepository;
import io.openepcis.repository.api.QueryRepository;
import io.openepcis.repository.cache.UserExtensionsCache;
import io.openepcis.service.util.Constants;
import io.openepcis.service.util.EventPredicates;
import io.openepcis.service.util.JsonLDUtil;
import io.openepcis.service.util.UserExtensionExtractor;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.unchecked.Unchecked;
import io.smallrye.reactive.messaging.MutinyEmitter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.OnOverflow;

import java.io.IOException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static io.openepcis.model.epcis.exception.ExceptionMessages.ERROR_WHILE_PERSISTING_EVENT;

@ApplicationScoped
public class EPCISEventPersistenceService {
  private final EventRepository reactiveRepository;

  private final QueryRepository queryRepository;

  private final ObjectMapper objectMapper;

  private final UserExtensionsCache userExtensionsCache;

  private final CaptureMetrics captureMetrics;

  private static final OpenEPCISLogger log =
      OpenEPCISLogger.getLogger(EPCISEventPersistenceService.class);

  @Inject JsonLDUtil jsonLDUtil;

  @Inject io.openepcis.capture.spi.StoredEventListener storedEventListener;

  public EPCISEventPersistenceService(
      final EventRepository reactiveRepository,
      final QueryRepository queryRepository,
      final ObjectMapper objectMapper,
      final UserExtensionsCache userExtensionsCache,
      final CaptureMetrics captureMetrics) {
    this.reactiveRepository = reactiveRepository;
    this.queryRepository = queryRepository;
    this.objectMapper = objectMapper;
    this.userExtensionsCache = userExtensionsCache;
    this.captureMetrics = captureMetrics;
  }

  @Inject
  @Channel("event-saved-out")
  @OnOverflow(value = OnOverflow.Strategy.UNBOUNDED_BUFFER)
  MutinyEmitter<EventSavedMessage> eventEmitter;

  public Uni<EPCISEvent> persistEvent(
      ObjectNode singleEventNode,
      ObjectNode eventNode,
      String captureID,
      Map<String, Object> contextAsMap,
      List<InvalidEPCISEventInfo> invalidEventsInfo,
      boolean isProceed,
      int eventIndex,
      Map<String, Object> metadata,
      boolean isBareEvent,
      String defaultGroup)
      throws ValidationException {
    if (isProceed || CollectionUtils.isEmpty(invalidEventsInfo)) {
      try {
        // Get the event and set the Hash value if already present
        final EPCISEvent epcisEvent = objectMapper.treeToValue(eventNode, EPCISEvent.class);
        if (eventNode.has(Constants.HASH)) {
          epcisEvent.getOpenEPCISExtension().setHash(eventNode.get(Constants.HASH).asText());
        }
        return extractExpandedIndividualEventList(singleEventNode.toString())
            .chain(
                extractedEvent -> {
                  // Build the event with recordTime, captureID and user extensions
                  buildEpcisEvent(epcisEvent, extractedEvent, captureID, contextAsMap, eventIndex);
                  final Set<UserExtensionField> newUserExtensionFields = new HashSet<>();
                  setUserExtensionsForAll(
                      epcisEvent,
                      eventNode,
                      contextAsMap,
                      newUserExtensionFields,
                      invalidEventsInfo);

                  // Try to save the event onto the repository after creating appropriate ES
                  // representations.
                  Uni<Boolean> saveOrUpdateUniResponse;
                  boolean isDuplicate =
                      Boolean.TRUE.equals(metadata.get(CommonConstants.IS_DUPLICATE));

                  if (isDuplicate) {
                    try {
                      saveOrUpdateUniResponse =
                          queryRepository
                              .updateEventMetaData(
                                  eventNode.get(Constants.HASH).asText(), metadata, captureID)
                              .chain(
                                  updated -> {
                                    if (Boolean.TRUE.equals(updated)) {
                                      return Uni.createFrom().item(true);
                                    }
                                    // The stored event the validation found is not there any
                                    // more (rolled back since): store this one.
                                    log.info(
                                        "Event {} found by its hash during validation is gone, storing it",
                                        epcisEvent.getEventID());
                                    metadata.remove(io.openepcis.repository.Constants.IS_REPLAY);
                                    return reactiveRepository.bulkSaveOrUpdate(
                                        List.of(epcisEvent),
                                        invalidEventsInfo,
                                        isProceed,
                                        captureID,
                                        ZonedDateTime.now(),
                                        metadata,
                                        defaultGroup,
                                        List.of(contextAsMap),
                                        isBareEvent);
                                  });
                    } catch (IOException e) {
                      throw new PersistenceException(
                          "Exception occurred while updating old event metadata in persist", e);
                    }
                  } else if (!CollectionUtils.isEmpty(invalidEventsInfo)) {
                    // If invalidEventInfo is not empty (due to userExtension extraction or other
                    // issues) then avoid saving the event and return with failure
                    log.debug(
                        "Cannot save event due to issues during user extension extraction : {}",
                        invalidEventsInfo);
                    final PersistenceException persistenceException =
                        new PersistenceException(
                            "Cannot save event due to issues during user extension extraction : "
                                + invalidEventsInfo);
                    return Uni.createFrom().failure(persistenceException);
                  } else {
                    // If invalidEventsInfo does not contain any information then only save the
                    // events
                    saveOrUpdateUniResponse =
                        reactiveRepository.bulkSaveOrUpdate(
                            List.of(epcisEvent),
                            invalidEventsInfo,
                            isProceed,
                            captureID,
                            ZonedDateTime.now(),
                            metadata,
                            defaultGroup,
                            List.of(contextAsMap),
                            isBareEvent);
                  }

                  return saveOrUpdateUniResponse
                      // first storage only; the listener never fails the capture
                      .call(
                          response ->
                              CollectionUtils.isEmpty(invalidEventsInfo)
                                      && !isDuplicate
                                      && !isReplay(metadata)
                                  ? storedEventListener
                                      .stored(eventNode, captureID, defaultGroup)
                                      .onFailure()
                                      .recoverWithNull()
                                  : Uni.createFrom().voidItem())
                      .chain(
                          response -> {
                            // If any error occurs then return with respective error
                            if (CollectionUtils.isNotEmpty(invalidEventsInfo)) {
                              log.debug(
                                  "Cannot save event due to invalid events : {}",
                                  invalidEventsInfo);
                              // Set the failure for the context due to failure to persist
                              final PersistenceException persistenceException =
                                  new PersistenceException(
                                      "Cannot save event due to invalid events : "
                                          + invalidEventsInfo);
                              return Uni.createFrom().failure(persistenceException);
                            }

                            // If no error then strip off the user extensions from the event and
                            // save them
                            newUserExtensionFields.removeAll(
                                userExtensionsCache.getUserExtensionFields());
                            if (CollectionUtils.isNotEmpty(newUserExtensionFields)) {
                              reactiveRepository
                                  .bulkSaveUserExtensionFields(newUserExtensionFields)
                                  .subscribe()
                                  .with(
                                      e ->
                                          log.debug(
                                              "successfully saved userExtensionFields for event with Id= {}",
                                              epcisEvent.getEventID()),
                                      throwable ->
                                          log.error(
                                              "Error while persisting userExtensionFields for event with Id= {}",
                                              epcisEvent.getEventID(),
                                              throwable));
                              userExtensionsCache.updateField(newUserExtensionFields);
                            }

                            log.debug(
                                "Event is saved successfully with EventId: {}",
                                epcisEvent.getEventID());
                            captureMetrics.eventPersisted(defaultGroup);

                            if (isReplay(metadata)) {
                              // The tenant had captured exactly this before: it counts as
                              // captured, but its event-saved went out with the first capture,
                              // and subscribers would get it twice.
                              log.debug(
                                  "Replay of stored event {}, no event-saved",
                                  epcisEvent.getEventID());
                              return Uni.createFrom().item(epcisEvent);
                            }
                            if (isProceed || isBareEvent) {
                              final EventSavedMessage eventSavedMessage =
                                  EventSavedMessage.builder()
                                      .eventID(epcisEvent.getEventID())
                                      .build();
                              // consumers scope by tenant (streaming subscriptions, instance master
                              // data); a single-event capture used to send none
                              eventSavedMessage.setDefaultGroup(defaultGroup);
                              // where the record is, so consumers GET it instead of searching
                              if (!isDuplicate) {
                                locate(eventSavedMessage, epcisEvent);
                              }
                              log.debug(
                                  "producing event to event-save topic with eventID= {}",
                                  epcisEvent.getEventID());
                              return produceMessageToEventSavedTopic(eventSavedMessage)
                                  .chain(x -> Uni.createFrom().item(epcisEvent));
                            }
                            // Set the success/persisted for the context due to sucess to persist.
                            return Uni.createFrom().item(epcisEvent);
                          })
                      .onFailure()
                      .recoverWithUni(
                          failure -> {
                            log.error("Failure during the persisting of the event", failure);
                            final PersistenceException persistenceException =
                                new PersistenceException(ERROR_WHILE_PERSISTING_EVENT, failure);
                            return Uni.createFrom().failure(persistenceException);
                          });
                })
            .onFailure()
            .recoverWithUni(
                failure -> {
                  log.error("Failure during the persisting of the event", failure);
                  final PersistenceException persistenceException =
                      new PersistenceException(ERROR_WHILE_PERSISTING_EVENT, failure);
                  return Uni.createFrom().failure(persistenceException);
                });
      } catch (Exception e) {
        log.error(
            "ERROR occur while persisting event in the ES with captureID={} and eventID={}",
            captureID,
            eventNode.get(Constants.EVENT_ID));
        return Uni.createFrom().failure(e);
      }
    }
    return Uni.createFrom().nothing();
  }

  /** Whether the event is an idempotent replay of what its tenant captured before. */
  static boolean isReplay(final Map<String, Object> metadata) {
    return metadata != null
        && Boolean.TRUE.equals(metadata.get(io.openepcis.repository.Constants.IS_REPLAY));
  }

  private void buildEpcisEvent(
      EPCISEvent epcisEvent,
      JsonArray expandedIndividualEventList,
      String captureID,
      Map<String, Object> contextAsMap,
      int sequence) {
    epcisEvent.getOpenEPCISExtension().setSequenceInEPCISDoc(sequence);
    epcisEvent.getOpenEPCISExtension().setCaptureID(captureID);
    epcisEvent.setRecordTime(OffsetDateTime.now().truncatedTo(ChronoUnit.MILLIS));
    if (StringUtils.isBlank(epcisEvent.getEventID())) {
      // An event that arrives without an identifier gets the one it already has:
      // the canonical CBV hash, computed a few dozen lines above and hanging on
      // this very object. A random UUID would name the same statement differently
      // on every send, which is the opposite of what an event identifier is for —
      // the sender retries, and the repository sees a new event each time.
      //
      // A supplied eventID is never touched. It is the sender's handle on their
      // own statement, and overwriting it would leave them unable to find what
      // they captured. The hash still governs de-duplication either way.
      //
      // The random UUID survives as the last resort, for the case where there is
      // no hash either. That case is refused earlier now (a blank hash is a 400),
      // so this is a floor, not a path.
      final String canonicalHash = epcisEvent.getOpenEPCISExtension() == null
          ? null
          : epcisEvent.getOpenEPCISExtension().getHash();
      epcisEvent.setEventID(StringUtils.isNotBlank(canonicalHash)
          ? canonicalHash
          : "urn:uuid:" + UUID.randomUUID());
    }
    final JsonObject expandedEvent = expandedIndividualEventList.getJsonObject(0);
    epcisEvent.setExpandedJSONLDString(expandedEvent.toString());
    epcisEvent.setContextInfo(!contextAsMap.isEmpty() ? List.of(contextAsMap) : null);
  }

  public void setUserExtensionsForAll(
      final EPCISEvent e,
      final ObjectNode individualEvent,
      final Map<String, Object> contextAsMap,
      final Set<UserExtensionField> newUserExtensionFields,
      final List<InvalidEPCISEventInfo> invalidEventsInfo)
      throws ValidationException {

    // Get user extensions
    Map<String, Object> userExtensions =
        UserExtensionExtractor.getUserExtensions(
            individualEvent, "", contextAsMap, newUserExtensionFields);
    e.setUserExtensions(userExtensions);

    // Get inner user extensions
    userExtensions =
        UserExtensionExtractor.getInnerUserExtensions(
            e.getUserExtensions(),
            individualEvent,
            new HashMap<>(),
            "",
            contextAsMap,
            newUserExtensionFields);
    e.setInnerUserExtensions(userExtensions);

    // To set the index of the invalid events if any exceptions occur during the userExtensions/ILMD
    // extractions from the event
    final List<Integer> invalidIndexes = new ArrayList<>();

    try {
      if (EventPredicates.HAS_READ_POINT.test(individualEvent)) {
        UserExtensionExtractor.extractUserExtensionFromReadPoint(
            individualEvent, e, contextAsMap, newUserExtensionFields);
      }

      if (EventPredicates.HAS_BIZ_LOCATION.test(individualEvent)) {
        UserExtensionExtractor.extractUserExtensionFromBizLocation(
            individualEvent, e, contextAsMap, newUserExtensionFields);
      }

      if (EventPredicates.IS_SENSOR_EVENT.test(individualEvent)) {
        UserExtensionExtractor.extractUserExtensionsFromSensorElement(
            individualEvent, e, contextAsMap, newUserExtensionFields);
      }

      if (EventPredicates.IS_ERROR_DECLARATION.test(individualEvent)) {
        UserExtensionExtractor.extractUserExtensionFromErrorDeclaration(
            individualEvent, e, contextAsMap, newUserExtensionFields);
      }

      if (EventPredicates.HAS_ILMD.test(individualEvent)) {
        UserExtensionExtractor.extractUserExtensionFromIlmd(
            individualEvent, e, contextAsMap, newUserExtensionFields);
      }
    } catch (Exception exception) {
      // During exception set the exception, and
      invalidIndexes.add(e.getOpenEPCISExtension().getSequenceInEPCISDoc());
      invalidEventsInfo.add(
          new InvalidEPCISEventInfo(
              exception.getClass().getSimpleName(),
              "User Extension Extraction Failed",
              400,
              exception.getLocalizedMessage(),
              invalidIndexes));
    }
  }

  private Uni<JsonArray> extractExpandedIndividualEventList(String captureString) {
    final Uni<JsonObject> uniJsonObject = jsonLDUtil.expandJsonLDDocument(captureString);
    return uniJsonObject.chain(
        jsonObject -> {
          if (jsonObject.containsKey(Constants.HTTPS_NS_GS_1_ORG_EPCIS_EPCIS_BODY)) {
            if (captureString.contains(EPCIS.EPCIS_QUERY_DOCUMENT)) {
              return Uni.createFrom()
                  .item(
                      jsonObject
                          .getJsonArray(Constants.HTTPS_NS_GS_1_ORG_EPCIS_EPCIS_BODY)
                          .getJsonObject(0)
                          .getJsonArray("https://ref.gs1.org/epcis/queryResults")
                          .getJsonObject(0)
                          .getJsonArray("https://ref.gs1.org/epcis/resultsBody")
                          .getJsonObject(0)
                          .getJsonArray("https://ref.gs1.org/epcis/eventList"));
            }
            return Uni.createFrom()
                .item(
                    jsonObject
                        .getJsonArray(Constants.HTTPS_NS_GS_1_ORG_EPCIS_EPCIS_BODY)
                        .getJsonObject(0)
                        .getJsonArray(Constants.HTTPS_NS_GS_1_ORG_EPCIS_EVENT_LIST));
          }
          return Uni.createFrom().item(Json.createArrayBuilder().add(jsonObject).build());
        });
  }

  public Uni<Boolean> removeEventsForCaptureID(
      String captureID, OffsetDateTime createdAt, String defaultGroup) {
    return reactiveRepository.removeEventsForCaptureID(
        captureID, createdAt.atZoneSameInstant(ZoneId.of("UTC")), defaultGroup);
  }

  public Uni<Boolean> updateEventVisibility(
      String captureID, OffsetDateTime createdAt, String defaultGroup) {
    return reactiveRepository.updateVisibilityByCaptureID(
        captureID, createdAt.atZoneSameInstant(ZoneId.of("UTC")), defaultGroup);
  }

  public Uni<Long> checkAllEventsAvailableForGivenCaptureID(
      final String captureID, final int processedEventCount) {
    return Uni.createFrom()
        .deferred(
            Unchecked.supplier(
                () -> {
                  try {
                    return queryRepository.getEventCountByCaptureID(captureID);
                  } catch (IOException e) {
                    throw new RuntimeException(
                        String.format(
                            "Error while collecting event count for captureID= %s", captureID));
                  }
                }))
        .onItem()
        .transformToUni(
            count -> {
              if (count < processedEventCount) {
                log.debug(
                    "Actual event counts are : {} which is less than processed event count : {}",
                    count,
                    processedEventCount);
                // Return a successful Uni indicating that count is not sufficient yet
                // Retry only if the previous attempt returned an insufficient count
                return Uni.createFrom()
                    .failure(
                        new RuntimeException(
                            "Actual event count is less than processed event count."));
              }

              log.debug(
                  "All event counts are matched actualEvent count= {} and processed event count are : {}",
                  count,
                  processedEventCount);
              return Uni.createFrom().item(count);
            })
        .onFailure()
        .retry()
        .withBackOff(Duration.ofMillis(250))
        .withJitter(0.2)
        .atMost(5)
        .onFailure()
        .invoke(
            throwable ->
                // Not recovered into a 0 any more. A count that never catches up
                // used to be swallowed here, and the chain went on to flip
                // visibility for documents it had not found - an update that
                // matches nothing, reports success, and leaves the events
                // invisible with a job marked green. Failing here marks the job
                // for what it is, and the errors land on the capture job where
                // somebody can see them.
                log.error(
                    "Events for captureID={} were still not searchable after retrying; "
                        + "visibility cannot be set. Expected {} events.",
                    captureID,
                    processedEventCount,
                    throwable));
  }

  public Multi<String> getEventIdsByCaptureId(String captureID) {
    return queryRepository.getEventIDsByCaptureID(captureID);
  }

  /**
   * Names the stored record in the message: the month index of its eventTime and its document id
   * (EventDocuments). Left out when the repository does not place events that way or the event
   * has no hash; consumers then look the event up by its eventID.
   */
  void locate(final EventSavedMessage message, final EPCISEvent epcisEvent) {
    final String hash =
        epcisEvent.getOpenEPCISExtension() == null
            ? null
            : epcisEvent.getOpenEPCISExtension().getHash();
    if (StringUtils.isBlank(hash) || epcisEvent.getEventTime() == null) {
      return;
    }
    final String index = reactiveRepository.eventIndexName(epcisEvent.getEventTime());
    if (StringUtils.isBlank(index)) {
      return;
    }
    message.setIndex(index);
    message.setDocumentId(
        io.openepcis.repository.util.EventDocuments.documentId(
            hash, epcisEvent.getErrorDeclaration() != null));
  }

  /**
   * The document a persisted event is stored as ({@code index/_id}), from its hash and eventTime.
   * Empty when that is not where it is: an event matched in a legacy index by its hash
   * (IS_DUPLICATE), or a repository that does not place events by document.
   */
  public Optional<String> documentOf(final EPCISEvent epcisEvent, final Map<String, Object> metadata) {
    if (epcisEvent == null
        || (metadata != null && Boolean.TRUE.equals(metadata.get(CommonConstants.IS_DUPLICATE)))) {
      return Optional.empty();
    }
    final EventSavedMessage probe = new EventSavedMessage();
    locate(probe, epcisEvent);
    if (StringUtils.isBlank(probe.getIndex()) || StringUtils.isBlank(probe.getDocumentId())) {
      return Optional.empty();
    }
    return Optional.of(
        new io.openepcis.repository.util.EventDocuments.Location(probe.getIndex(), probe.getDocumentId())
            .toString());
  }

  /**
   * Completes a capture job whose events are known by document: one realtime multi get, one bulk
   * update by id that makes them visible, and an event-saved message naming index and document
   * for each event that is news under this capture. Fails when a document is missing, so the
   * caller can fall back to finding the events by search.
   */
  public Uni<Integer> completeByDocuments(
      final String captureID, final List<String> documents, final Tracer trace) {
    return queryRepository
        .getEventsByDocuments(documents)
        .chain(
            found -> {
              if (found.size() != new HashSet<>(documents).size()) {
                return Uni.createFrom()
                    .failure(
                        new PersistenceException(
                            String.format(
                                "%d of %d documents of capture %s found",
                                found.size(), documents.size(), captureID)));
              }
              return reactiveRepository
                  .markVisible(List.copyOf(found.keySet()))
                  .chain(
                      visible -> {
                        if (!Boolean.TRUE.equals(visible)) {
                          return Uni.createFrom()
                              .<Integer>failure(
                                  new PersistenceException(
                                      "unable to make the events of capture " + captureID + " visible"));
                        }
                        final List<Uni<Void>> sends = new ArrayList<>();
                        found.forEach(
                            (document, event) -> {
                              if (!io.openepcis.repository.util.EventDocuments.newInCapture(event, captureID)) {
                                return;
                              }
                              final io.openepcis.repository.util.EventDocuments.Location at =
                                  io.openepcis.repository.util.EventDocuments.Location.parse(document);
                              final EventSavedMessage message =
                                  EventSavedMessage.builder().eventID(event.getEventID()).build();
                              message.setIndex(at.index());
                              message.setDocumentId(at.id());
                              message.setTraceId(trace.getTraceId());
                              message.setSpanId(trace.getSpanId());
                              message.setDefaultGroup(trace.getDefaultGroup());
                              sends.add(produceMessageToEventSavedTopic(message));
                            });
                        if (sends.isEmpty()) {
                          return Uni.createFrom().item(0);
                        }
                        return Uni.join().all(sends).andFailFast().map(List::size);
                      });
            });
  }

  /** Makes captured events searchable, for the completion that finds them by search. */
  public Uni<Void> refreshEvents() {
    return reactiveRepository.refreshEventIndices();
  }

  public Uni<Void> produceMessageToEventSavedTopic(EventSavedMessage eventSavedMessage) {
    return eventEmitter.send(eventSavedMessage);
  }
}
