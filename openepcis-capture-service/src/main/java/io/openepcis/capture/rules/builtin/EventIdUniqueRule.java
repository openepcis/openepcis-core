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
package io.openepcis.capture.rules.builtin;

import io.openepcis.opentelemetry.logging.OpenEPCISLogger;
import io.openepcis.repository.api.QueryRepository;
import io.openepcis.service.util.Constants;
import io.openepcis.model.epcis.constants.CommonConstants;
import io.openepcis.validation.rule.EventRule;
import io.openepcis.validation.rule.Finding;
import io.openepcis.validation.rule.RuleContext;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import org.apache.commons.collections4.CollectionUtils;

/** An eventID may be captured once; a second event under the same id is refused. */
@ApplicationScoped
public class EventIdUniqueRule implements EventRule {

  public static final String ID = "event-id-unique";
  private static final OpenEPCISLogger log = OpenEPCISLogger.getLogger(EventIdUniqueRule.class);

  private final QueryRepository queryRepository;

  public EventIdUniqueRule(final QueryRepository queryRepository) {
    this.queryRepository = queryRepository;
  }

  @Override
  public String id() {
    return ID;
  }

  /** The type every capture validation entry had before rules; clients may match on it. */
  @Override
  public String type() {
    return CommonConstants.CAPTURE_VALIDATION_EXCEPTION;
  }

  @Override
  public String title() {
    return CommonConstants.VALIDATION_FAILED;
  }

  @Override
  public int order() {
    return 100;
  }

  @Override
  public OnFailure onFailure() {
    return OnFailure.FAIL;
  }

  @Override
  public boolean applies(final RuleContext context) {
    // an error declaration names the eventID of the event it declares erroneous: that one exists
    return context.event().has(Constants.EVENT_ID)
        && context.event().get(CommonConstants.ERROR_DECLARATION) == null;
  }

  /**
   * The eventID is taken, unless by this tenant's own event with exactly this content: that is
   * the same event sent again, an idempotent replay, which the hash check settles (it counts as
   * captured and is not stored twice).
   */
  @Override
  public Uni<List<Finding>> check(final RuleContext context) {
    final String eventID = context.event().get(Constants.EVENT_ID).asText();
    final String hash = context.event().path(Constants.HASH).asText("");
    return queryRepository
        .getEventIDs(List.of(eventID))
        .chain(
            existing -> {
              if (CollectionUtils.isEmpty(existing)) {
                return Uni.createFrom().item(List.<Finding>of());
              }
              if (hash.isBlank()) {
                return Uni.createFrom().item(taken(context));
              }
              return queryRepository
                  .getTenantEventsByHash(context.tenant(), hash)
                  .map(
                      sameContent ->
                          sameContent != null
                                  && sameContent.stream()
                                      .anyMatch(
                                          e ->
                                              e.getErrorDeclaration() == null
                                                  && eventID.equals(e.getEventID()))
                              ? List.<Finding>of()
                              : taken(context));
            });
  }

  private static List<Finding> taken(final RuleContext context) {
    log.info(
        "EPCIS document with captureId={} was not processed due to event with same eventID already present in document and "
            + "GS1-Capture-Error-Behaviour=rollback",
        context.captureId());
    return List.of(
        Finding.hard(
                "Event for eventID: "
                    + context.event().get(Constants.EVENT_ID).textValue()
                    + " is already present.")
            .at("eventID"));
  }
}
