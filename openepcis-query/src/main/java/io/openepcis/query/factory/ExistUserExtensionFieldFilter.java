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

import io.openepcis.query.builder.QueryBuilder;
import io.openepcis.query.data.QueryParam;
import java.util.Map;

public class ExistUserExtensionFieldFilter extends AbstractFilter {

  public ExistUserExtensionFieldFilter(QueryParam key) {
    super(key, key.getValue());
  }

  @Override
  public QueryBuilder getQuery() {
    final QueryBuilder boolQuery = new QueryBuilder().addBoolQuery();
    getQueryParam()
        .getQueryParamName()
        .getKeys()
        .forEach(
            (key, path) -> {
              final Map<String, Object> fieldValuePairs = (Map) getValue();
              final QueryBuilder shouldQueryBuilder = new QueryBuilder().addBoolQuery();
              fieldValuePairs.forEach(
                  (field, value) -> {
                    final QueryBuilder filterBoolQuery =
                        new QueryBuilder()
                            .addBoolQuery()
                            .addFilterClause(
                                new QueryBuilder()
                                    .addTermQuery(key, (path.endsWith(".") ? "." : "") + field));
                    shouldQueryBuilder.addShouldClause(filterBoolQuery);
                  });

              QueryBuilder nestedQuery =
                  new QueryBuilder()
                      .addNestedQuery(
                          path.endsWith(".") ? path.substring(0, path.length() - 1) : path,
                          shouldQueryBuilder);
              boolQuery.addShouldClause(nestedQuery);
            });
    return boolQuery;
  }
}
