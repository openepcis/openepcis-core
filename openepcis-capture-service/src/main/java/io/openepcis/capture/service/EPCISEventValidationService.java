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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Streams;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.openepcis.capture.rules.EventRuleEngine;
import io.openepcis.capture.spi.CaptureAuthorization;
import io.openepcis.capture.spi.OpenCaptureAuthorization;
import io.openepcis.capture.rules.FindingReport;
import io.openepcis.constants.EPCIS;
import io.openepcis.custom.extension.validators.UserExtensionValidator;
import io.openepcis.model.dto.InvalidEPCISEventInfo;
import io.openepcis.model.epcis.EPCISEvent;
import io.openepcis.model.epcis.ErrorDeclaration;
import io.openepcis.model.epcis.constants.CommonConstants;
import io.openepcis.model.epcis.exception.CaptureValidationException;
import io.openepcis.model.epcis.exception.ExceptionMessages;
import io.openepcis.opentelemetry.logging.OpenEPCISLogger;
import io.openepcis.repository.api.QueryRepository;
import io.openepcis.repository.model.CapturedByES;
import io.openepcis.repository.util.JSONUtil;
import io.openepcis.service.util.Constants;
import io.openepcis.service.util.EventPredicates;
import io.openepcis.service.util.UserExtensionExtractor;
import io.openepcis.validation.service.JsonSchemaValidator;
import io.openepcis.validation.service.SensorEventValidator;
import io.openepcis.validation.service.ValidationMessageFormatter;
import io.openepcis.validation.rule.Finding;
import io.openepcis.validation.rule.RuleContext;
import io.opentelemetry.api.baggage.Baggage;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;

@ApplicationScoped
public class EPCISEventValidationService {

  private final JsonSchemaValidator jsonSchemaValidator;

  private final QueryRepository queryRepository;

  private final UserExtensionValidator userExtensionValidator;

  private final EventRuleEngine ruleEngine;

  private final CaptureAuthorization authorization;

  final MeterRegistry meterRegistry;

  private final Counter duplicateEventCounter;

  private final Counter invalidEventCounter;

  private static final OpenEPCISLogger log =
      OpenEPCISLogger.getLogger(EPCISEventValidationService.class);

  /**
   * The search on the content hash across every event index before each write. Events stored
   * under their hash in the month index of their eventTime (EventDocuments) do not need it: the
   * create refuses the second copy and settles a second tenant. It stays on for events stored
   * before that, which have random ids in per-tenant indices; switch it off once those are
   * reindexed.
   */
  @org.eclipse.microprofile.config.inject.ConfigProperty(
      name = "openepcis.capture.duplicate-hash-search",
      defaultValue = "true")
  boolean duplicateHashSearch = true;

  public EPCISEventValidationService(
      final JsonSchemaValidator jsonSchemaValidator,
      final QueryRepository queryRepository,
      UserExtensionValidator userExtensionValidator,
      final MeterRegistry meterRegistry,
      final EventRuleEngine ruleEngine) {
    this(
        jsonSchemaValidator,
        queryRepository,
        userExtensionValidator,
        meterRegistry,
        ruleEngine,
        new OpenCaptureAuthorization());
  }

  @jakarta.inject.Inject
  public EPCISEventValidationService(
      final JsonSchemaValidator jsonSchemaValidator,
      final QueryRepository queryRepository,
      UserExtensionValidator userExtensionValidator,
      final MeterRegistry meterRegistry,
      final EventRuleEngine ruleEngine,
      final CaptureAuthorization authorization) {
    this.authorization = authorization;
    this.jsonSchemaValidator = jsonSchemaValidator;
    this.queryRepository = queryRepository;
    this.userExtensionValidator = userExtensionValidator;
    this.ruleEngine = ruleEngine;
    this.meterRegistry = meterRegistry;
    this.duplicateEventCounter = meterRegistry.counter("openepcis_validate_event_duplicate_count");
    this.invalidEventCounter = meterRegistry.counter("openepcis_validate_event_invalid_count");
  }

