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

import io.openepcis.query.util.Constants;
import java.util.*;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.query_dsl.*;

public class QueryConverterOpenSearchJC {

  public static Query convert(Map<String, Object> queryMap) {
    return convertQuery(queryMap);
  }

  private static Query convertQuery(Map<String, Object> queryMap) {
    if (Objects.isNull(queryMap)) {
      return null;
    }
    if (queryMap.containsKey(Constants.TERM)) {
      return getTermQuery(queryMap);
    }
    if (queryMap.containsKey(Constants.TERMS)) {
      return getTermsQuery(queryMap);
    }
    if (queryMap.containsKey(Constants.RANGE)) {
      return getRangeQuery(queryMap);
    }
    if (queryMap.containsKey(Constants.BOOL)) {
      return getBoolQuery(queryMap);
    }
    if (queryMap.containsKey(Constants.EXITS)) {
      return getExistsQuery(queryMap);
    }
    if (queryMap.containsKey(Constants.NESTED)) {
      return getNestedQuery(queryMap);
    }
    if (queryMap.containsKey(Constants.WILDCARD)) {
      return getWildCardQuery(queryMap);
    }
    if (queryMap.containsKey(Constants.MATCH_ALL)) {
      return Query.of(q -> q.matchAll(QueryBuilders.matchAll().build()));
    }
    return null;
  }

  private static Query getWildCardQuery(Map<String, Object> queryMap) {
    Map<String, Object> wildcard = (Map<String, Object>) queryMap.get(Constants.WILDCARD);
    Map.Entry<String, Object> entry = wildcard.entrySet().stream().findFirst().get();
    Map<String, Object> valueMap = (Map<String, Object>) entry.getValue();
    String value = (String) valueMap.get(Constants.VALUE);
    return Query.of(q -> q.wildcard(QueryBuilders.wildcard().field(entry.getKey()).value(value).build()));
  }

  private static Query getNestedQuery(Map<String, Object> queryMap) {
    Map<String, Object> nested = (Map<String, Object>) queryMap.get(Constants.NESTED);
    String path = (String) nested.get(Constants.PATH);
    Map<String, Object> nestedQuery = (Map<String, Object>) nested.get(Constants.QUERY);
    return Query.of(q -> q.nested(QueryBuilders.nested()
        .path(path)
        .query(convertQuery(nestedQuery))
        .scoreMode(ChildScoreMode.Avg)
        .build()));
  }

  private static Query getExistsQuery(Map<String, Object> queryMap) {
    Map<String, Object> exists = (Map<String, Object>) queryMap.get(Constants.EXITS);
    String field = (String) exists.get(Constants.FIELD);
    return Query.of(q -> q.exists(QueryBuilders.exists().field(field).build()));
  }

  private static Query getBoolQuery(Map<String, Object> queryMap) {
    BoolQuery.Builder queryBuilder = QueryBuilders.bool();
    Map<String, Object> clauses = (Map<String, Object>) queryMap.get(Constants.BOOL);
    if (clauses.containsKey(Constants.MUST)) {
      for (Object must : (List<Object>) clauses.get(Constants.MUST)) {
        queryBuilder.must(convertQuery((Map<String, Object>) must));
      }
    }
    if (clauses.containsKey(Constants.SHOULD)) {
      for (Object should : (List<Object>) clauses.get(Constants.SHOULD)) {
        queryBuilder.should(convertQuery((Map<String, Object>) should));
      }
    }
    if (clauses.containsKey(Constants.MUST_NOT)) {
      for (Object mustNot : (List<Object>) clauses.get(Constants.MUST_NOT)) {
        queryBuilder.mustNot(convertQuery((Map<String, Object>) mustNot));
      }
    }
    if (clauses.containsKey(Constants.FILTER)) {
      for (Object filter : (List<Object>) clauses.get(Constants.FILTER)) {
        queryBuilder.filter(convertQuery((Map<String, Object>) filter));
      }
    }
    return Query.of(q -> q.bool(queryBuilder.build()));
  }

  private static Query getRangeQuery(Map<String, Object> queryMap) {
    BoolQuery.Builder queryBuilder = QueryBuilders.bool();
    Map<String, Object> ranges = (Map<String, Object>) queryMap.get(Constants.RANGE);
    ranges.forEach(
        (key, value) -> {
          Map<String, Object> rangeValue = (Map<String, Object>) value;
          RangeQuery.Builder rangeQueryBuilder = QueryBuilders.range().field(key);
          if (rangeValue.containsKey(Constants.GTE)) {
            rangeQueryBuilder.gte(JsonData.of(rangeValue.get(Constants.GTE)));
          }
          if (rangeValue.containsKey(Constants.GT)) {
            rangeQueryBuilder.gt(JsonData.of(rangeValue.get(Constants.GT)));
          }
          if (rangeValue.containsKey(Constants.LTE)) {
            rangeQueryBuilder.lte(JsonData.of(rangeValue.get(Constants.LTE)));
          }
          if (rangeValue.containsKey(Constants.LT)) {
            rangeQueryBuilder.lt(JsonData.of(rangeValue.get(Constants.LT)));
          }
          queryBuilder.filter(Query.of(q -> q.range(rangeQueryBuilder.build())));
        });
    return Query.of(q -> q.bool(queryBuilder.build()));
  }

  private static Query getTermsQuery(Map<String, Object> queryMap) {
    Map<String, Object> termQuery = (Map<String, Object>) queryMap.get(Constants.TERMS);
    Map.Entry<String, Object> terms = termQuery.entrySet().stream().findFirst().get();
    Object termsValue = terms.getValue();
    List<?> list =
        (termsValue == null)
            ? Collections.emptyList()
            : (termsValue.getClass().isArray())
                ? Arrays.asList((Object[]) termsValue)
                : (termsValue instanceof Collection)
                    ? new ArrayList<>((Collection<?>) termsValue)
                    : Collections.singletonList(termsValue);
    TermsQueryField termsQueryField =
        new TermsQueryField.Builder()
            .value(list.stream().map(item -> FieldValue.of(item.toString())).toList())
            .build();
    return Query.of(q -> q.terms(QueryBuilders.terms().field(terms.getKey()).terms(termsQueryField).build()));
  }

  private static Query getTermQuery(Map<String, Object> queryMap) {
    Map<String, Object> termQuery = (Map<String, Object>) queryMap.get(Constants.TERM);
    Map.Entry<String, Object> term = termQuery.entrySet().stream().findFirst().get();
    return Query.of(q -> q.term(QueryBuilders.term()
        .field(term.getKey())
        .value(FieldValue.of(term.getValue().toString()))
        .build()));
  }
}
