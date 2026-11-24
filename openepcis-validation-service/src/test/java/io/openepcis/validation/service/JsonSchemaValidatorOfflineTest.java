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
package io.openepcis.validation.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.openepcis.model.epcis.exception.CaptureValidationException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Capture validation works offline: the GS1 EPCIS JSON schema is bundled, nothing is fetched
 * from ref.gs1.org on first use.
 */
class JsonSchemaValidatorOfflineTest {

  private static final String VALID =
      """
      {
        "@context": ["https://ref.gs1.org/standards/epcis/2.0.0/epcis-context.jsonld"],
        "type": "EPCISDocument",
        "schemaVersion": "2.0",
        "creationDate": "2026-11-23T09:00:00.000Z",
        "epcisBody": {
          "eventList": [
            {
              "type": "ObjectEvent",
              "eventTime": "2026-11-23T08:00:00.000+01:00",
              "eventTimeZoneOffset": "+01:00",
              "epcList": ["urn:epc:id:sgtin:0614141.107346.2017"],
              "action": "OBSERVE",
              "bizStep": "shipping"
            }
          ]
        }
      }
      """;

  @Test
  @DisplayName("no schema the validator loads points at a remote location")
  void schemasAreBundled() throws IOException {
    for (final String path :
        new String[] {"/modular-EPCIS-JSON-schema.json", "/json-schema/epcis-json-schema.json"}) {
      try (InputStream in = JsonSchemaValidator.class.getResourceAsStream(path)) {
        final String schema = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        assertFalse(schema.matches("(?s).*\"\\$ref\"\\s*:\\s*\"https?://.*"), path);
      }
    }
  }

  @Test
  @DisplayName("a valid document passes and an invalid one is refused, from the bundled schema")
  void validatesFromBundledSchema() {
    final JsonSchemaValidator validator = new JsonSchemaValidator();
    validator.postConstruct();
    assertDoesNotThrow(() -> validator.validateAgainstCaptureJSONSchema(VALID));
    assertThrows(
        CaptureValidationException.class,
        () -> validator.validateAgainstCaptureJSONSchema(VALID.replace("\"OBSERVE\"", "\"LOOK\"")));
  }
}