  public Uni<List<InvalidEPCISEventInfo>> validateEvent(
      final ObjectNode singleEventNode,
      final ObjectNode eventNode,
      final String[] arrayOfHash,
      final Map<String, Object> contextAsMap,
      final String captureID,
      final List<InvalidEPCISEventInfo> invalidEventsInfo,
      int eventIndex,
      final List<String> eventIDs,
      final boolean verifyErrorDeclarationEvent,
      final Map<String, Object> metadata) {
    return validateEventAgainstJsonSchema(singleEventNode, captureID, invalidEventsInfo, eventIndex)
        .chain(
            invalidEPCISEventInfos -> {
              final String eventHash =
                  (arrayOfHash != null && arrayOfHash.length > 0) ? arrayOfHash[0] : "";

              // Before the hash is judged: the event has to state when it happened.
              // The canonical hash is computed over the event's own statement, and
              // eventTime is part of that statement — so an event without one, or
              // with one nobody can place on a timeline, would still get a hash and
              // therefore an identity. That is the most dangerous shape of all: an
              // incomplete statement, identified. The generator does not stop it
              // either; a missing eventTime is simply left out of the pre-hash
              // string, and an eventTime without an offset throws a raw
              // DateTimeParseException at whoever called.
              // Class-level identifiers are converted when the event is stored; one that is not a
              // GS1 identifier failed only there, after the event had passed validation, and the
              // capture job then waited on an event that would never be stored. The same
              // conversion runs here, so such an event is refused like any invalid one.
              final Optional<String> unconvertible =
                  whyDigitalLinksFail(eventNode)
                      .or(() -> whyClassLevelIdentifiersFail(eventNode, contextAsMap));
              if (unconvertible.isPresent()) {
                log.error("Rejecting event: {} (captureID={}, eventIndex={})",
                    unconvertible.get(), captureID, eventIndex);
                invalidEventCounter.increment();
                invalidEPCISEventInfos.add(
                    new InvalidEPCISEventInfo(
                        CommonConstants.CAPTURE_VALIDATION_EXCEPTION,
                        CommonConstants.VALIDATION_FAILED,
                        400,
                        unconvertible.get(),
                        List.of(eventIndex)));
                return Uni.createFrom().item(invalidEPCISEventInfos);
              }

              final Optional<String> undatable = whyItCannotBeDated(eventNode);
              if (undatable.isPresent()) {
                log.error("Rejecting event: {} (captureID={}, eventIndex={})",
                    undatable.get(), captureID, eventIndex);
                invalidEPCISEventInfos.add(
                    new InvalidEPCISEventInfo(
                        CommonConstants.CAPTURE_VALIDATION_EXCEPTION,
                        CommonConstants.VALIDATION_FAILED,
                        400,
                        "The event cannot be identified because it cannot be dated: "
                            + undatable.get()
                            + ". The canonical event hash covers eventTime, so an event"
                            + " without a usable one would be stored under an identity that"
                            + " does not describe it.",
                        List.of(eventIndex)));
                return Uni.createFrom().item(invalidEPCISEventInfos);
              }

              // A blank hash means the event could not be canonicalised / pre-hashed — in
              // practice because a declared GS1-Extensions namespace has no context the
              // repository can resolve (see task: register extension schemas). Such an event
              // MUST NOT proceed: it cannot be de-duplicated and would previously either be
              // silently dropped (falsely flagged a duplicate via ""-contains-"") or silently
              // indexed without a hash. Stop here and record a definitive validation error so
              // the capture job finalises as failed with an actionable message, rather than
              // reporting success while nothing (or something un-hashable) was stored.
              if (StringUtils.isBlank(eventHash)) {
                // Do not guess the cause here. An unresolved namespace prefix does
                // NOT make the generator fail — it quietly yields a different hash.
                // A blank hash means the generator threw, or it was never asked, or
                // its answer was read before it arrived. Naming one plausible cause
                // in the message sent the last reader looking in the wrong place for
                // an afternoon.
                log.error(
                    "Rejecting event: the canonical hash came back blank (captureID={}, eventIndex={}). "
                        + "Cause unknown at this point - see any EventHashException logged just above.",
                    captureID,
                    eventIndex);
                eventNode.put(Constants.HASH, StringUtils.EMPTY);
                invalidEPCISEventInfos.add(
                    new InvalidEPCISEventInfo(
                        CommonConstants.CAPTURE_VALIDATION_EXCEPTION,
                        CommonConstants.VALIDATION_FAILED,
                        400,
                        "The canonical event hash came back blank, so the event cannot be"
                            + " de-duplicated and is refused rather than stored unidentified."
                            + " This is a fault on our side, not a defect in the document:"
                            + " re-capture, and if it persists, the capture log carries the"
                            + " reason the hash generator gave.",
                        List.of(eventIndex)));
                // Short-circuit: skip duplicate/custom validation + persistence for this event.
                return Uni.createFrom().item(invalidEPCISEventInfos);
              }

              eventNode.put(Constants.HASH, eventHash);
              return validateEventAgainstCustomValidations(
                  eventNode,
                  invalidEPCISEventInfos,
                  arrayOfHash,
                  captureID,
                  contextAsMap,
                  eventIndex,
                  metadata,
                  eventIDs);
            })
        .chain(
            invalidEPCISEventInfos ->
                verifyErrorDeclarationEvent
                    ? validateErrorDeclarationEvent(
                        eventNode, eventIDs, eventIndex, invalidEPCISEventInfos, tenantOf(metadata))
                    : Uni.createFrom().item(invalidEPCISEventInfos))
        .chain(
            invalidEPCISEventInfos ->
                validateUserExtensions(
                    eventNode, invalidEPCISEventInfos, captureID, contextAsMap, eventIndex));
  }

  /** The fields that hold instance-level identifiers. */
  private static final List<String> INSTANCE_LEVEL_FIELDS = List.of("epcList", "childEPCs", "parentID");

