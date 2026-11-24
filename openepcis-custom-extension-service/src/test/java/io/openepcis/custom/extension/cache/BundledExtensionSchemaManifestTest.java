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
package io.openepcis.custom.extension.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openepcis.service.util.JsonSchemaLoader;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The bundled manifest is read at startup and a broken entry is only logged, so a capture of the
 * affected namespace fails later with "No JSON schema found". This checks every entry up front.
 */
class BundledExtensionSchemaManifestTest {

  private static final String MANIFEST = "bundled-extension-schemas/manifest.json";
  private static final String CANONICAL_HOST = "https://ref.openepcis.org/";
  private static final String LEGACY_HOST = "https://ref.openepcis.io/";

  @Test
  void everyEntryLoadsAndEveryLegacyNamespaceHasItsCanonicalTwin() throws Exception {
    final ClassLoader cl = Thread.currentThread().getContextClassLoader();
    final JsonNode manifest;
    try (InputStream in = cl.getResourceAsStream(MANIFEST)) {
      assertNotNull(in, "missing " + MANIFEST);
      manifest = new ObjectMapper().readTree(in);
    }
    final Map<String, String> resourceByNamespace = new HashMap<>();
    for (final JsonNode entry : manifest) {
      final String namespace = entry.path("namespace").asText();
      final String resource = entry.path("resource").asText();
      assertTrue(namespace.startsWith(CANONICAL_HOST) || namespace.startsWith(LEGACY_HOST), namespace);
      assertTrue(entry.path("jsonldContextUrl").asText().startsWith(namespace), namespace);
      assertEquals(null, resourceByNamespace.put(namespace, resource), "duplicate namespace " + namespace);
      try (InputStream in = cl.getResourceAsStream(resource)) {
        assertNotNull(in, "missing bundled schema " + resource);
        assertNotNull(JsonSchemaLoader.loadSchema(in), resource);
      }
    }
    resourceByNamespace.forEach((namespace, resource) -> {
      if (namespace.startsWith(LEGACY_HOST)) {
        final String canonical = CANONICAL_HOST + namespace.substring(LEGACY_HOST.length());
        assertEquals(resource, resourceByNamespace.get(canonical), "legacy " + namespace + " without its .org twin");
      }
    });
    assertTrue(resourceByNamespace.containsKey(CANONICAL_HOST + "extensions/common/assessment/"));
    assertFalse(resourceByNamespace.containsKey(CANONICAL_HOST + "extensions/product/benetag/"));
    for (final String resource : new java.util.TreeSet<>(resourceByNamespace.values())) {
      try (InputStream in = cl.getResourceAsStream(resource)) {
        final String text = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        assertFalse(text.contains("x-access-level"), resource + " carries access-tier annotations");
      }
    }
  }
}
