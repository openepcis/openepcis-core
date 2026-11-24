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
package io.openepcis.capture.context;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openepcis.constants.EPCIS;
import org.apache.commons.lang3.tuple.Pair;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;

import static io.openepcis.model.epcis.constants.CommonConstants.CONTEXT_URLS;

/**
 * This utilities contains operations to extract or enrich {@link ObjectNode} of EPCIS event or
 * EPCIS document.
 */
public class EPCISEventObjectNodeUtil {

    private EPCISEventObjectNodeUtil() {
        throw new UnsupportedOperationException("Invalid invocation of constructor");
    }

    /**
     * Wraps EPCIS event {@link ObjectNode} into a given EPCIS document {@link ObjectNode}
     *
     * @param event        EPCIS event
     * @param documentNode EPCIS document
     * @return EPCIS document as {@link ObjectNode}
     */
    public static ObjectNode wrapEventIntoEPCISDocument(ObjectNode event, ObjectNode documentNode) {
        if (EPCIS.EPCIS_QUERY_DOCUMENT.equals(documentNode.get(EPCIS.TYPE).asText())) {
            JsonNode queryName = documentNode.remove(EPCIS.QUERY_NAME);
            documentNode
                    .putObject(EPCIS.EPCIS_BODY_IN_CAMEL_CASE)
                    .putObject(EPCIS.QUERY_RESULTS_IN_CAMEL_CASE)
                    .putObject(EPCIS.RESULTS_BODY_IN_CAMEL_CASE)
                    .putArray(EPCIS.EVENT_LIST_IN_CAMEL_CASE)
                    .add(event);
            ObjectNode queryResultsNode =
                    ((ObjectNode)
                            documentNode
                                    .get(EPCIS.EPCIS_BODY_IN_CAMEL_CASE)
                                    .get(EPCIS.QUERY_RESULTS_IN_CAMEL_CASE));
            queryResultsNode.putIfAbsent(EPCIS.QUERY_NAME, queryName);
            if (documentNode.has(EPCIS.SUBSCRIPTION_ID)) {
                JsonNode subscriptionID = documentNode.remove(EPCIS.SUBSCRIPTION_ID);
                queryResultsNode.putIfAbsent(EPCIS.SUBSCRIPTION_ID, subscriptionID);
            }
        } else {
            documentNode
                    .putObject(EPCIS.EPCIS_BODY_IN_CAMEL_CASE) //
                    .putArray(EPCIS.EVENT_LIST_IN_CAMEL_CASE) //
                    .add(event);
        }
        return documentNode;
    }

    /**
     * Prepares a pair of event and JSON-LD context map out of EPCISDocument {@link ObjectNode}
     *
     * @param epcisDocument EPCIS Document from which JSON-LD context to be extracted
     * @param objectMapper  ObjectMapper for parsing JSON-LD context from epcisDocument
     * @return a pair of EPCIS event and JSON-LD context map
     */
    public static Pair<ObjectNode, Map<String, Object>> extractEventAndContextNodeFromDocument(
            final ObjectNode epcisDocument, ObjectMapper objectMapper) {
        final Map<String, Object> contextAsMap =
                collectContextInfoIntoMap(epcisDocument.get(EPCIS.CONTEXT), objectMapper);
        final JsonNode array =
                EPCIS.EPCIS_QUERY_DOCUMENT.equals(epcisDocument.get(EPCIS.TYPE).asText())
                        ? epcisDocument
                        .get(EPCIS.EPCIS_BODY_IN_CAMEL_CASE)
                        .get(EPCIS.QUERY_RESULTS_IN_CAMEL_CASE)
                        .get(EPCIS.RESULTS_BODY_IN_CAMEL_CASE)
                        .get(EPCIS.EVENT_LIST_IN_CAMEL_CASE)
                        : epcisDocument.get(EPCIS.EPCIS_BODY_IN_CAMEL_CASE).get(EPCIS.EVENT_LIST_IN_CAMEL_CASE);
        final ObjectNode eventNode = (ObjectNode) array.get(0);
        return Pair.of(eventNode, contextAsMap);
    }

    /**
     * Flattens JSON-LD @context into a Map<String, Object>
     */
    public static Map<String, Object> collectContextInfoIntoMap(Object ctx, ObjectMapper om) {
        return switch (ctx) {
            case null -> new HashMap<>();

            // String → treat as context URL
            case String s -> mapWithUrl(s);

            // org.json JSONObject
            case JSONObject json -> convertToMap(json.toMap());

            // org.json JSONArray
            case JSONArray arr -> processArrayElements(arr.iterator(), om);

            // Jackson ObjectNode
            case ObjectNode jsonNodes -> om.convertValue(ctx, new TypeReference<Map<String, Object>>() {
            });

            // Jackson ArrayNode
            case ArrayNode arr -> processArrayElements(arr.iterator(), om);
            default ->
                // Unknown type → return empty
                    new HashMap<>();
        };

    }

    private static Map<String, Object> mapWithUrl(String url) {
        Map<String, Object> map = new HashMap<>();
        map.put(CONTEXT_URLS, url);
        return map;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> convertToMap(Map<?, ?> raw) {
        Map<String, Object> result = new HashMap<>();
        raw.forEach((k, v) -> result.put(String.valueOf(k), v));
        return result;
    }

    private static Map<String, Object> processArrayElements(Iterator<?> iterator, ObjectMapper om) {
        Map<String, Object> result = new HashMap<>();
        List<String> urls = new ArrayList<>();

        while (iterator.hasNext()) {
            Object element = iterator.next();

            if (element instanceof JSONObject json) {
                result.putAll(convertToMap(json.toMap()));
            }
            else if (element instanceof JsonNode node) {
                // If it's a JSON text node, treat as a context URL string
                if (node.isTextual()) {
                    urls.add(node.asText());
                } else {
                    // For ObjectNode or other structured nodes, convert to map
                    result.putAll(om.convertValue(node, new TypeReference<Map<String, Object>>() {}));
                }
            }
            else if (element instanceof String s) {
                urls.add(s);
            }
        }

        if (!urls.isEmpty()) {
            result.put(CONTEXT_URLS, urls);
        }

        return result;
    }


}
