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
package io.openepcis.query.factory;

import io.openepcis.model.epcis.exception.ExceptionMessages;
import io.openepcis.model.epcis.util.DataTypeUtil;
import io.openepcis.query.builder.QueryBuilder;
import io.openepcis.query.data.OperatorType;
import io.openepcis.query.data.QueryParam;
import java.util.Map;

public class MultiRangeOperatorFilter extends AbstractFilter {
  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MultiRangeOperatorFilter.class);
  private OperatorType operator;

  public MultiRangeOperatorFilter(QueryParam key) {
    super(key, key.getValue());
    operator = key.getQueryParamName().getOperator();
  }

  @Override
  public QueryBuilder getQuery() {
    final QueryBuilder boolQuery = new QueryBuilder().addBoolQuery();
    getQueryParam().getQueryParamName().getKeys().forEach((field1, field2) -> {
      Map<String, Object> fieldValuePairs = (Map) getValue();
      final QueryBuilder shouldQueryBuilder = new QueryBuilder().addBoolQuery();
      fieldValuePairs.forEach((field1Value, field2Value) -> {
        final QueryBuilder filterBoolQuery = new QueryBuilder().addBoolQuery().addFilterClause(new QueryBuilder().addTermQuery(field1, field1Value));
        final QueryBuilder rangeQuery = new QueryBuilder().addRange(getValueKey(field2, field2Value));
        applyOperator(rangeQuery, getValueKey(field2, field2Value), String.valueOf(field2Value));
        filterBoolQuery.addFilterClause(rangeQuery);
        shouldQueryBuilder.addShouldClause(filterBoolQuery);
      });
      String nestedPath = field2.substring(0, field2.lastIndexOf('.'));
      QueryBuilder nestedQuery = new QueryBuilder().addNestedQuery(nestedPath, shouldQueryBuilder);
      boolQuery.addShouldClause(nestedQuery);
    });
    return boolQuery;
  }

  private void applyOperator(QueryBuilder rangeQuery, String field, String value) {
    final Object val = parse(value);
    switch (operator) {
      case GE -> rangeQuery.addGTE(field, val);
      case GT -> rangeQuery.addGT(field, val);
      case LE -> rangeQuery.addLTE(field, val);
      case LT -> rangeQuery.addLT(field, val);
      default -> log.warn(ExceptionMessages.NO_MATCHING_OPERATOR_FOUND + operator);
    }
  }

  private Object parse(String value) {
    final String numValue = value.replaceAll("[\\[\\]]", "");
    if (DataTypeUtil.isLong(numValue)) {
      return Long.parseLong(numValue);
    }
    if (DataTypeUtil.isDouble(numValue)) {
      return Double.parseDouble(numValue);
    }
    log.debug("Value {} is neither long nor double", value);
    return value;
  }

  private String getValueKey(String valueKey, Object value) {
    return getQueryParam().getQueryParamName().isUserExtension() ? valueKey.concat(DataTypeUtil.getPathSuffixFromType(value)).concat(getFieldSuffix(valueKey)).trim() : valueKey;
  }

  private String getFieldSuffix(String valueKey) {
    return valueKey.contains("attributes") ? "Attribute" : "Value  ";
  }
}
