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

import io.openepcis.model.epcis.util.DataTypeUtil;
import io.openepcis.query.builder.QueryBuilder;
import io.openepcis.query.data.QueryParam;
import java.util.Collection;
import java.util.Map;

public class MultiEqFilter extends AbstractFilter {

  public MultiEqFilter(QueryParam key) {
    super(key, key.getValue());
  }

  @Override
  public QueryBuilder getQuery() {
    final QueryBuilder boolQuery = new QueryBuilder().addBoolQuery();
    getQueryParam()
        .getQueryParamName()
        .getKeys()
        .forEach(
            (field1, field2) -> {
              final Map<String, Object> fieldValuePairs = (Map) getValue();
              final QueryBuilder shouldQueryBuilder = new QueryBuilder().addBoolQuery();
              fieldValuePairs.forEach(
                  (field1Value, field2Value) -> {
                    if (field2Value instanceof Collection) {
                      final QueryBuilder filterBoolQuery =
                          new QueryBuilder()
                              .addBoolQuery()
                              .addFilterClause(new QueryBuilder().addTermQuery(field1, field1Value))
                              .addFilterClause(
                                  new QueryBuilder()
                                      .addTermsQuery(
                                          getValueKey(field2, field2Value),
                                          ((Collection<?>) field2Value).toArray()));
                      shouldQueryBuilder.addShouldClause(filterBoolQuery);
                    } else {
                      final QueryBuilder filterBoolQuery =
                          new QueryBuilder()
                              .addBoolQuery()
                              .addFilterClause(new QueryBuilder().addTermQuery(field1, field1Value))
                              .addFilterClause(
                                  new QueryBuilder()
                                      .addTermQuery(getValueKey(field2, field2Value), field2Value));
                      shouldQueryBuilder.addShouldClause(filterBoolQuery);
                    }
                  });
              String path = field2.substring(0, field2.lastIndexOf('.'));

              QueryBuilder nestedQuery =
                  new QueryBuilder().addNestedQuery(path, shouldQueryBuilder);
              boolQuery.addShouldClause(nestedQuery);
            });
    return boolQuery;
  }

  private String getValueKey(String valueKey, Object value) {

    return getQueryParam().getQueryParamName().isUserExtension()
        ? valueKey
            .concat(DataTypeUtil.getPathSuffixFromType(value))
            .concat(getFieldSuffix(valueKey))
            .trim()
        : valueKey;
  }

  private String getFieldSuffix(String valueKey) {

    return valueKey.contains("attributes") ? "Attribute" : "Value  ";
  }
}
