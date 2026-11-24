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

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.apicatalog.jsonld.loader.DocumentLoaderOptions;
import java.net.URI;
import java.nio.charset.StandardCharsets;
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
}
