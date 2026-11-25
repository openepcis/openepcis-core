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
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import static io.openepcis.model.epcis.exception.ExceptionMessages.ERROR_WHILE_VALIDATING_JSONLD_USER_EXTENSION_FIELDS;

@ApplicationScoped
public class JsonLDUtil {
  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(JsonLDUtil.class);
  /**
   * The GS1 EPCIS 2.0 contexts under every URL a document may name them by, served from the jar. A
   * document that names only these needs no network to be expanded. Each URL gets its own version:
   * 2.0.1, which the unversioned URL serves, maps the prefix gs1 to https://ref.gs1.org/voc/ as the
   * rest of the code expects, 2.0.0 to https://gs1.org/voc/. Both are @protected, so a context
   * built on the unversioned URL (those of ref.openepcis.org are) clashes with the 2.0.0 copy.
   */
  private static final Map<String, String> GS1_CONTEXTS =
      Map.of(
          EPCISVersion.getDefaultJSONContext(), "/epcis-context-2.0.1.jsonld",
          "https://ref.gs1.org/standards/epcis/2.0.1/epcis-context.jsonld", "/epcis-context-2.0.1.jsonld",
          "https://ref.gs1.org/standards/epcis/2.0.0/epcis-context.jsonld", "/epcis-context-2.0.0.jsonld");

  /** Where a jar lists further contexts it bundles, as [{"url": …, "resource": …}]. */
  static final String BUNDLED_CONTEXTS_MANIFEST = "bundled-jsonld-contexts/manifest.json";

  /**
   * Every context served from the classpath: the GS1 ones, then those of each
   * {@value #BUNDLED_CONTEXTS_MANIFEST} on the classpath (the extension contexts of
   * ref.openepcis.org in this jar; an edition can add its own). The first entry for a URL wins.
   */
  private static final Map<String, String> jsonLDSchemaMap = bundledContexts();

  private static Map<String, String> bundledContexts() {
    final Map<String, String> contexts = new java.util.LinkedHashMap<>(GS1_CONTEXTS);
    try {
      final java.util.Enumeration<java.net.URL> manifests =
          JsonLDUtil.class.getClassLoader().getResources(BUNDLED_CONTEXTS_MANIFEST);
      while (manifests.hasMoreElements()) {
        final java.net.URL manifest = manifests.nextElement();
        try (InputStream in = manifest.openStream();
            jakarta.json.JsonReader reader = jakarta.json.Json.createReader(in)) {
          for (final jakarta.json.JsonValue entry : reader.readArray()) {
            final JsonObject context = entry.asJsonObject();
            contexts.putIfAbsent(context.getString("url"), "/" + context.getString("resource"));
          }
        } catch (java.io.IOException | RuntimeException e) {
          log.warn("Bundled JSON-LD context manifest {} not readable, its contexts are loaded from the network: {}", manifest, e.toString());
        }
      }
    } catch (java.io.IOException e) {
      log.warn("Bundled JSON-LD context manifests not found: {}", e.toString());
    }
    return Map.copyOf(contexts);
  }

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
    final byte[] loaded;
    try {
      loaded = fetchDocumentBytes(uri, documentLoaderOptions);
    } catch (JsonLdError e) {
      // Titanium replaces this error by one without a cause, so this is the only place it is seen
      log.warn("Loading the JSON-LD context {} failed: {}", uri, rootCauseMessage(e), e);
      throw e;
    }
    if (contextCache.size() < MAX_CACHED_CONTEXTS) {
      contextCache.putIfAbsent(key, loaded);
    }
    return loaded;
  }

  private byte[] fetchDocumentBytes(final URI uri, final DocumentLoaderOptions documentLoaderOptions) throws JsonLdError {
    // the map is keyed by the URL as text; a lookup by URI never matched, so the bundled
    // context was never used and the GS1 one was fetched from ref.gs1.org
    final String bundled = jsonLDSchemaMap.get(uri.toString());
    if (bundled != null) {
      // read as a stream: inside the application jar the resource has a jar: URL, which the
      // scheme router (http, https, file) cannot load
      try (InputStream in = JsonLDUtil.class.getResourceAsStream(bundled)) {
        if (in == null) {
          throw new JsonLdError(JsonLdErrorCode.LOADING_DOCUMENT_FAILED, "bundled context missing: " + bundled);
        }
        return in.readAllBytes();
      } catch (java.io.IOException e) {
        throw new JsonLdError(JsonLdErrorCode.LOADING_DOCUMENT_FAILED, e);
      }
    }
    try {
      Document loaded = SchemeRouter.defaultInstance().loadDocument(uri, documentLoaderOptions);
      // Serialise the parsed JSON-LD Document back to bytes so the cache
      // stores an immutable representation that can be re-parsed on every
      // call, isolating each expansion from Titanium's in-place mutation.
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      try (jakarta.json.JsonWriter w = jakarta.json.Json.createWriter(out)) {
        w.write(loaded.getJsonContent().orElseThrow(() -> new JsonLdError(JsonLdErrorCode.LOADING_DOCUMENT_FAILED)));
      }
      return out.toByteArray();
    } catch (RuntimeException e) {
      throw new JsonLdError(JsonLdErrorCode.LOADING_DOCUMENT_FAILED, e);
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

  /** The message chain down to the root cause: Titanium wraps every loader failure alike. */
  private static String rootCauseMessage(final Throwable error) {
    final StringBuilder message = new StringBuilder(String.valueOf(error.getMessage()));
    for (Throwable cause = error.getCause(); cause != null && cause != cause.getCause(); cause = cause.getCause()) {
      message.append(" <- ").append(cause);
    }
    return message.toString();
  }

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
          log.warn("JSON-LD expansion failed (event will be persisted without" + " extracted user extensions): {}", rootCauseMessage(e));
          em.complete(jakarta.json.Json.createObjectBuilder().build());
        } catch (Exception e) {
          log.error(ERROR_WHILE_VALIDATING_JSONLD_USER_EXTENSION_FIELDS, e);
          em.fail(new CaptureValidationException(ERROR_WHILE_VALIDATING_JSONLD_USER_EXTENSION_FIELDS, e));
        }
      });
    });
  }
}
