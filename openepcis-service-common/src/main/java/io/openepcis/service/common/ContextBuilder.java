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
package io.openepcis.service.common;

import io.openepcis.model.epcis.exception.QueryValidationException;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.collections4.MapUtils;
import org.json.JSONArray;
import org.json.JSONObject;

@ApplicationScoped
public class ContextBuilder {
  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ContextBuilder.class);

  public Map<String, Object> collectContextInfoIntoMap(final Object context, final Map<String, Object> contextAsMap) {
    if (context instanceof String) {
      buildContext((String) context, contextAsMap);
    }
    if (context instanceof JSONObject) {
      buildContext(context, contextAsMap);
    } else if (context instanceof JSONArray) {
      ((JSONArray) context).forEach(e -> {
        if (e instanceof JSONObject) {
          buildContext(e, contextAsMap);
        }
        if (e instanceof String) {
          buildContext((String) e, contextAsMap);
        }
      });
    }
    return contextAsMap;
  }

  private Map<String, Object> buildContext(final Object context, Map<String, Object> contextAsMap) {
    if (MapUtils.isNotEmpty(contextAsMap) && ((JSONObject) context).keySet().stream().anyMatch(contextAsMap::containsKey)) {
      throw new QueryValidationException("context has duplicate keys.");
    } else {
      contextAsMap.putAll(((JSONObject) context).toMap());
    }
    return contextAsMap;
  }

  public static Map<String, Object> addGs1ExtensionInContext(Optional<String> gs1Extensions, Map<String, Object> contextAsMap) {
    if (getGs1ExtensionAsMap(gs1Extensions).isPresent()) {
      contextAsMap.putAll(getGs1ExtensionAsMap(gs1Extensions).get());
    }
    return contextAsMap;
  }

  /**
   * Parse the value of the {@code GS1-Extensions} HTTP header. Three forms are
   * accepted, in order of precedence:
   * <ol>
   *   <li>{@code prefix=namespaceURI} pairs, comma-separated.
   *   <li>A JSON object fragment (e.g. {@code "acme":"https://example.com/epcis#"}).
   *   <li>A comma-separated list of prefix names (e.g. {@code acme,FIT}) —
   *       prefixes declared here contribute nothing to the JSON-LD context but
   *       signal which registered extensions the document is allowed to use.
   *       The registered namespace is resolved later during validation.
   * </ol>
   *
   * Previously, an unparseable value (anything not matching the first two
   * shapes) was fed to {@code new JSONObject("{" + value + "}")} which raised
   * a {@code JSONException}. That exception was swallowed inside the reactive
   * pipeline on the Kafka Streams thread, causing the capture job record to
   * stay in a null state and {@code GET /capture/{id}} to hang forever. Now
   * we fall back to the bare-prefix interpretation and log a warning on truly
   * malformed input so the capture always completes.
   */
  public static Optional<Map<String, Object>> getGs1ExtensionAsMap(Optional<String> gs1Extensions) {
    if (gs1Extensions.isEmpty()) return Optional.empty();
    final String raw = gs1Extensions.get().trim();
    if (raw.isEmpty()) return Optional.empty();
    if (raw.contains("=")) {
      final Map<String, Object> map = new HashMap<>();
      for (final String ext : raw.split(",")) {
        final String[] kv = ext.trim().split("=", 2);
        if (kv.length == 2 && !kv[0].isBlank()) {
          map.put(kv[0].trim(), kv[1].trim());
        }
      }
      return map.isEmpty() ? Optional.empty() : Optional.of(map);
    }
    if (raw.contains(":") && raw.contains("\"")) {
      try {
        return Optional.of(new JSONObject("{" + raw + "}").toMap());
      } catch (Exception e) {
        log.warn("GS1-Extensions header is not a valid JSON fragment: \'{}\' ({})", raw, e.getMessage());
      }
    }
    // Bare prefix list (e.g. "acme" or "acme,FIT"). Swallow silently
    // here — registered prefixes are resolved against the user extension
    // store in the validator; unknown prefixes are reported there.
    return Optional.empty();
  }

  public ContextBuilder() {
  }
}
