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

import com.apicatalog.jsonld.JsonLd;
import com.apicatalog.jsonld.JsonLdError;
import com.apicatalog.jsonld.JsonLdErrorCode;
import com.apicatalog.jsonld.document.Document;
import com.apicatalog.jsonld.document.JsonDocument;
import com.apicatalog.jsonld.loader.DocumentLoader;
import com.apicatalog.jsonld.loader.DocumentLoaderOptions;
import com.apicatalog.jsonld.loader.SchemeRouter;
import io.openepcis.constants.EPCISVersion;
import io.openepcis.model.epcis.exception.CaptureValidationException;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.JsonObject;
import org.eclipse.microprofile.context.ManagedExecutor;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import static io.openepcis.model.epcis.exception.ExceptionMessages.ERROR_WHILE_VALIDATING_JSONLD_USER_EXTENSION_FIELDS;

@ApplicationScoped
public class JsonLDUtil {
  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(JsonLDUtil.class);
  /**
   * The GS1 EPCIS 2.0 context under every URL a document may name it by, served from the jar. A
   * document that names only these needs no network to be expanded.
   */
  private static final Map<String, String> jsonLDSchemaMap =
      Map.of(
          EPCISVersion.getDefaultJSONContext(), "/epcis-context-2.0.0.jsonld",
          "https://ref.gs1.org/standards/epcis/2.0.0/epcis-context.jsonld", "/epcis-context-2.0.0.jsonld");

  /** Bounds the in-memory copy of remote contexts; a context is small and documents name few. */
  private static final int MAX_CACHED_CONTEXTS = 256;

  /**
   * Loaded contexts by URL, as bytes. A cache interceptor cannot serve here: the loader calls this
   * class itself, which bypasses the proxy, so every expansion fetched every remote context again.
   */
  private final java.util.concurrent.ConcurrentMap<String, byte[]> contextCache =
      new java.util.concurrent.ConcurrentHashMap<>();
  // The document loader hands the expander a FRESH Document each call.
  // Titanium's expander mutates internal state on the Document it's handed
  // during expansion (ActiveContextBuilder / TermDefinitionBuilder), so
  // reusing a parsed Document across expansions poisons the cache and
  // subsequent calls fail with INVALID_TERM_DEFINITION — even when calls
  // are serialised.
  //
  // The cache holds raw bytes (per URI) so the remote fetch still happens
  // only once; the JsonDocument.of(...) parse is cheap and thread-safe.
  private final DocumentLoader documentLoader = (uri, documentLoaderOptions) -> {
    byte[] bytes = this.loadDocumentBytes(uri, documentLoaderOptions);
    try (InputStream in = new ByteArrayInputStream(bytes)) {
      return JsonDocument.of(in);
    } catch (java.io.IOException e) {
      throw new JsonLdError(JsonLdErrorCode.LOADING_DOCUMENT_FAILED, e);
    }
  };
  @Inject
  ManagedExecutor managedExecutor;

  public final byte[] loadDocumentBytes(final URI uri, final DocumentLoaderOptions documentLoaderOptions) throws JsonLdError {
    final String key = uri.toString();
    final byte[] cached = contextCache.get(key);
    if (cached != null) {
      return cached;
    }
    final byte[] loaded = fetchDocumentBytes(uri, documentLoaderOptions);
    if (contextCache.size() < MAX_CACHED_CONTEXTS) {
      contextCache.putIfAbsent(key, loaded);
    }
    return loaded;
  }

  private byte[] fetchDocumentBytes(final URI uri, final DocumentLoaderOptions documentLoaderOptions) throws JsonLdError {
    try {
      // the map is keyed by the URL as text; a lookup by URI never matched, so the bundled
      // context was never used and the GS1 one was fetched from ref.gs1.org
      Document loaded = jsonLDSchemaMap.containsKey(uri.toString()) ? SchemeRouter.defaultInstance().loadDocument(JsonLDUtil.class.getResource(jsonLDSchemaMap.get(uri.toString())).toURI(), documentLoaderOptions) : SchemeRouter.defaultInstance().loadDocument(uri, documentLoaderOptions);
      // Serialise the parsed JSON-LD Document back to bytes so the cache
      // stores an immutable representation that can be re-parsed on every
      // call, isolating each expansion from Titanium's in-place mutation.
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      try (jakarta.json.JsonWriter w = jakarta.json.Json.createWriter(out)) {
        w.write(loaded.getJsonContent().orElseThrow(() -> new JsonLdError(JsonLdErrorCode.LOADING_DOCUMENT_FAILED)));
      }
      return out.toByteArray();
    } catch (URISyntaxException | RuntimeException e) {
      throw new JsonLdError(JsonLdErrorCode.UNSPECIFIED);
    }
  }

  // Belt-and-braces thread-safety: Titanium's expander has mutable internal
  // state in multiple places (ActiveContextBuilder, TermDefinitionBuilder,
  // and IRI-resolution caches) that all need to be isolated between
  // concurrent expansions. Fresh-parsed Documents per call solve the cache
  // poisoning problem; the static lock serialises expand() so the library's
  // internal non-Document state can't race either. At demo/capture scale
  // (sub-ms per expansion) this has no measurable throughput impact.
  private static final Object EXPAND_LOCK = new Object();

  public Uni<JsonObject> expandJsonLDDocument(final String captureString) {
    return Uni.createFrom().<JsonObject>emitter(em -> {
      managedExecutor.runAsync(() -> {
        try {
          final JsonDocument jsonDocument = JsonDocument.of(new StringReader(captureString));
          final JsonObject result;
          synchronized (EXPAND_LOCK) {
            result = JsonLd.expand(jsonDocument).loader(documentLoader).get().getJsonObject(0);
          }
          em.complete(result);
        } catch (JsonLdError e) {
          // Expansion failures must NOT abort persistence. They only
          // power user-extension extraction; if expansion fails the
          // event still needs to land in OpenSearch — downstream
          // setUserExtensionsForAll() simply walks an empty extracted
          // event and produces no extension fields. Log + degrade.
          log.warn("JSON-LD expansion failed (event will be persisted without" + " extracted user extensions): {}", e.getMessage());
          em.complete(jakarta.json.Json.createObjectBuilder().build());
        } catch (Exception e) {
          log.error(ERROR_WHILE_VALIDATING_JSONLD_USER_EXTENSION_FIELDS, e);
          em.fail(new CaptureValidationException(ERROR_WHILE_VALIDATING_JSONLD_USER_EXTENSION_FIELDS, e));
        }
      });
    });
  }
}
