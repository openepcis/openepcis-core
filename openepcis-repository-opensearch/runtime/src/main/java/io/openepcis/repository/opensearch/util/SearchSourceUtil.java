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
package io.openepcis.repository.opensearch.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch._types.SortOptions;
import org.opensearch.client.opensearch.core.SearchRequest;

public class SearchSourceUtil {
  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SearchSourceUtil.class);
  public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final JacksonJsonpMapper jsonpMapper = new JacksonJsonpMapper(OBJECT_MAPPER);
  private static final Set<String> IGNORE_SEARCH_REQUEST_BUILDER_FIELDS = Set.of("searchAfter");

  private SearchSourceUtil() {
  }

  public static final String serialize(final Object o) {
    final StringWriter writer = new StringWriter();
    final var jsonGenerator = jsonpMapper.jsonProvider().createGenerator(writer);
    jsonpMapper.serialize(o, jsonGenerator);
    jsonGenerator.close();
    return writer.toString();
  }

  public static final <T> T deserialize(final String json, final Class<T> type) {
    final var jsonParser = jsonpMapper.jsonProvider().createParser(new StringReader(json));
    return jsonpMapper.deserialize(jsonParser, type);
  }

  public static final JsonNode toJsonNode(Object o) {
    try {
      return OBJECT_MAPPER.readTree(serialize(o));
    } catch (Throwable throwable) {
      throw new UnsupportedOperationException(throwable.getMessage(), throwable);
    }
  }

  public static final String toString(final SearchRequest searchRequest) {
    return serialize(searchRequest);
  }

  public static final SearchRequest searchRequest(final String json) {
    try {
      final ObjectNode node = (ObjectNode) OBJECT_MAPPER.readTree(json);
      // need to work around broken sort deserialization in Opensearch
      final ArrayNode sort = (ArrayNode) node.remove("sort");
      final var jsonParser = jsonpMapper.jsonProvider().createParser(new StringReader(OBJECT_MAPPER.writeValueAsString(node)));
      final SearchRequest searchRequest = jsonpMapper.deserialize(jsonParser, SearchRequest.class);
      if (sort != null) {
        final List<SortOptions> sortOptionsList = new ArrayList<>();
        Iterator<JsonNode> sortElements = sort.elements();
        while (sortElements.hasNext()) {
          JsonNode sortElement = sortElements.next();
          final SortOptions sortOptions = deserialize(OBJECT_MAPPER.writeValueAsString(sortElement), SortOptions.class);
          sortOptionsList.add(sortOptions);
        }
        FieldUtils.writeField(searchRequest, "sort", sortOptionsList, true);
      }
      return searchRequest;
    } catch (Throwable throwable) {
      throw new UnsupportedOperationException(throwable.getMessage(), throwable);
    }
  }

  public static final SearchRequest.Builder searchRequestBuilder(final SearchRequest searchRequest) {
    return searchRequestBuilder(searchRequest, true);
  }

  public static final SearchRequest.Builder searchRequestBuilder(final SearchRequest searchRequest, final boolean preserveAllFields) {
    final SearchRequest.Builder searchRequestBuilder = new SearchRequest.Builder();
    final List<Field> fields = FieldUtils.getAllFieldsList(SearchRequest.Builder.class);
    for (Field writeField : fields) {
      if (!preserveAllFields && IGNORE_SEARCH_REQUEST_BUILDER_FIELDS.contains(writeField.getName())) {
        continue;
      }
      try {
        final Field readField = FieldUtils.getField(SearchRequest.class, writeField.getName(), true);
        if (readField != null) {
          final Object value = FieldUtils.readField(readField, searchRequest, true);
          if (value != null) {
            FieldUtils.writeField(writeField, searchRequestBuilder, value, true);
          }
        }
      } catch (Exception e) {
        log.warn(e.getMessage(), e);
      }
    }
    return searchRequestBuilder;
  }
}
