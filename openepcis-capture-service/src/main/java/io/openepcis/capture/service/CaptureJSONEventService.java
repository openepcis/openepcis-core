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

import static io.openepcis.model.epcis.exception.ExceptionMessages.ERROR_WHILE_PERSISTING_EVENT;
import static io.openepcis.repository.util.InvalidEventInfoUtil.condenseInvalidInfos;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openepcis.capture.api.CaptureEventService;
import io.openepcis.capture.context.CaptureContext;
import io.openepcis.capture.context.EPCISEventObjectNodeUtil;
import io.openepcis.capture.context.StorageService;
import io.openepcis.constants.CBVVersion;
import io.openepcis.constants.EPCISFormat;
import io.openepcis.constants.EPCISVersion;
import io.openepcis.eventhash.EventHashGenerator;
import io.openepcis.model.dto.CaptureJobStatusMessage;
import io.openepcis.model.dto.InvalidEPCISEventInfo;
import io.openepcis.model.epcis.EPCISEvent;
import io.openepcis.model.epcis.constants.CommonConstants;
import io.openepcis.model.epcis.exception.CaptureValidationException;
import io.openepcis.reactive.publisher.ObjectNodePublisher;
import io.openepcis.repository.api.EventRepository;
import io.openepcis.repository.util.JSONUtil;
import io.openepcis.service.common.ContextBuilder;
import io.openepcis.service.config.CloudStorageConfig;
import io.openepcis.service.util.Constants;
import io.openepcis.service.util.InputStreamMultiplier;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.json.JSONArray;
import org.json.JSONObject;

@ApplicationScoped
public class CaptureJSONEventService implements CaptureEventService {
  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(CaptureJSONEventService.class);
  @Inject
  EventRepository reactiveRepository;
  @Inject
  ObjectMapper objectMapper;
  @Inject
  CaptureConfigurationService captureConfigService;
  @Inject
  EPCISEventValidationService eventValidationService;
  @Inject
  EPCISEventPersistenceService epcisEventPersistenceService;
  @Inject
  CaptureMetrics captureMetrics;
  @Inject
  StorageService storageService;
  @Inject
  ManagedExecutor managedExecutor;
  @Inject
  CloudStorageConfig cloudStorageConfig;
  /**
   * The CBV version the canonical hash is computed under.
   *
   * <p>It has to be said out loud, and it has to be the same everywhere. Moving
   * from 2.0 to 2.1 does not merely change the {@code ?ver=} suffix — it changes
   * the canonicalisation itself wherever sensor elements or user extensions are
   * involved, so every event would hash differently from that moment on. Since
   * the hash is the event's identity, that is a migration, not a version bump.
   * The parameterless constructor hid the decision; this makes it a
   * configuration value with the same key the standalone hash service uses.
   */
  @ConfigProperty(name = "openepcis.event-hash.cbv-version", defaultValue = "2.0.0")
  String cbvVersion;

  protected EventHashGenerator eventHashGenerator;
  protected InputStreamMultiplier inputStreamMultiplier;

  @PostConstruct
  public void init() {
    // Not CBVVersion.of(): that answers 2.0.1 for anything it does not
    // recognise, so a typo in the configuration would quietly re-mint every
    // identifier this repository issues. An unknown version stops the service.
    this.eventHashGenerator = new EventHashGenerator(CBVVersion.fromString(cbvVersion)
        .orElseThrow(() -> new IllegalStateException(
            "openepcis.event-hash.cbv-version is '" + cbvVersion + "', which is not a CBV version"
                + " this build knows. The canonical event hash - and with it every event's"
                + " identity - depends on it, so it is not guessed.")));
    this.inputStreamMultiplier = new InputStreamMultiplier(captureConfigService.bufferSize());
  }

  @Override
  public Uni<CaptureContext> captureDocument(final InputStream is, final CaptureContext context) {
    try {
      final InputStream[] inputStreams = inputStreamMultiplier.multiply(is, 2);
      final ObjectNodePublisher<ObjectNode> publisher = new ObjectNodePublisher<>(inputStreams[0], () -> storageService.get(context.getStorageKey()));
      context.getObjectNodePublisher().set(publisher);
      return storageService.documentUpload(context, inputStreams[1]).chain(x -> {
        storageService.publishToContext(publisher, context, managedExecutor);
        captureMetrics.documentCaptured(context.getDefaultGroup());
        return Uni.createFrom().item(x);
      });
    } catch (Exception e) {
      return Uni.createFrom().failure(e);
    }
  }