  /**
   * Why a GS1 Digital Link among the event's instance-level identifiers is invalid, if one is. A
   * URI the translator does not take for a GS1 identifier belongs to another scheme and passes, as
   * it does when stored. One it takes for a GS1 identifier and refuses, such as a GTIN of the
   * wrong length, is refused here, like the same identifier as a URN.
   */
  static Optional<String> whyDigitalLinksFail(final com.fasterxml.jackson.databind.JsonNode eventNode) {
    for (final String field : INSTANCE_LEVEL_FIELDS) {
      final com.fasterxml.jackson.databind.JsonNode node = eventNode.path(field);
      final Iterable<com.fasterxml.jackson.databind.JsonNode> values =
          node.isArray() ? node : node.isTextual() ? List.of(node) : List.of();
      for (final com.fasterxml.jackson.databind.JsonNode value : values) {
        final String epc = value.asText("");
        if (!epc.startsWith("http://") && !epc.startsWith("https://")) {
          continue;
        }
        try {
          io.openepcis.identifiers.converter.util.ConverterUtil.toURN(epc);
        } catch (io.openepcis.core.exception.UnsupportedGS1IdentifierException e) {
          // another identifier scheme
        } catch (io.openepcis.core.exception.ValidationException e) {
          return Optional.of(
              field + " '" + epc + "' is not a valid GS1 Digital Link: " + e.getMessage());
        }
      }
    }
    return Optional.empty();
  }

  /** The quantity lists whose epcClass is converted to a class-level identifier when stored. */
  private static final List<String> QUANTITY_LISTS =
      List.of("quantityList", "childQuantityList", "inputQuantityList", "outputQuantityList");

  /**
   * Why an epcClass of the event cannot be converted the way storing it converts it, if one
   * cannot. A value under a namespace the document declares is a user extension and passes, as
   * it does when stored.
   */
  static Optional<String> whyClassLevelIdentifiersFail(
      final com.fasterxml.jackson.databind.JsonNode eventNode, final Map<String, Object> contextAsMap) {
    final List<Object> context =
        contextAsMap == null ? List.of() : List.of(contextAsMap);
    for (final String list : QUANTITY_LISTS) {
      final com.fasterxml.jackson.databind.JsonNode elements = eventNode.path(list);
      if (!elements.isArray()) {
        continue;
      }
      for (final com.fasterxml.jackson.databind.JsonNode element : elements) {
        final String epcClass = element.path("epcClass").asText("");
        if (epcClass.isEmpty()) {
          continue;
        }
        try {
          io.openepcis.repository.util.EPCFormatUtil.buildClassLevelEpc(epcClass, context);
        } catch (Exception e) {
          return Optional.of(
              list + ".epcClass '" + epcClass + "' is not a GS1 class-level identifier"
                  + " (EPC class URN or GS1 Digital Link): " + e.getMessage());
        }
      }
    }
    return Optional.empty();
  }

  private Uni<List<InvalidEPCISEventInfo>> validateEventAgainstJsonSchema(
      final ObjectNode singleEventNode,
      final String captureID,
      final List<InvalidEPCISEventInfo> invalidEventsInfo,
      int eventIndex) {
    Baggage baggage = Baggage.current();
    return Uni.createFrom()
        .item(invalidEventsInfo)
        .map(
            invalidEPCISEventInfos -> {
              try {
                jsonSchemaValidator.validateAgainstCaptureJSONSchema(singleEventNode.toString());
              } catch (Throwable t) {
                log.error(
                    "EPCIS document with captureId={} was not processed because of json schema validation failure\" +\n"
                        + "                    \"GS1-Capture-Error-Behaviour=rollback\"",
                    captureID);
                invalidEventCounter.increment();
                invalidEventsInfo.add(
                    new InvalidEPCISEventInfo(
                        CommonConstants.CAPTURE_VALIDATION_EXCEPTION,
                        CommonConstants.VALIDATION_FAILED,
                        400,
                        t.getMessage() + (1),
                        List.of(eventIndex)));
              }
              return invalidEPCISEventInfos;
            });
  }

  // Validate the event for the user extensions and populate the InvalidEPCISEventInfo if contains
  // invalid information.
  private Uni<List<InvalidEPCISEventInfo>> validateUserExtensions(
      final ObjectNode eventNode,
      final List<InvalidEPCISEventInfo> invalidEventsInfo,
      final String captureID,
      final Map<String, Object> contextAsMap,
      final int eventIndex) {
    return Uni.createFrom()
        .item(invalidEventsInfo)
        .map(
            invalidEPCISEventInfos -> {
              try {
                UserExtensionExtractor.getUserExtensions(
                    eventNode, "", contextAsMap, new HashSet<>());
              } catch (Exception e) {
                log.error(
                    "ERROR occurred while validating the user extensions captureID : {} error: {}",
                    captureID,
                    e.getMessage());
                invalidEventCounter.increment();
                invalidEventsInfo.add(
                    new InvalidEPCISEventInfo(
                        CommonConstants.CAPTURE_VALIDATION_EXCEPTION,
                        CommonConstants.VALIDATION_FAILED,
                        400,
                        e.getMessage(),
                        List.of(eventIndex)));
              }
              return invalidEPCISEventInfos;
            });
  }

  public Uni<List<InvalidEPCISEventInfo>> validateEventAgainstCustomValidations(
      final ObjectNode eventNode,
      final List<InvalidEPCISEventInfo> invalidEventsInfoList,
      final String[] arrayOfHash,
      final String captureID,
      final Map<String, Object> contextAsMap,
      final int eventIndex,
      final Map<String, Object> metadata) {
    return validateEventAgainstCustomValidations(
        eventNode, invalidEventsInfoList, arrayOfHash, captureID, contextAsMap, eventIndex, metadata,
        List.of());
  }

