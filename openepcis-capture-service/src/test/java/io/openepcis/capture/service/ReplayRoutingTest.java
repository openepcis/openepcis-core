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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.openepcis.capture.rules.EventRuleEngine;
import io.openepcis.capture.rules.builtin.EventIdUniqueRule;
import io.openepcis.custom.extension.validators.UserExtensionValidator;
import io.openepcis.model.dto.InvalidEPCISEventInfo;
import io.openepcis.model.epcis.EPCISEvent;
import io.openepcis.model.epcis.constants.CommonConstants;
import io.openepcis.repository.Constants;
import io.openepcis.repository.api.QueryRepository;
import io.openepcis.repository.model.CapturedByES;
import io.openepcis.repository.model.EPCISEventES;
import io.openepcis.repository.model.MetadataES;
import io.openepcis.validation.rule.Finding;
import io.openepcis.validation.rule.RuleContext;
import io.openepcis.validation.service.JsonSchemaValidator;
import io.smallrye.mutiny.Uni;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What a stored event with the same content hash means for a capture: a replay by the same
 * tenant, a share by another tenant, or the same event twice in one document.
 */
class ReplayRoutingTest {

  private static final String HASH = "ni:///sha-256;abc?ver=CBV2.0";

  private static EPCISEventES stored(final String captureID, final int sequence, final String... tenants) {
    final EPCISEventES event = new EPCISEventES();
    event.setHash(HASH);
    event.setCaptureID(captureID);
    event.setSequenceInEPCISDoc(sequence);
    final List<CapturedByES> capturedBy = new ArrayList<>();
    for (String tenant : tenants) {
      capturedBy.add(CapturedByES.builder().captureID(captureID).defaultGroup(tenant).userID(tenant + "-user").build());
    }
    event.setMetadata(MetadataES.builder().capturedBy(capturedBy).build());
    return event;
  }

  private static EPCISEventValidationService service(final EPCISEventES found) {
    final QueryRepository repository = mock(QueryRepository.class);
    when(repository.getEPCISEventESFromHash(any())).thenReturn(Uni.createFrom().item(found));
    return new EPCISEventValidationService(
        mock(JsonSchemaValidator.class),
        repository,
        mock(UserExtensionValidator.class),
        new SimpleMeterRegistry(),
        EventRuleEngine.of(List.of()));
  }

  private static Map<String, Object> metadata(final String tenant) {
    return new HashMap<>(Map.of(CommonConstants.CAPTURED_BY, Map.of("defaultGroup", tenant, "userID", tenant + "-user")));
  }

  private static List<InvalidEPCISEventInfo> check(
      final EPCISEventES found, final Map<String, Object> metadata, final String captureID, final int eventIndex) {
    final List<InvalidEPCISEventInfo> invalid = new ArrayList<>();
    service(found)
        .checkDuplicateEventsFromHash(new String[] {HASH}, invalid, eventIndex, metadata, captureID)
        .await()
        .indefinitely();
    return invalid;
  }

  @Test
  @DisplayName("the same tenant under another capture: a replay, no error")
  void sameTenantIsAReplay() {
    final Map<String, Object> metadata = metadata("acme");
    assertEquals(List.of(), check(stored("c1", 1, "acme"), metadata, "c2", 1));
    assertEquals(Boolean.TRUE, metadata.get(CommonConstants.IS_DUPLICATE));
    assertEquals(Boolean.TRUE, metadata.get(Constants.IS_REPLAY));
  }

  @Test
  @DisplayName("another tenant: shares the document, not a replay")
  void otherTenantShares() {
    final Map<String, Object> metadata = metadata("globex");
    assertEquals(List.of(), check(stored("c1", 1, "acme"), metadata, "c2", 1));
    assertEquals(Boolean.TRUE, metadata.get(CommonConstants.IS_DUPLICATE));
    assertNull(metadata.get(Constants.IS_REPLAY));
  }

  @Test
  @DisplayName("stored by this capture from another place in the document: the duplicate it always was")
  void twiceInOneDocument() {
    final Map<String, Object> metadata = metadata("acme");
    final List<InvalidEPCISEventInfo> invalid = check(stored("c1", 1, "acme"), metadata, "c1", 2);
    assertEquals(1, invalid.size());
    assertEquals("Duplicate EPCIS Event", invalid.getFirst().getDetail());
    assertNull(metadata.get(CommonConstants.IS_DUPLICATE));
  }

  @Test
  @DisplayName("this very event validated again: settled, not a replay")
  void redeliveredIsSettled() {
    final Map<String, Object> metadata = metadata("acme");
    assertEquals(List.of(), check(stored("c1", 1, "acme"), metadata, "c1", 1));
    assertEquals(Boolean.TRUE, metadata.get(CommonConstants.IS_DUPLICATE));
    assertNull(metadata.get(Constants.IS_REPLAY));
  }

  @Test
  @DisplayName("the validate-only endpoint passes an immutable map: no error, no exception")
  void validateOnly() {
    assertEquals(List.of(), check(stored("c1", 1, "acme"), Map.of(), "", 1));
  }

  private static ObjectNode withEventID(final String eventID) {
    return new ObjectMapper().createObjectNode().put("eventID", eventID).put("hash", HASH);
  }

  private static List<Finding> unique(final List<EPCISEvent> sameContentOfTenant) {
    final QueryRepository repository = mock(QueryRepository.class);
    when(repository.getEventIDs(anyList())).thenReturn(Uni.createFrom().item(Set.of("urn:uuid:1")));
    when(repository.getTenantEventsByHash(eq("acme"), eq(HASH)))
        .thenReturn(Uni.createFrom().item(sameContentOfTenant));
    return new EventIdUniqueRule(repository)
        .check(new RuleContext(withEventID("urn:uuid:1"), Map.of(), 1, List.of(), "acme", "c2", Map.of()))
        .await()
        .indefinitely();
  }

  @Test
  @DisplayName("an eventID taken by the tenant's own event with this content is a replay, not a conflict")
  void eventIdOfTheReplayedEvent() {
    final EPCISEvent same = new EPCISEvent();
    same.setEventID("urn:uuid:1");
    assertEquals(List.of(), unique(List.of(same)));
    final List<Finding> taken = unique(List.of());
    assertEquals(1, taken.size());
    assertTrue(taken.getFirst().detail().contains("already present"));
  }
}