  @Override
  public Uni<EPCISEvent> captureSingleEvent(final String captureString, final CaptureContext ctx) {
    final byte[] documentBytes = captureString.getBytes(StandardCharsets.UTF_8);
    final String storageKey = storageService.getStoragePath(ctx, cloudStorageConfig);
    final Map<String, String> tags = storageService.createCaptureTags(ctx);
    tags.put(CommonConstants.DOCUMENT_TYPE, Constants.EPCIS_DOCUMENT);
    storageService.documentUpload(storageKey, ctx.getCaptureID(), ctx.getContentType(), ctx.getContentLength(), tags, new ByteArrayInputStream(documentBytes));
    return captureSingleEventFromJSON(ctx.getGs1Extensions().get(), ctx.getMetadata().get(), ctx.getCaptureID(), storageKey, captureString, ctx.getDefaultGroup());
  }

  protected Uni<EPCISEvent> captureSingleEventFromJSON(Optional<String> gs1Extensions, Map<String, Object> metadata, String captureID, String storageKey, String eventJsonString, String defaultGroup) {
    return Uni.createFrom().item(eventJsonString).chain(item -> processEventJson(item, gs1Extensions, metadata, captureID, storageKey, defaultGroup)).onFailure().recoverWithUni(throwable -> Uni.createFrom().failure(CaptureValidationException.of(ERROR_WHILE_PERSISTING_EVENT, throwable)));
  }

  private Uni<EPCISEvent> processEventJson(String eventJsonString, Optional<String> gs1Extensions, Map<String, Object> metadata, String captureID, String storageKey, String defaultGroup) {
    try {
      captureMetrics.eventCaptured(defaultGroup);
      final OffsetDateTime createdAt = OffsetDateTime.now();
      final JSONObject captureJson = new JSONObject(eventJsonString);
      final ObjectNode eventNode = (ObjectNode) objectMapper.readTree(captureJson.toString());
      final JSONObject epcisDocumentJson = createEPCISDocumentJson(createdAt, eventNode);
      final ObjectNode documentNode = (ObjectNode) objectMapper.readTree(epcisDocumentJson.toString());
      final ObjectNode objectNode = EPCISEventObjectNodeUtil.wrapEventIntoEPCISDocument(eventNode, documentNode);
      final Map<String, Object> contextAsMap = EPCISEventObjectNodeUtil.collectContextInfoIntoMap(objectNode.get(Constants.CONTEXT), objectMapper);
      ContextBuilder.addGs1ExtensionInContext(gs1Extensions, contextAsMap);
      final List<InvalidEPCISEventInfo> invalidEventsInfo = new ArrayList<>();
      return generateEventHash(objectNode).chain(eventHash ->
        validateEvent(objectNode, eventNode, eventHash, contextAsMap, invalidEventsInfo, metadata).map(invalidEPCISEventInfos -> {
        createCaptureJob(captureID, storageKey, createdAt, invalidEventsInfo, defaultGroup, metadata);
        return invalidEPCISEventInfos;
      }).chain(invalidEPCISEventInfos -> {
        if (invalidEventsInfo.isEmpty()) {
          metadata.put(Constants.VISIBLE, true);
          return persistEvent(metadata, captureID, objectNode, eventNode, contextAsMap, invalidEventsInfo, defaultGroup);
        } else {
          throw new CaptureValidationException(invalidEventsInfo.stream().map(InvalidEPCISEventInfo::getDetail).collect(Collectors.joining()));
        }
      }).onFailure().recoverWithUni(throwable -> Uni.createFrom().failure(CaptureValidationException.of(ERROR_WHILE_PERSISTING_EVENT, throwable))));
    } catch (Exception e) {
      log.error(e.getMessage(), e);
      return Uni.createFrom().failure(e);
    }
  }

  private JSONObject createEPCISDocumentJson(OffsetDateTime createdAt, ObjectNode eventNode) {
    final JSONObject epcisDocumentJson = new JSONObject();
    epcisDocumentJson.put(Constants.CREATION_DATE, createdAt);
    epcisDocumentJson.put(Constants.CONTEXT, eventNode.findValue(Constants.CONTEXT).isArray() ? new JSONArray(eventNode.get(Constants.CONTEXT).toPrettyString()) : List.of(eventNode.get(Constants.CONTEXT).textValue()));
    eventNode.remove(Constants.CONTEXT);
    epcisDocumentJson.put(Constants.TYPE, Constants.EPCIS_DOCUMENT);
    epcisDocumentJson.put(Constants.SCHEMA_VERSION, "2.0");
    return epcisDocumentJson;
  }

