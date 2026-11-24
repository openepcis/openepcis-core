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
package io.openepcis.validation.rule;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Everything a rule may look at for one event. Rules read; they never change the event or the
 * capture metadata, which is why both are handed out as they are but must be treated read-only.
 *
 * @param event the event as captured (with its canonical hash in {@code openepcisExtension.hash}
 *     once the admission checks have run)
 * @param jsonLdContext the document's JSON-LD context, prefix to namespace
 * @param eventIndex the event's position in the capture document
 * @param documentEventIds every eventID in the capture document, for checks across the document
 * @param tenant the capturing tenant (defaultGroup), or {@code null} when unknown
 * @param captureId the capture job
 * @param parameters this rule's configured parameters ({@code openepcis.validation.rules.<id>.params})
 * @param claims claims of the capturing caller that rules may judge against, as the capture
 *     authorisation reads them from the capture metadata; empty in the Community Edition
 */
public record RuleContext(
    ObjectNode event,
    Map<String, Object> jsonLdContext,
    int eventIndex,
    List<String> documentEventIds,
    String tenant,
    String captureId,
    Map<String, String> parameters,
    Map<String, Object> claims) {

  public RuleContext {
    jsonLdContext = jsonLdContext == null ? Map.of() : jsonLdContext;
    documentEventIds = documentEventIds == null ? List.of() : documentEventIds;
    parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
    claims = claims == null ? Map.of() : Map.copyOf(claims);
  }

  /** The shape before claims reached rules; every existing call site keeps compiling. */
  public RuleContext(
      final ObjectNode event,
      final Map<String, Object> jsonLdContext,
      final int eventIndex,
      final List<String> documentEventIds,
      final String tenant,
      final String captureId,
      final Map<String, String> parameters) {
    this(event, jsonLdContext, eventIndex, documentEventIds, tenant, captureId, parameters, Map.of());
  }

  public RuleContext withParameters(final Map<String, String> ruleParameters) {
    return new RuleContext(
        event, jsonLdContext, eventIndex, documentEventIds, tenant, captureId, ruleParameters, claims);
  }

  /** A map-valued claim, or empty when absent or not a map. */
  public Optional<Map<String, Object>> claim(final String name) {
    final Object value = claims.get(name);
    if (value instanceof Map<?, ?> map) {
      @SuppressWarnings("unchecked")
      final Map<String, Object> typed = (Map<String, Object>) map;
      return Optional.of(typed);
    }
    return Optional.empty();
  }

  public Optional<String> parameter(final String name) {
    return Optional.ofNullable(parameters.get(name));
  }

  public boolean flag(final String name, final boolean fallback) {
    return parameter(name).map(Boolean::parseBoolean).orElse(fallback);
  }
}
