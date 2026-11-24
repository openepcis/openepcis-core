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

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * An epcClass that is not a GS1 class-level identifier is refused in validation. It used to pass
 * validation and fail only when stored, which left the capture job waiting.
 */
class ClassLevelIdentifierValidationTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static JsonNode objectEvent(final String epcClass) throws Exception {
    return MAPPER.readTree(
        "{\"type\":\"ObjectEvent\",\"quantityList\":[{\"epcClass\":\""
            + epcClass
            + "\",\"quantity\":10,\"uom\":\"KGM\"}]}");
  }

  @Test
  @DisplayName("GS1 class-level identifiers pass, as URN and as Digital Link")
  void gs1IdentifiersPass() throws Exception {
    assertTrue(
        EPCISEventValidationService.whyClassLevelIdentifiersFail(
                objectEvent("urn:epc:class:lgtin:4012345.012345.998877"), Map.of())
            .isEmpty());
    assertTrue(
        EPCISEventValidationService.whyClassLevelIdentifiersFail(
                objectEvent("https://id.gs1.org/01/04012345123456/10/998877"), Map.of())
            .isEmpty());
  }

  @Test
  @DisplayName("a made-up identifier is refused, naming the list and the value")
  void madeUpIdentifierIsRefused() throws Exception {
    final Optional<String> why =
        EPCISEventValidationService.whyClassLevelIdentifiersFail(
            objectEvent("ACME-WIDGET-42"), Map.of());
    assertTrue(why.isPresent());
    assertTrue(why.get().contains("quantityList.epcClass 'ACME-WIDGET-42'"), why.get());
  }

  @Test
  @DisplayName("a value under a namespace the document declares is a user extension and passes")
  void declaredExtensionPasses() throws Exception {
    assertTrue(
        EPCISEventValidationService.whyClassLevelIdentifiersFail(
                objectEvent("ex:widget-42"), Map.of("ex", "https://example.com/epcis/"))
            .isEmpty());
  }

  @Test
  @DisplayName("child, input and output quantity lists are checked as well")
  void otherListsAreChecked() throws Exception {
    final JsonNode event =
        MAPPER.readTree(
            "{\"type\":\"TransformationEvent\",\"outputQuantityList\":[{\"epcClass\":\"NOT-GS1\"}]}");
    final Optional<String> why =
        EPCISEventValidationService.whyClassLevelIdentifiersFail(event, Map.of());
    assertTrue(why.isPresent());
    assertTrue(why.get().startsWith("outputQuantityList.epcClass"), why.get());
  }
}
