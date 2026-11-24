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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.openepcis.capture.rules.EventRuleEngine;
import io.openepcis.capture.rules.builtin.EventIdUniqueRule;
import io.openepcis.capture.rules.builtin.SensorReportRule;
import io.openepcis.capture.rules.builtin.UserExtensionNamespaceRule;
import io.openepcis.capture.rules.builtin.UserExtensionSchemaRule;
import io.openepcis.custom.extension.validators.UserExtensionValidator;
import io.openepcis.model.dto.InvalidEPCISEventInfo;
import io.openepcis.model.epcis.constants.CommonConstants;
import io.openepcis.repository.api.QueryRepository;
import io.openepcis.repository.model.EPCISEventES;
import io.openepcis.validation.rule.EventRule;
import io.openepcis.validation.rule.Finding;
import io.openepcis.validation.rule.RuleContext;
import io.openepcis.validation.service.JsonSchemaValidator;
import io.smallrye.mutiny.Uni;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The checks that became rules report what they reported before, in the same order. */
class BuiltinRulesReportTest {

  private static final String EVENT_ID = "ni:///sha-256;0000?ver=CBV2.0";

  private EPCISEventValidationService service(final List<EventRule> extra, final Set<String> existingIds,
      final Set<String> extensionErrors) {
    final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    final QueryRepository repository = mock(QueryRepository.class);
    when(repository.getEPCISEventESFromHash(any())).thenReturn(Uni.createFrom().item(new EPCISEventES()));
    when(repository.getEventIDs(anyList())).thenReturn(Uni.createFrom().item(existingIds));
    final UserExtensionValidator extensions = mock(UserExtensionValidator.class);
    when(extensions.validateUserExtensions(any(), any())).thenReturn(extensionErrors);
    final List<EventRule> rules = new ArrayList<>(List.of(
        new EventIdUniqueRule(repository),
        new UserExtensionSchemaRule(extensions, registry),
        new UserExtensionNamespaceRule(registry),
        new SensorReportRule()));
    rules.addAll(extra);
    return new EPCISEventValidationService(
        mock(JsonSchemaValidator.class), repository, extensions, registry, EventRuleEngine.of(rules));
  }

  private static ObjectNode event() {
    final ObjectNode event = new ObjectMapper().createObjectNode()
        .put("type", "ObjectEvent")
        .put("eventID", EVENT_ID);
    event.put("example:batch", "42");
    return event;
  }

  @Test
  @DisplayName("eventID, extension schema and namespace findings keep their texts, positions and order")
  void builtinsReportAsBefore() {
    final List<InvalidEPCISEventInfo> report =
        service(List.of(), Set.of(EVENT_ID), Set.of("example:batch must be a number"))
            .validateEventAgainstCustomValidations(event(), new ArrayList<>(), new String[] {""}, "capture-1",
                new HashMap<>(), 4, new HashMap<>())
            .await().atMost(Duration.ofSeconds(10));

    assertEquals(3, report.size());
    assertEquals("Event for eventID: " + EVENT_ID + " is already present.", report.get(0).getDetail());
    assertEquals(List.of(4), report.get(0).getSequenceInEPCISDoc());
    final var location = report.get(0).getEventLocationsInEPCISDocument().get(0);
    assertEquals(4, location.getIndex());
    assertEquals(EVENT_ID, location.getEventId());
    assertEquals("$.eventID", location.getJsonPath());
    assertEquals("/ObjectEvent/eventID", location.getXmlPath());
    assertEquals(List.of(5), report.get(1).getSequenceInEPCISDoc(), "the extension checks count from one");

    assertTrue(report.get(2).getDetail().contains("example"));
    for (final InvalidEPCISEventInfo entry : report) {
      assertEquals(CommonConstants.CAPTURE_VALIDATION_EXCEPTION, entry.getType());
      assertEquals(CommonConstants.VALIDATION_FAILED, entry.getTitle());
      assertEquals(400, entry.getStatus());
      assertEquals(InvalidEPCISEventInfo.Severity.HARD_ERROR, entry.getSeverity());
    }
  }

  @Test
  @DisplayName("an event without eventID skips the built-in rules, as it skipped the checks")
  void noEventIdNoBuiltins() {
    final ObjectNode event = event();
    event.remove("eventID");
    final List<InvalidEPCISEventInfo> report =
        service(List.of(), Set.of(EVENT_ID), Set.of("broken"))
            .validateEventAgainstCustomValidations(event, new ArrayList<>(), new String[] {""}, "capture-1",
                new HashMap<>(), 0, new HashMap<>())
            .await().atMost(Duration.ofSeconds(10));
    assertEquals(List.of(), report);
  }

  @Test
  @DisplayName("a warning does not refuse the event; it is listed in the capture metadata")
  void warningsDoNotBlock() {
    final EventRule advisory = new EventRule() {
      @Override
      public String id() {
        return "advisory";
      }

      @Override
      public Uni<List<Finding>> check(final RuleContext context) {
        return Uni.createFrom().item(List.of(Finding.soft("looks odd").at("bizStep").about(List.of("urn:epc:id:sgtin:0614141.107346.2017"))));
      }
    };
    final Map<String, Object> metadata = new HashMap<>();
    final List<InvalidEPCISEventInfo> report =
        service(List.of(advisory), Set.of(), Set.of())
            .validateEventAgainstCustomValidations(event(), new ArrayList<>(), new String[] {""}, "capture-1",
                Map.of("example", "https://example.org/"), 0, metadata)
            .await().atMost(Duration.ofSeconds(10));
    assertEquals(List.of(), report);
    @SuppressWarnings("unchecked")
    final List<Map<String, Object>> listed =
        (List<Map<String, Object>>) metadata.get(EPCISEventValidationService.VALIDATION_METADATA);
    assertEquals(1, listed.size());
    assertEquals("urn:openepcis:validation:advisory", listed.get(0).get("type"));
    assertEquals("SOFT_ERROR", listed.get(0).get("severity"));
    @SuppressWarnings("unchecked")
    final Map<String, Object> where =
        ((List<Map<String, Object>>) listed.get(0).get("eventLocationsInEPCISDocument")).get(0);
    assertEquals("$.bizStep", where.get("jsonPath"));
    assertEquals("/ObjectEvent/bizStep", where.get("xmlPath"));
    assertEquals(
        List.of(Map.of("urn", "urn:epc:id:sgtin:0614141.107346.2017",
            "digitalLink", "https://id.gs1.org/01/10614141073464/21/2017")),
        where.get("identifiers"));
  }
}
