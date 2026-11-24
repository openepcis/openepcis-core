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
package io.openepcis.query.builder;

import io.openepcis.query.util.Constants;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class QueryBuilder {
  private final Map<String, Object> query;

  public QueryBuilder() {
    this.query = new HashMap<>();
  }

  public QueryBuilder addTermQuery(String field, Object value) {
    Map<String, Object> term = new HashMap<>();
    term.put(field, value);
    query.put(Constants.TERM, term);
    return this;
  }

  public QueryBuilder addTermsQuery(String field, Object values) {
    Map<String, Object> terms = new HashMap<>();
    terms.put(field, values);
    query.put(Constants.TERMS, terms);
    return this;
  }

  public QueryBuilder addMatchAllQuery() {
    query.put(Constants.MATCH_ALL, new HashMap<>());
    return this;
  }

  public QueryBuilder addBoolQuery() {
    query.put(Constants.BOOL, new HashMap<>());
    return this;
  }

  public QueryBuilder addExistsQuery(Object value) {
    Map<String, Object> field = new HashMap<>();
    field.put(Constants.FIELD, value);
    query.put(Constants.EXITS, field);
    return this;
  }

  public QueryBuilder addNestedQuery(String path, QueryBuilder nestedQuery) {
    Map<String, Object> nested = new HashMap<>();
    nested.put(Constants.PATH, path);
    nested.put(Constants.QUERY, nestedQuery.build());
    query.put(Constants.NESTED, nested);
    return this;
  }

  public QueryBuilder addWildcardQuery(String key, String value) {
    Map<String, Object> valueMap = new HashMap<>();
    valueMap.put(Constants.VALUE, value);
    valueMap.put(Constants.BOOST, 1);
    Map<String, Map<String, Object>> wildcard = new HashMap<>();
    wildcard.put(key, valueMap);
    query.put(Constants.WILDCARD, wildcard);
    return this;
  }

  public QueryBuilder addRange(String field) {
    Map<String, Object> range = new HashMap<>();
    range.put(field, new HashMap<String, Object>());
    query.put(Constants.RANGE, range);
    return this;
  }

  public QueryBuilder addGT(String field, Object value) {
    Map<String, Object> range = (Map<String, Object>) query.get(Constants.RANGE);
    if (range == null) {
      range = new HashMap<>();
      query.put(Constants.RANGE, range);
    }
    Map<String, Object> fieldMap = (Map<String, Object>) range.get(field);
    if (fieldMap == null) {
      fieldMap = new HashMap<>();
      range.put(field, fieldMap);
    }
    fieldMap.put(Constants.GT, value);
    return this;
  }

  public QueryBuilder addGTE(String field, Object value) {
    Map<String, Object> range = (Map<String, Object>) query.get(Constants.RANGE);
    if (range == null) {
      range = new HashMap<>();
      query.put(Constants.RANGE, range);
    }
    Map<String, Object> fieldMap = (Map<String, Object>) range.get(field);
    if (fieldMap == null) {
      fieldMap = new HashMap<>();
      range.put(field, fieldMap);
    }
    fieldMap.put(Constants.GTE, value);
    return this;
  }

  public QueryBuilder addLT(String field, Object value) {
    Map<String, Object> range = (Map<String, Object>) query.get(Constants.RANGE);
    if (range == null) {
      range = new HashMap<>();
      query.put(Constants.RANGE, range);
    }
    Map<String, Object> fieldMap = (Map<String, Object>) range.get(field);
    if (fieldMap == null) {
      fieldMap = new HashMap<>();
      range.put(field, fieldMap);
    }
    fieldMap.put(Constants.LT, value);
    return this;
  }

  public QueryBuilder addLTE(String field, Object value) {
    Map<String, Object> range = (Map<String, Object>) query.get(Constants.RANGE);
    if (range == null) {
      range = new HashMap<>();
      query.put(Constants.RANGE, range);
    }
    Map<String, Object> fieldMap = (Map<String, Object>) range.get(field);
    if (fieldMap == null) {
      fieldMap = new HashMap<>();
      range.put(field, fieldMap);
    }
    fieldMap.put(Constants.LTE, value);
    return this;
  }

  public QueryBuilder addFilterClause(QueryBuilder subQuery) {
    Map<String, Object> bool = (Map<String, Object>) query.get(Constants.BOOL);
    if (bool == null) {
      bool = new HashMap<>();
      query.put(Constants.BOOL, bool);
    }
    List<Map<String, Object>> filterList = (List<Map<String, Object>>) bool.get(Constants.FILTER);
    if (filterList == null) {
      filterList = new ArrayList<>();
      bool.put(Constants.FILTER, filterList);
    }
    filterList.add(subQuery.build());
    return this;
  }

  public QueryBuilder addMustClause(QueryBuilder subQuery) {
    Map<String, Object> bool = (Map<String, Object>) query.get(Constants.BOOL);
    if (bool == null) {
      bool = new HashMap<>();
      query.put(Constants.BOOL, bool);
    }
    List<Map<String, Object>> mustList = (List<Map<String, Object>>) bool.get(Constants.MUST);
    if (mustList == null) {
      mustList = new ArrayList<>();
      bool.put(Constants.MUST, mustList);
    }
    mustList.add(subQuery.build());
    return this;
  }

  public QueryBuilder addShouldClause(QueryBuilder subQuery) {
    Map<String, Object> bool = (Map<String, Object>) query.get(Constants.BOOL);
    if (bool == null) {
      bool = new HashMap<>();
      query.put(Constants.BOOL, bool);
    }
    List<Map<String, Object>> shouldList = (List<Map<String, Object>>) bool.get(Constants.SHOULD);
    if (shouldList == null) {
      shouldList = new ArrayList<>();
      bool.put(Constants.SHOULD, shouldList);
    }
    shouldList.add(subQuery.build());
    return this;
  }

  public QueryBuilder addMustNotClause(QueryBuilder subQuery) {
    Map<String, Object> bool = (Map<String, Object>) query.get(Constants.BOOL);
    if (bool == null) {
      bool = new HashMap<>();
      query.put(Constants.BOOL, bool);
    }
    List<Map<String, Object>> mustNotList =
        (List<Map<String, Object>>) bool.get(Constants.MUST_NOT);
    if (mustNotList == null) {
      mustNotList = new ArrayList<>();
      bool.put(Constants.MUST_NOT, mustNotList);
    }
    mustNotList.add(subQuery.build());
    return this;
  }

  public Map<String, Object> build() {
    return query;
  }
}