  /**
   * The canonical CBV hash of one event, as a {@link Uni}.
   *
   * <p>This used to subscribe and read the result in the next statement:
   * {@code …collect().asList().subscribe().with(listOfHash::addAll)} followed by
   * {@code listOfHash.isEmpty() ? "" : listOfHash.get(0)}. The generator ends on
   * {@code runSubscriptionOn(worker pool)}, so the list was still empty when it
   * was read and the hash came back as the empty string — not sometimes, but as
   * the rule. Downstream that is a hard 400 with a message about GS1 extension
   * namespaces, which sends whoever reads it looking in the wrong place; the
   * document path never had the bug ({@code CaptureContextTopology} drains the
   * stream properly).
   *
   * <p>Returned as a Uni rather than awaited, because this runs on the request's
   * thread: waiting here would trade a race for a blocked event loop.
   */
  private Uni<String> generateEventHash(ObjectNode objectNode) {
    try {
      return eventHashGenerator
          .fromJson(new ByteArrayInputStream(objectNode.toString().getBytes(StandardCharsets.UTF_8)), "sha-256")
          .collect().first()
          .onItem().ifNull().continueWith("")
          .onFailure().recoverWithItem(throwable -> {
            log.warn("Issue while generating event hash for event with eventID- {} ", objectNode.get(Constants.EVENT_ID), throwable);
            return "";
          });
    } catch (IOException e) {
      log.warn("Issue while generating event hash for event with eventID- {} ", objectNode.get(Constants.EVENT_ID), e);
      return Uni.createFrom().item("");
    }
  }

  private Uni<List<InvalidEPCISEventInfo>> validateEvent(ObjectNode objectNode, ObjectNode eventNode, String eventHash, Map<String, Object> contextAsMap, List<InvalidEPCISEventInfo> invalidEventsInfo, Map<String, Object> metadata) {
    // Whether this is a correction is a property of the event, not of the path
    // it arrived on. This used to pass a hard false, so a correction captured as
    // a bare event was stored without anyone asking whether the event it
    // corrects exists — the document path (CaptureContextTopology) asked all
    // along. A correction to nothing is not a correction.
    // The capture's own metadata, not an empty map: the capture authorisation and the rules
    // judge the event against who captures it.
    return eventValidationService.validateEvent(objectNode, eventNode, new String[] {eventHash}, contextAsMap, StringUtils.EMPTY, invalidEventsInfo, 1, Collections.emptyList(), eventNode.has(Constants.ERROR_DECLARATION), metadata == null ? Collections.emptyMap() : metadata);
  }

  private void createCaptureJob(String captureID, String storageKey, OffsetDateTime createdAt, List<InvalidEPCISEventInfo> invalidEventsInfo, String defaultGroup, Map<String, Object> metadata) {
    final CaptureJobStatusMessage captureJobStatusMessage = populateCaptureJobStatusMessageForSingleEvent(captureID, createdAt, Constants.CAPTURE_ERROR_BEHAVIOUR_ROLLBACK, cloudStorageConfig.bucket(), storageKey, invalidEventsInfo);
    reactiveRepository.saveCaptureJob(captureJobStatusMessage, captureJobStatusMessage.getCaptureID(), captureJobStatusMessage.getCreatedAt(), defaultGroup, metadata).onFailure().invoke(throwable -> log.error("Error occur while saving captureJob details for captureID: {}", captureJobStatusMessage.getCaptureID(), throwable)).subscribe().with(s -> log.info("successfully saved the captureJob details for captureID: {}", captureJobStatusMessage.getCaptureID()));
  }

  private Uni<EPCISEvent> persistEvent(Map<String, Object> metadata, String captureID, ObjectNode objectNode, ObjectNode eventNode, Map<String, Object> contextAsMap, List<InvalidEPCISEventInfo> invalidEventsInfo, String defaultGroup) {
    return epcisEventPersistenceService.persistEvent(objectNode, eventNode, captureID, contextAsMap, invalidEventsInfo, false, 0, metadata, true, defaultGroup);
  }

  private CaptureJobStatusMessage populateCaptureJobStatusMessageForSingleEvent(String captureId, OffsetDateTime createdAt, String captureErrorBehaviour, String storageBucket, String storageKey, List<InvalidEPCISEventInfo> invalidEventsInfo) {
    final CaptureJobStatusMessage captureJobStatusMessage = new CaptureJobStatusMessage(captureId, false, true, createdAt, captureErrorBehaviour, OffsetDateTime.now(), storageBucket, storageKey);
    captureJobStatusMessage.setCapturedEventCount(1);
    captureJobStatusMessage.setProcessedEventCount(1);
    if (!invalidEventsInfo.isEmpty()) {
      captureJobStatusMessage.setSuccess(false);
      captureJobStatusMessage.setErrors(invalidEventsInfo);
      captureJobStatusMessage.setInvalidEventCount(1);
      captureJobStatusMessage.setValidEventCount(0);
    } else {
      captureJobStatusMessage.setInvalidEventCount(0);
      captureJobStatusMessage.setValidEventCount(1);
    }
    return captureJobStatusMessage;
  }