  /**
   * The hash duplicate check, then the {@link io.openepcis.validation.rule.EventRule}s. The duplicate
   * check stays here: it decides whether the event is stored or only gains another capturer, which
   * is not a finding but a routing decision.
   */
  public Uni<List<InvalidEPCISEventInfo>> validateEventAgainstCustomValidations(
      final ObjectNode eventNode,
      final List<InvalidEPCISEventInfo> invalidEventsInfoList,
      final String[] arrayOfHash,
      final String captureID,
      final Map<String, Object> contextAsMap,
      final int eventIndex,
      final Map<String, Object> metadata,
      final List<String> eventIDs) {
    final Uni<Void> duplicateCheckUni =
        eventNode.get(CommonConstants.ERROR_DECLARATION) == null && duplicateHashSearch
            ? checkDuplicateEventsFromHash(
                arrayOfHash, invalidEventsInfoList, eventIndex, metadata, captureID)
            : Uni.createFrom().voidItem();
    // Authorisation, not data quality: checked on every path, before and independent of the
    // configurable rule engine.
    final Map<String, Object> claims = metadata == null ? Map.of() : authorization.claims(metadata);
    final RuleContext context =
        new RuleContext(
            eventNode, contextAsMap, eventIndex, eventIDs, tenantOf(metadata), captureID, Map.of(), claims);
    final List<Finding> refused = authorization.check(context);
    if (!refused.isEmpty()) {
      return duplicateCheckUni.map(
          ignored -> {
            for (final Finding finding : refused) {
              invalidEventsInfoList.add(FindingReport.toInvalidEventInfo(finding, eventNode, eventIndex));
            }
            return invalidEventsInfoList;
          });
    }
    return duplicateCheckUni
        .chain(() -> ruleEngine.run(context))
        .map(
            findings -> {
              for (final Finding finding : findings) {
                final InvalidEPCISEventInfo entry =
                    FindingReport.toInvalidEventInfo(finding, eventNode, eventIndex);
                if (finding.severity() == null || finding.severity().blocks()) {
                  invalidEventsInfoList.add(entry);
                } else {
                  recordNonBlocking(finding, entry, captureID, metadata);
                }
              }
              return invalidEventsInfoList;
            });
  }

  /**
   * A SOFT_ERROR does not keep the event out. It is logged and listed, in the shape of the report
   * entry, under {@code metadata.validation} of the capture message, where the persistence picks it up.
   */
  @SuppressWarnings("unchecked")
  private static void recordNonBlocking(
      final Finding finding,
      final InvalidEPCISEventInfo entry,
      final String captureID,
      final Map<String, Object> metadata) {
    log.info(
        "Validation {} from rule {} on capture {}: {}",
        finding.severity(),
        finding.ruleId(),
        captureID,
        finding.detail());
    if (metadata == null) {
      return;
    }
    try {
      ((List<Object>) metadata.computeIfAbsent(VALIDATION_METADATA, key -> new ArrayList<>()))
          .add(new ObjectMapper().convertValue(entry, Map.class));
    } catch (UnsupportedOperationException | ClassCastException e) {
      // the validate-only endpoints pass an immutable metadata map; the log line is the record
    }
  }

  /** Key in the capture metadata under which non-blocking findings are listed. */
  public static final String VALIDATION_METADATA = io.openepcis.repository.model.MetadataES.VALIDATION;

