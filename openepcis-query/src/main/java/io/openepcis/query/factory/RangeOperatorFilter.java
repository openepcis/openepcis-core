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
import io.openepcis.opentelemetry.logging.OpenEPCISLogger;
import io.openepcis.query.builder.QueryBuilder;
import io.openepcis.query.data.OperatorType;
import io.openepcis.query.data.QueryParam;
import org.apache.commons.lang3.StringUtils;

public class RangeOperatorFilter extends AbstractFilter {

  private OperatorType operator;
  private static final OpenEPCISLogger log = OpenEPCISLogger.getLogger(RangeOperatorFilter.class);

  public RangeOperatorFilter(QueryParam key) {
    super(key, key.getValue());
    operator = key.getQueryParamName().getOperator();
  }

  @Override
  public QueryBuilder getQuery() {
    final QueryBuilder boolQuery = new QueryBuilder().addBoolQuery();
    getQueryParam()
        .getQueryParamName()
        .getKeys()
        .forEach(
            (key, path) -> {
              final QueryBuilder rangeQuery = new QueryBuilder().addRange(key);
              applyOperator(rangeQuery, key, String.valueOf(getValue()));
              if (StringUtils.isBlank(path)) {
                boolQuery.addShouldClause(rangeQuery);
              } else {
                QueryBuilder nestedQuery = new QueryBuilder().addNestedQuery(path, rangeQuery);
                boolQuery.addShouldClause(nestedQuery);
              }
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
    Object val;
    try {
      try {
        val = Long.parseLong(value);
      } catch (Exception e) {
        val = Double.parseDouble(value);
      }
    } catch (Exception p) {
      log.debug("Value {} - not parsable to double.", value);
      val = value;
    }
    return val;
  }
}
