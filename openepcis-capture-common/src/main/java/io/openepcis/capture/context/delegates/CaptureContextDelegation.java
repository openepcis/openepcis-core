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
package io.openepcis.capture.context.delegates;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openepcis.capture.context.CaptureContext;
import io.openepcis.constants.EPCIS;
import io.openepcis.service.common.ContextBuilder;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;

@ApplicationScoped
public class CaptureContextDelegation {
  private final EpcisDocumentNodePersistenceDelegate epcisDocumentNodePersistenceDelegate;
  private final ErrorDeclarationNodeDelegate errorDeclarationNodeDelegate;
  private final EventNodeMessageDelegate eventNodeMessageDelegate;

  public BiConsumer<CaptureContext, ObjectNode> findDelegate(CaptureContext context, ObjectNode node) {
    if (EPCIS.EPCIS_DOCUMENT.equals(node.get(EPCIS.TYPE).asText()) || EPCIS.EPCIS_QUERY_DOCUMENT.equals(node.get(EPCIS.TYPE).asText())) {
      if (context.getGs1Extensions().get().isPresent()) {
        populateGS1ExtensionInContextField(context, node);
      }
      return epcisDocumentNodePersistenceDelegate;
    } else if (node.has(EPCIS.ERROR_DECLARATION)) return errorDeclarationNodeDelegate;
     else {
      if (node.has(EPCIS.EVENT_ID)) {
        context.getEventIDs().get().add(node.get(EPCIS.EVENT_ID).asText());
      }
      return eventNodeMessageDelegate;
    }
  }

  private void populateGS1ExtensionInContextField(CaptureContext context, ObjectNode node) {
    // Merge the declared GS1-Extensions namespaces (prefix -> IRI) INTO the event's @context
    // inline namespace object, in place. Deliberately does NOT remove/re-add @context: removing
    // then re-adding appends it AFTER epcisBody, producing an "early-eventList" document that
    // makes the streaming event-hash generator skip every event (empty hash -> capture fails
    // closed). It also never injects internal keys (e.g. contextUrls) into @context and never
    // assumes a fixed shape — robust to @context being a string, an array (with or without an
    // inline object), or absent. Preserving position keeps streaming O(1)-memory for any payload.
    final Optional<Map<String, Object>> extensions = ContextBuilder.getGs1ExtensionAsMap(context.getGs1Extensions().get());
    if (extensions.isEmpty() || extensions.get().isEmpty()) {
      return;
    }
    final JsonNode existing = node.get(EPCIS.CONTEXT);
    final ArrayNode contextArray;
    if (existing instanceof ArrayNode array) {
      contextArray = array; // mutate in place — @context keeps its original position
    } else {
      contextArray = new ObjectMapper().createArrayNode();
      if (existing != null && !existing.isNull()) {
        contextArray.add(existing);
      }
      node.set(EPCIS.CONTEXT, contextArray); // set() replaces the value at the SAME position
    }
    // Reuse the last inline object as the namespace container, or add one.
    ObjectNode namespaces = null;
    for (final JsonNode element : contextArray) {
      if (element.isObject()) {
        namespaces = (ObjectNode) element;
      }
    }
    if (namespaces == null) {
      namespaces = new ObjectMapper().createObjectNode();
      contextArray.add(namespaces);
    }
    for (final Map.Entry<String, Object> extension : extensions.get().entrySet()) {
      if (!namespaces.has(extension.getKey()) && extension.getValue() instanceof String iri) {
        namespaces.put(extension.getKey(), iri);
      }
    }
  }

  public CaptureContextDelegation(EpcisDocumentNodePersistenceDelegate epcisDocumentNodePersistenceDelegate, ErrorDeclarationNodeDelegate errorDeclarationNodeDelegate, EventNodeMessageDelegate eventNodeMessageDelegate) {
    this.epcisDocumentNodePersistenceDelegate = epcisDocumentNodePersistenceDelegate;
    this.errorDeclarationNodeDelegate = errorDeclarationNodeDelegate;
    this.eventNodeMessageDelegate = eventNodeMessageDelegate;
  }

  public EventNodeMessageDelegate getEventNodeMessageDelegate() {
    return this.eventNodeMessageDelegate;
  }
}