  private static String tenantOf(final Map<String, Object> metadata) {
    if (metadata == null || metadata.get(CommonConstants.CAPTURED_BY) == null) {
      return null;
    }
    try {
      final CapturedByES capturedBy =
          new ObjectMapper().convertValue(metadata.get(CommonConstants.CAPTURED_BY), CapturedByES.class);
      return capturedBy == null ? null : capturedBy.getDefaultGroup();
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  /**
   * Finds the event a declaration declares erroneous, the way EPCIS 2.0 7.4.1.2 defines it: the
   * declaration "SHALL be otherwise identical to a prior event", every field equal except
   * errorDeclaration and recordTime, the eventID included.
   *
   * <p>The content hash leaves out errorDeclaration, eventID and recordTime (CBV 8.9.2), so a
   * stored event with the declaration's hash is an event with exactly the declaration's content.
   * That is the match. With an eventID, the matched event also has to carry that eventID; without
   * one, the declaration takes the eventID of the event it matched. Only the tenant's own events
   * are looked at.
   *
   * <p>Before, an eventID that merely existed was enough, whatever the content, and a declaration
   * without one was stored under its hash even when the original had an eventID of its own.
   */
  private Uni<List<InvalidEPCISEventInfo>> validateErrorDeclarationEvent(
      ObjectNode eventNode,
      List<String> eventIDs,
      int eventIndex,
      final List<InvalidEPCISEventInfo> invalidEventInfoList,
      final String tenant) {
    return matchErroneousEvent(queryRepository, eventNode, eventIndex, invalidEventInfoList, tenant)
        .chain(
            ignored ->
                checkErrorDeclaration(eventNode, eventIDs, eventIndex, invalidEventInfoList, tenant));
  }

  static Uni<List<InvalidEPCISEventInfo>> matchErroneousEvent(
      final QueryRepository queryRepository,
      final ObjectNode eventNode,
      final int eventIndex,
      final List<InvalidEPCISEventInfo> invalidEventInfoList,
      final String tenant) {
    final String hash = eventNode.path(Constants.HASH).asText("");
    final String declaredID =
        eventNode.hasNonNull(Constants.EVENT_ID) ? eventNode.get(Constants.EVENT_ID).asText() : null;
    log.info("Validating error declaration (eventID {}, hash {})", declaredID, hash);
    return queryRepository
        .getTenantEventsByHash(tenant, hash)
        .map(sameContent -> sameContent == null ? List.<EPCISEvent>of() : sameContent)
        .chain(
            sameContent -> {
              // One declaration per event: it is stored under the event's hash with a qualifier
              // (EventDocuments), and a second has nothing to add that the first did not say.
              // A repeat of the stored declaration is a replay: it passes, and the store
              // settles it like any replay (EventRepository: the create conflict compares them).
              final Optional<EPCISEvent> storedDeclaration =
                  sameContent.stream().filter(e -> e.getErrorDeclaration() != null).findFirst();
              if (storedDeclaration.isPresent()) {
                if (sameDeclaration(storedDeclaration.get(), eventNode)
                    && (declaredID == null
                        || declaredID.equals(storedDeclaration.get().getEventID()))) {
                  if (declaredID == null
                      && StringUtils.isNotBlank(storedDeclaration.get().getEventID())) {
                    eventNode.put(Constants.EVENT_ID, storedDeclaration.get().getEventID());
                  }
                  return Uni.createFrom().item(invalidEventInfoList);
                }
                invalidEventInfoList.add(
                    declarationError(
                        eventIndex,
                        "This event already has an error declaration, and it says something"
                            + " else. There is one per event; to correct the correction,"
                            + " capture a corrective event."));
                return Uni.createFrom().item(invalidEventInfoList);
              }
              final List<EPCISEvent> originals = sameContent;
              if (declaredID == null) {
                if (originals.isEmpty()) {
                  invalidEventInfoList.add(
                      declarationError(
                          eventIndex,
                          "This error declaration names no eventID, and no event of yours has"
                              + " exactly its content. A declaration has to repeat the erroneous"
                              + " event unchanged and only add errorDeclaration (EPCIS 2.0"
                              + " 7.4.1.2), so either a field differs from the event you mean or"
                              + " that event was never captured."));
                  return Uni.createFrom().item(invalidEventInfoList);
                }
                final String originalID = originals.get(0).getEventID();
                eventNode.put(
                    Constants.EVENT_ID, StringUtils.isNotBlank(originalID) ? originalID : hash);
                return Uni.createFrom().item(invalidEventInfoList);
              }
              if (originals.stream().anyMatch(e -> declaredID.equals(e.getEventID()))) {
                return Uni.createFrom().item(invalidEventInfoList);
              }
              if (!originals.isEmpty()) {
                invalidEventInfoList.add(
                    declarationError(
                        eventIndex,
                        "The event with exactly this content has the eventID "
                            + originals.get(0).getEventID()
                            + ", not "
                            + declaredID
                            + ". An error declaration carries the eventID of the event it"
                            + " declares erroneous (EPCIS 2.0 7.4.1.2)."));
                return Uni.createFrom().item(invalidEventInfoList);
              }
              return queryRepository
                  .getTenantEventIDs(tenant, List.of(declaredID))
                  .map(
                      known -> {
                        invalidEventInfoList.add(
                            declarationError(
                                eventIndex,
                                known != null && known.contains(declaredID)
                                    ? "The event "
                                        + declaredID
                                        + " exists, but this declaration does not repeat it"
                                        + " unchanged: some field differs. A declaration has to be"
                                        + " identical to the erroneous event except for"
                                        + " errorDeclaration (EPCIS 2.0 7.4.1.2). Copy the event"
                                        + " as captured and add only errorDeclaration."
                                    : "There is no event "
                                        + declaredID
                                        + " to declare erroneous. Capture the event first, or"
                                        + " check the eventID."));
                        return invalidEventInfoList;
                      });
            });
  }

  /**
   * Whether the declaration in the capture says what the stored one says: the same
   * declarationTime, reason (in any of its spellings) and correctiveEventIDs. Extensions are
   * compared where the declaration is stored.
   */
  static boolean sameDeclaration(final EPCISEvent stored, final ObjectNode eventNode) {
    final ErrorDeclaration declared = stored.getErrorDeclaration();
    final JsonNode captured = eventNode.get(Constants.ERROR_DECLARATION);
    if (declared == null || captured == null || !captured.isObject()) {
      return false;
    }
    final OffsetDateTime capturedTime;
    try {
      capturedTime =
          captured.hasNonNull("declarationTime")
              ? OffsetDateTime.parse(captured.get("declarationTime").asText())
              : null;
    } catch (java.time.format.DateTimeParseException e) {
      return false;
    }
    if (declared.getDeclarationTime() == null
        ? capturedTime != null
        : capturedTime == null || !declared.getDeclarationTime().isEqual(capturedTime)) {
      return false;
    }
    final String capturedReason =
        captured.hasNonNull("reason") ? captured.get("reason").asText() : null;
    if (!Objects.equals(reasonOf(declared.getReason()), reasonOf(capturedReason))) {
      return false;
    }
    final Set<String> capturedCorrections = new HashSet<>();
    if (captured.has(Constants.CORRECTIVE_EVENT_IDS)) {
      captured.get(Constants.CORRECTIVE_EVENT_IDS).forEach(id -> capturedCorrections.add(id.asText()));
    }
    final Set<String> storedCorrections =
        declared.getCorrectiveEventIDs() == null
            ? Set.of()
            : new HashSet<>(declared.getCorrectiveEventIDs());
    return storedCorrections.equals(capturedCorrections);
  }

  private static String reasonOf(final String reason) {
    return reason == null ? null : ERROR_REASONS.getOrDefault(reason, reason);
  }

  private static InvalidEPCISEventInfo declarationError(final int eventIndex, final String detail) {
    return new InvalidEPCISEventInfo(
        CommonConstants.CAPTURE_VALIDATION_EXCEPTION,
        CommonConstants.VALIDATION_FAILED,
        400,
        detail,
        List.of(eventIndex));
  }

  /**
   * The two things a declaration has to get right about itself.
   *
   * <p>Kept together because they are one rule read from two sides: CBV defines
   * exactly two reasons, and one of them — {@code did_not_occur} — says the
   * event never happened, which is why it may not also point at events that put
   * it right. There is nothing to correct about something that did not occur.
   */
  private Uni<List<InvalidEPCISEventInfo>> checkErrorDeclaration(
      final ObjectNode eventNode,
      final List<String> eventIDs,
      final int eventIndex,
      final List<InvalidEPCISEventInfo> invalidEventInfoList,
      final String tenant) {
    checkReason(eventNode, eventIndex, invalidEventInfoList);
    return checkCorrectiveEvents(
        queryRepository, eventNode, eventIDs, eventIndex, invalidEventInfoList, tenant);
  }

  public Uni<Void> checkDuplicateEventsFromHash(
      String[] arrayOfHash,
      List<InvalidEPCISEventInfo> invalidEPCISEventInfoList,
      int eventIndex,
      Map<String, Object> metadata) {
    return checkDuplicateEventsFromHash(
        arrayOfHash, invalidEPCISEventInfoList, eventIndex, metadata, null);
  }

  /**
   * The search on the content hash, for events stored before they were stored under it.
   *
   * <p>What a stored event with the same hash means:
   *
   * <ul>
   *   <li>captured by another tenant: the event gains that tenant in {@code capturedBy}
   *       ({@link CommonConstants#IS_DUPLICATE});
   *   <li>captured by the same tenant under another capture: an idempotent replay. It counts as
   *       captured and gains this capture in {@code capturedBy}, but it is not stored again and no
   *       event-saved goes out for it ({@link io.openepcis.repository.Constants#IS_REPLAY});
   *   <li>stored by this capture at another place in the document: the document holds the event
   *       twice, which is refused as before.
   * </ul>
   */
  public Uni<Void> checkDuplicateEventsFromHash(
      String[] arrayOfHash,
      List<InvalidEPCISEventInfo> invalidEPCISEventInfoList,
      int eventIndex,
      Map<String, Object> metadata,
      String captureID) {
    final Set<String> uniqueHashes = new HashSet<>();
    final List<Integer> twiceInDocument = new ArrayList<>();
    final List<Integer> storedIndexes = new ArrayList<>();
    final CapturedByES capturedByES =
        new ObjectMapper()
            .convertValue(metadata.get(CommonConstants.CAPTURED_BY), CapturedByES.class);

    return queryRepository
        .getEPCISEventESFromHash(arrayOfHash)
        .onItem()
        .invoke(
            epcisEventES -> {
              final String hashes =
                  epcisEventES.getHash() != null ? epcisEventES.getHash() : StringUtils.EMPTY;

              for (int i = 0; i < arrayOfHash.length; i++) {
                // A blank hash is NOT a duplicate signal. It means the event could not be
                // pre-hashed (e.g. an extension context the repo cannot resolve). Guard it:
                // otherwise `"".contains("")` is true and every unhashable event is falsely
                // flagged a cross-tenant duplicate, routed to metadata-update, and silently
                // never indexed. Skipping here sends it down the normal index path instead;
                // the empty-hash condition is surfaced separately (see the arrayOfHash warn).
                if (StringUtils.isBlank(arrayOfHash[i])) {
                  continue;
                }
                if (!uniqueHashes.add(arrayOfHash[i])) {
                  twiceInDocument.add(i + eventIndex);
                }

                if (StringUtils.isNotBlank(hashes) && hashes.contains(arrayOfHash[i])) {
                  storedIndexes.add(i + eventIndex);
                }
              }

              if (!storedIndexes.isEmpty()) {
                // An existing hash matched. Who captured it decides what it is. Some historical
                // records have a null `metadata` (observed on events captured by older builds, or
                // when a capture job crashed before metadata was persisted). Treat missing metadata
                // conservatively as "not the same user" so tenant isolation is preserved rather
                // than NPE'ing out of the validation pipeline.
                final boolean writtenByThisCapture =
                    StringUtils.isNotBlank(captureID) && captureID.equals(epcisEventES.getCaptureID());
                final boolean addedToThisCapture =
                    StringUtils.isNotBlank(captureID)
                        && io.openepcis.repository.util.EventDocuments.capturedIn(
                            epcisEventES, captureID);
                boolean isUserIdOrGroupMatch = false;
                if (capturedByES == null) {
                  // no capturer to tell apart (single tenant, or the validate-only endpoint):
                  // the same content again is a replay
                  isUserIdOrGroupMatch = true;
                } else if (epcisEventES.getMetadata() == null
                    || epcisEventES.getMetadata().getCapturedBy() == null) {
                  log.warn(
                      "Hash-duplicate match on existing event with missing metadata.capturedBy; treating as cross-tenant (eventIndex={})",
                      eventIndex);
                } else {
                  isUserIdOrGroupMatch =
                      epcisEventES.getMetadata().getCapturedBy().stream()
                          .filter(Objects::nonNull)
                          .anyMatch(
                              r ->
                                  Objects.equals(r.getUserID(), capturedByES.getUserID())
                                      || (r.getDefaultGroup() != null
                                          && r.getDefaultGroup()
                                              .equalsIgnoreCase(capturedByES.getDefaultGroup())));
                }

                duplicateEventCounter.increment();
                if (writtenByThisCapture
                    && !Objects.equals(epcisEventES.getSequenceInEPCISDoc(), eventIndex)) {
                  // stored by this capture from another place in the document
                  twiceInDocument.addAll(storedIndexes);
                } else if (writtenByThisCapture || addedToThisCapture) {
                  // this very event, validated again (a redelivered message): it is settled
                  route(metadata, CommonConstants.IS_DUPLICATE);
                  if (!io.openepcis.repository.util.EventDocuments.newInCapture(
                      epcisEventES, captureID)) {
                    route(metadata, io.openepcis.repository.Constants.IS_REPLAY);
                  }
                } else if (!isUserIdOrGroupMatch) {
                  route(metadata, CommonConstants.IS_DUPLICATE);
                } else {
                  log.debug(
                      "Replay of a stored EPCIS event by the same tenant at index : {}",
                      storedIndexes);
                  route(metadata, CommonConstants.IS_DUPLICATE);
                  route(metadata, io.openepcis.repository.Constants.IS_REPLAY);
                }
              }

              if (!twiceInDocument.isEmpty()) {
                log.debug("Duplicate EPCIS event detected at index : {}", twiceInDocument);
                invalidEPCISEventInfoList.add(
                    new InvalidEPCISEventInfo(
                        CommonConstants.CAPTURE_VALIDATION_EXCEPTION,
                        CommonConstants.VALIDATION_FAILED,
                        400,
                        "Duplicate EPCIS Event",
                        twiceInDocument));
              }
            })
        .replaceWithVoid();
  }

  /** Marks the event's routing in its capture metadata; the validate-only endpoints pass none. */
  private static void route(final Map<String, Object> metadata, final String key) {
    try {
      metadata.put(key, Boolean.TRUE);
    } catch (UnsupportedOperationException e) {
      // an immutable map: nothing is persisted on that path
    }
  }

  /**
   * Whether the events this declaration says put the error right actually exist.
   *
   * <p>They used to be looked for in the capture document alone. For a sender
   * that captures event by event — the normal case, and the only case for a
   * connector — that meant every correction was refused whose corrective event
   * had been captured earlier: it was not in this document, so it counted as
   * missing. The document is still asked first, because an id in the same
   * document is answered without a round trip; what is left over is asked of
   * the repository.
   */
  static Uni<List<InvalidEPCISEventInfo>> checkCorrectiveEvents(
      final QueryRepository queryRepository,
      final ObjectNode eventNode,
      final List<String> eventIDs,
      final int eventIndex,
      final List<InvalidEPCISEventInfo> invalidEPCISEventInfoList,
      final String tenant) {
    final JsonNode errorDeclaration = eventNode.get(Constants.ERROR_DECLARATION);
    if (errorDeclaration == null || !errorDeclaration.has(Constants.CORRECTIVE_EVENT_IDS)) {
      return Uni.createFrom().item(invalidEPCISEventInfoList);
    }
    final List<String> correctiveEventIDs =
        Streams.stream(errorDeclaration.get(Constants.CORRECTIVE_EVENT_IDS).elements())
            .map(JsonNode::asText)
            .collect(Collectors.toList());
    if (!EventPredicates.HAS_CORRECTIVE_EVENT_ID.test(correctiveEventIDs)) {
      return Uni.createFrom().item(invalidEPCISEventInfoList);
    }
    final String ownID = eventNode.path(Constants.EVENT_ID).asText("");
    if (StringUtils.isNotBlank(ownID) && correctiveEventIDs.contains(ownID)) {
      // The declared eventID is the erroneous event's own: it cannot be its own correction.
      invalidEPCISEventInfoList.add(
          declarationError(
              eventIndex,
              "correctiveEventIDs names "
                  + ownID
                  + ", the event this declaration declares erroneous. A corrective event is a"
                  + " different event with its own eventID."));
      return Uni.createFrom().item(invalidEPCISEventInfoList);
    }
    final List<String> notInThisDocument =
        CollectionUtils.isNotEmpty(eventIDs)
            ? correctiveEventIDs.stream()
                .filter(id -> !eventIDs.contains(id))
                .collect(Collectors.toList())
            : correctiveEventIDs;
    if (CollectionUtils.isEmpty(notInThisDocument)) {
      return Uni.createFrom().item(invalidEPCISEventInfoList);
    }
    return queryRepository
        .getTenantEventIDs(tenant, notInThisDocument)
        .map(
            known -> {
              final List<String> missing =
                  notInThisDocument.stream()
                      .filter(id -> known == null || !known.contains(id))
                      .collect(Collectors.toList());
              if (CollectionUtils.isNotEmpty(missing)) {
                log.info("EventIDs of missing corrective EPCIS events: {}", missing);
                invalidEPCISEventInfoList.add(
                    new InvalidEPCISEventInfo(
                        CommonConstants.CAPTURE_VALIDATION_EXCEPTION,
                        CommonConstants.VALIDATION_FAILED,
                        400,
                        "EventIDs of missing corrective EPCIS events: " + missing,
                        List.of(eventIndex)));
              }
              return invalidEPCISEventInfoList;
            });
  }

  /** The two reasons CBV defines, in the three spellings it allows. */
  private static final Map<String, String> ERROR_REASONS =
      Map.of(
          "did_not_occur", "did_not_occur",
          "urn:epcglobal:cbv:er:did_not_occur", "did_not_occur",
          "https://ref.gs1.org/cbv/ER-did_not_occur", "did_not_occur",
          "incorrect_data", "incorrect_data",
          "urn:epcglobal:cbv:er:incorrect_data", "incorrect_data",
          "https://ref.gs1.org/cbv/ER-incorrect_data", "incorrect_data");

  /**
   * The reason, and what it is allowed to be combined with.
   *
   * <p>CBV defines exactly two error reasons and forbids one combination:
   * {@code did_not_occur} must not carry {@code correctiveEventIDs}. The reason
   * is not a formality — {@code did_not_occur} withdraws the event outright,
   * and events that "put it right" would contradict the withdrawal. Neither the
   * closed list nor the combination was checked anywhere, so both a typo and a
   * contradiction were stored as stated.
   */
  static void checkReason(
      final ObjectNode eventNode,
      final int eventIndex,
      final List<InvalidEPCISEventInfo> invalidEPCISEventInfoList) {
    final JsonNode errorDeclaration = eventNode.get(Constants.ERROR_DECLARATION);
    if (errorDeclaration == null || !errorDeclaration.has(Constants.REASON)) {
      return;
    }
    final String stated = errorDeclaration.get(Constants.REASON).asText("");
    final String reason = ERROR_REASONS.get(stated);
    if (reason == null) {
      invalidEPCISEventInfoList.add(
          new InvalidEPCISEventInfo(
              CommonConstants.CAPTURE_VALIDATION_EXCEPTION,
              CommonConstants.VALIDATION_FAILED,
              400,
              "'"
                  + stated
                  + "' is not an error reason. CBV defines two, did_not_occur and"
                  + " incorrect_data, as a bare string, a urn:epcglobal:cbv:er: URN or a"
                  + " https://ref.gs1.org/cbv/ER- URI.",
              List.of(eventIndex)));
      return;
    }
    if ("did_not_occur".equals(reason)
        && errorDeclaration.has(Constants.CORRECTIVE_EVENT_IDS)
        && errorDeclaration.get(Constants.CORRECTIVE_EVENT_IDS).size() > 0) {
      invalidEPCISEventInfoList.add(
          new InvalidEPCISEventInfo(
              CommonConstants.CAPTURE_VALIDATION_EXCEPTION,
              CommonConstants.VALIDATION_FAILED,
              400,
              "An error declaration with reason did_not_occur must not name"
                  + " correctiveEventIDs: the event is withdrawn, and there is nothing"
                  + " for a corrective event to put right (CBV).",
              List.of(eventIndex)));
    }
  }

  /**
   * Why this event cannot be placed on a timeline, if it cannot.
   *
   * <p>Two shapes, and both have to be named separately, because they are
   * different mistakes. An absent {@code eventTime} is an incomplete statement.
   * An {@code eventTime} without a UTC offset is a complete-looking statement
   * that means nothing without knowing whose clock it came off — and EPCIS
   * requires the offset for exactly that reason.
   */
  static Optional<String> whyItCannotBeDated(final ObjectNode eventNode) {
    final JsonNode eventTime = eventNode == null ? null : eventNode.get(EPCIS.EVENT_TIME);
    if (eventTime == null || eventTime.isNull() || StringUtils.isBlank(eventTime.asText())) {
      return Optional.of("it states no eventTime");
    }
    try {
      OffsetDateTime.parse(eventTime.asText());
      return Optional.empty();
    } catch (DateTimeParseException e) {
      return Optional.of("its eventTime '" + eventTime.asText() + "' is not an ISO-8601 instant"
          + " with a UTC offset (for example 2026-08-10T09:00:00.000Z or 2026-08-10T11:00:00+02:00)");
    }
  }
}
