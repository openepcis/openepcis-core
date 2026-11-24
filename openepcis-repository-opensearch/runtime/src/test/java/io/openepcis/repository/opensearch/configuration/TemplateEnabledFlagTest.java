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
package io.openepcis.repository.opensearch.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.opensearch.client.opensearch._types.mapping.Property;

/**
 * The event template keeps userExtensions.objectValue out of the mapping ("enabled": false). The
 * parser dropped the flag, so every key below objectValue was mapped dynamically, and a document
 * with 15 and 1.5 under one key failed in a fresh index.
 */
class TemplateEnabledFlagTest {

  @Test
  void objectValueStaysUnmapped() throws Exception {
    final ObjectMapper mapper = new ObjectMapper();
    final JsonNode template;
    try (InputStream in = getClass().getResourceAsStream("/templates/opensearch-indices/epcis-event-template.json")) {
      template = mapper.readTree(in);
    }
    final Map<String, Property> properties = new HashMap<>();
    new OpenSearchInitializer(null, mapper, null).parseProperties(template.path("mappings").path("properties"), properties);

    final Property userExtensions = properties.get("userExtensions");
    final Property objectValue = userExtensions.nested().properties().get("objectValue");
    assertEquals(Boolean.FALSE, objectValue.object().enabled());

    final Property sensorReport = properties.get("sensorElementList").nested().properties().get("sensorReport");
    final Property reportObjectValue =
        sensorReport.nested().properties().get("userExtensions").nested().properties().get("objectValue");
    assertEquals(Boolean.FALSE, reportObjectValue.object().enabled());

    assertNull(properties.get("metadata").nested().properties().get("capturedBy").object().enabled(), "an object without the flag stays as it was");
  }
}
