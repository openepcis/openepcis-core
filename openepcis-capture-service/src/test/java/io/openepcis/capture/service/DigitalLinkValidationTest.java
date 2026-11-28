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
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A GS1 Digital Link among the EPCs is checked like the same identifier as a URN. */
class DigitalLinkValidationTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static JsonNode event(final String json) throws Exception {
    return MAPPER.readTree(json);
  }

  @Test
  @DisplayName("a valid SGTIN Digital Link passes")
  void validDigitalLinkPasses() throws Exception {
    assertTrue(
        EPCISEventValidationService.whyDigitalLinksFail(
                event("{\"epcList\":[\"https://id.gs1.org/01/09521234543213/21/4333\"]}"))
            .isEmpty());
  }

  @Test
  @DisplayName("a GTIN of the wrong length is refused, naming the field and the value")
  void wrongGtinLengthIsRefused() throws Exception {
    final Optional<String> why =
        EPCISEventValidationService.whyDigitalLinksFail(
            event("{\"epcList\":[\"https://id.gs1.org/01/09523765458/21/4333\"]}"));
    assertTrue(why.isPresent());
    assertTrue(
        why.get().startsWith("epcList 'https://id.gs1.org/01/09523765458/21/4333'"), why.get());
  }

  @Test
  @DisplayName("parentID and childEPCs are checked as well")
  void parentAndChildrenAreChecked() throws Exception {
    assertTrue(
        EPCISEventValidationService.whyDigitalLinksFail(
                event("{\"parentID\":\"https://id.gs1.org/00/12345\"}"))
            .isPresent());
    assertTrue(
        EPCISEventValidationService.whyDigitalLinksFail(
                event("{\"childEPCs\":[\"https://id.gs1.org/01/123/21/1\"]}"))
            .isPresent());
  }

  @Test
  @DisplayName("a URI of another scheme and a URN are left alone")
  void otherSchemesPass() throws Exception {
    assertTrue(
        EPCISEventValidationService.whyDigitalLinksFail(
                event(
                    "{\"epcList\":[\"https://example.com/products/42\","
                        + "\"urn:epc:id:sgtin:0614141.107346.2017\"]}"))
            .isEmpty());
  }
}