  public String getContextInfo(final ObjectNode individualEvent, final Map<String, Object> contextInfo) {
    final Map<String, Object> contextInfoMap = JSONUtil.getAllKeys(individualEvent).stream().map(key -> key.contains(Constants.JSON_LD_USER_EXTENSION_FIELD_SEPARATOR) ? key.split(Constants.JSON_LD_USER_EXTENSION_FIELD_SEPARATOR)[0] : key).filter(contextInfo::containsKey).collect(Collectors.toMap(key -> key, contextInfo::get, (a, b) -> a));
    return new JSONObject(contextInfoMap).toString();
  }

  /**
   * The verdict on a document, for {@code POST /events/validate}.
   *
   * <p>This used to subscribe to the publisher and read {@code documentNode} and the
   * event list in the next statement, then fire one validation per event without
   * waiting for any of them and return the verdict immediately - the same
   * fire-and-forget that returned a blank event hash, one level up. Here it decides
   * what the endpoint answers, and an empty list reads as "nothing wrong with this
   * document". Making the hash a Uni widened the window from occasional to reliable:
   * the generator hands its answer over on a worker pool, so validation had not even
   * started when the verdict went out.
   *
   * <p>Both halves are chained now. The document is drained before it is read, and
   * the verdict leaves only once every event has been validated.
   */
  @Override
  public Uni<List<InvalidEPCISEventInfo>> validateCaptureDocument(InputStream captureString) throws IOException {
    final ObjectNodePublisher<ObjectNode> publisher = new ObjectNodePublisher<>(captureString);
    final List<InvalidEPCISEventInfo> invalidEventsInfo = new ArrayList<>();
    return Multi.createFrom().publisher(publisher).collect().asList().chain(objectNodes -> {
      final ObjectNode finalDocumentNode = objectNodes.stream()
          .filter(node -> Constants.EPCIS_DOCUMENT.equals(node.get(Constants.TYPE).asText()))
          .reduce((first, second) -> second)
          .orElse(null);
      final List<ObjectNode> eventNodeList = objectNodes.stream()
          .filter(node -> !Constants.EPCIS_DOCUMENT.equals(node.get(Constants.TYPE).asText()))
          .toList();
      final AtomicInteger eventIndex = new AtomicInteger(1);
      // Concatenate rather than join: the verdict list is a plain ArrayList that every
      // validation appends to, and running them at once would have them race on it.
      return Multi.createFrom().iterable(eventNodeList).onItem().transformToUniAndConcatenate(eventNode -> {
        final ObjectNode singleEventNode = EPCISEventObjectNodeUtil.wrapEventIntoEPCISDocument(eventNode, finalDocumentNode);
        final Pair<ObjectNode, Map<String, Object>> eventAndContextPair = EPCISEventObjectNodeUtil.extractEventAndContextNodeFromDocument(singleEventNode, objectMapper);
        final Map<String, Object> contextAsMap = eventAndContextPair.getValue();
        final int index = eventIndex.getAndIncrement();
        return generateEventHash(singleEventNode)
            .chain(eventHash -> eventValidationService.validateEvent(singleEventNode, eventNode, new String[] {eventHash}, contextAsMap, StringUtils.EMPTY, invalidEventsInfo, index, Collections.emptyList(), eventNode.has(Constants.ERROR_DECLARATION), Collections.emptyMap()))
            .onFailure().recoverWithItem(throwable -> {
              log.error("Error while validating events against customValidations", throwable);
              return invalidEventsInfo;
            });
      }).collect().last().replaceWith(() -> condenseInvalidInfos(invalidEventsInfo));
    });
  }

  @Override
  public Integer getCaptureLimit() {
    return captureConfigService.limit();
  }

  @Override
  public Double getCaptureFileSizeLimit() {
    return captureConfigService.fileSizeLimit();
  }

  @Override
  public EPCISVersion getEPCISVersionMin() {
    return EPCISVersion.VERSION_2_0_0;
  }

  @Override
  public EPCISVersion getEPCISVersionMax() {
    return EPCISVersion.VERSION_2_0_1;
  }

  @Override
  public EPCISVersion getEPCISVersion() {
    return EPCISVersion.VERSION_2_0_0;
  }

  @Override
  public Collection<EPCISFormat> getSupportedEPCISFormats() {
    return Set.of(EPCISFormat.JSON_LD);
  }

  public CaptureJSONEventService() {
  }
}
