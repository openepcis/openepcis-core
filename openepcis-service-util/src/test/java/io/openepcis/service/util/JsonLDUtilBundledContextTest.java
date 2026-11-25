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
package io.openepcis.service.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.apicatalog.jsonld.loader.DocumentLoaderOptions;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The GS1 EPCIS context comes from the jar, under both URLs a document may use, and once. */
class JsonLDUtilBundledContextTest {

  @Test
  @DisplayName("the GS1 context is served from the jar under its current and its 2.0.0 URL")
  void gs1ContextIsBundled() throws Exception {
    final JsonLDUtil util = new JsonLDUtil();
    for (final String url :
        new String[] {
          "https://ref.gs1.org/standards/epcis/epcis-context.jsonld",
          "https://ref.gs1.org/standards/epcis/2.0.1/epcis-context.jsonld",
          "https://ref.gs1.org/standards/epcis/2.0.0/epcis-context.jsonld"
        }) {
      final byte[] first = util.loadDocumentBytes(URI.create(url), new DocumentLoaderOptions());
      final String context = new String(first, StandardCharsets.UTF_8);
      assertTrue(context.contains("\"@context\""), url);
      assertTrue(context.contains("ObjectEvent"), url);
      // the second call is served from memory, not loaded again
      assertSame(first, util.loadDocumentBytes(URI.create(url), new DocumentLoaderOptions()), url);
    }
  }

  @Test
  @DisplayName("each GS1 context URL is served in its own version, with its own gs1 prefix")
  void gs1PrefixFollowsTheVersion() throws Exception {
    final JsonLDUtil util = new JsonLDUtil();
    assertEquals("https://ref.gs1.org/voc/", gs1Prefix(util, "https://ref.gs1.org/standards/epcis/epcis-context.jsonld"));
    assertEquals("https://ref.gs1.org/voc/", gs1Prefix(util, "https://ref.gs1.org/standards/epcis/2.0.1/epcis-context.jsonld"));
    assertEquals("https://gs1.org/voc/", gs1Prefix(util, "https://ref.gs1.org/standards/epcis/2.0.0/epcis-context.jsonld"));
  }

  @Test
  @DisplayName("an extension context built on the current GS1 context expands without a protected-term clash")
  void extensionContextOnTheCurrentGs1Context() throws Exception {
    final JsonLDUtil util = new JsonLDUtil();
    final Field executor = JsonLDUtil.class.getDeclaredField("managedExecutor");
    executor.setAccessible(true);
    executor.set(util, ManagedExecutor.builder().build());
    // what the contexts of ref.openepcis.org do: repeat the gs1 prefix of the current GS1 context
    final String document =
        "{\"@context\":[\"https://ref.gs1.org/standards/epcis/epcis-context.jsonld\","
            + "{\"gs1\":\"https://ref.gs1.org/voc/\",\"eubat\":\"https://ref.openepcis.org/extensions/eu/battery/\"}],"
            + "\"type\":\"EPCISDocument\",\"schemaVersion\":\"2.0\",\"creationDate\":\"2026-11-25T00:00:00.000Z\","
            + "\"epcisBody\":{\"eventList\":[{\"type\":\"ObjectEvent\",\"eventTime\":\"2026-11-25T00:00:00.000Z\","
            + "\"eventTimeZoneOffset\":\"+00:00\",\"epcList\":[\"https://id.example.com/01/09521234000013/21/1\"],"
            + "\"action\":\"ADD\",\"eubat:stateOfHealth\":97}]}}";
    final JsonObject expanded = util.expandJsonLDDocument(document).await().atMost(Duration.ofSeconds(30));
    // a failed expansion degrades to an empty object, so the extension term must be there
    assertTrue(expanded.toString().contains("https://ref.openepcis.org/extensions/eu/battery/stateOfHealth"), expanded.toString());
  }

  private static String gs1Prefix(final JsonLDUtil util, final String url) throws Exception {
    final byte[] context = util.loadDocumentBytes(URI.create(url), new DocumentLoaderOptions());
    return Json.createReader(new ByteArrayInputStream(context)).readObject().getJsonObject("@context").getString("gs1");
  }

  @Test
  @DisplayName("every extension context of the manifest is on the classpath, benetag is not")
  void extensionContextsAreBundled() throws Exception {
    final JsonLDUtil util = new JsonLDUtil();
    final jakarta.json.JsonArray manifest;
    try (java.io.InputStream in = JsonLDUtil.class.getClassLoader().getResourceAsStream(JsonLDUtil.BUNDLED_CONTEXTS_MANIFEST)) {
      manifest = Json.createReader(in).readArray();
    }
    assertTrue(manifest.size() > 10, "manifest lists the extension contexts");
    for (final jakarta.json.JsonValue entry : manifest) {
      final String url = entry.asJsonObject().getString("url");
      assertTrue(!url.contains("benetag"), url);
      final byte[] context = util.loadDocumentBytes(URI.create(url), new DocumentLoaderOptions());
      assertTrue(Json.createReader(new ByteArrayInputStream(context)).readObject().containsKey("@context"), url);
    }
  }

  @Test
  @org.junit.jupiter.api.Disabled(
      "dpp-core-context scopes gs1-shortcuts-context under gs1:masterDataAvailableFor, which redefines"
          + " epcis, masterData and certificationInfo of the protected GS1 context. JSON-LD 1.1 allows a"
          + " property-scoped context to do that and jsonld.js accepts it, Titanium (1.4.1 to 1.7.0) does not"
          + " pass override-protected on to a remote scoped context. Enable once the contexts avoid the clash.")
  @DisplayName("a battery event with the contexts of ref.openepcis.org expands from the jar")
  void batteryEventWithRefContexts() throws Exception {
    final JsonLDUtil util = new JsonLDUtil();
    final Field executor = JsonLDUtil.class.getDeclaredField("managedExecutor");
    executor.setAccessible(true);
    executor.set(util, ManagedExecutor.builder().build());
    final String document =
        "{\"@context\":[\"https://ref.gs1.org/standards/epcis/epcis-context.jsonld\","
            + "\"https://ref.openepcis.org/extensions/common/core/dpp-core-context.jsonld\","
            + "\"https://ref.openepcis.org/extensions/eu/battery/battery-context.jsonld\"],"
            + "\"type\":\"EPCISDocument\",\"schemaVersion\":\"2.0\",\"creationDate\":\"2026-11-25T00:00:00.000Z\","
            + "\"epcisBody\":{\"eventList\":[{\"type\":\"ObjectEvent\",\"eventTime\":\"2026-11-25T00:00:00.000Z\","
            + "\"eventTimeZoneOffset\":\"+00:00\",\"epcList\":[\"https://id.example.com/01/09521234000013/21/1\"],"
            + "\"action\":\"ADD\",\"eubat:stateOfHealth\":97}]}}";
    final JsonObject expanded = util.expandJsonLDDocument(document).await().atMost(Duration.ofSeconds(30));
    assertTrue(expanded.toString().contains("https://ref.openepcis.org/extensions/eu/battery/stateOfHealth"), expanded.toString());
  }
}
