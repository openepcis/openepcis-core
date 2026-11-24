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
import java.util.Collection;
import org.apache.commons.lang3.StringUtils;

public class MatchFilter extends AbstractFilter {

  public MatchFilter(QueryParam param) {
    super(param, param.getValue());
  }

  @Override
  public QueryBuilder getQuery() {
    final QueryBuilder boolQuery = new QueryBuilder().addBoolQuery();
    getQueryParam()
        .getQueryParamName()
        .getKeys()
        .forEach(
            (key, path) -> {
              if (StringUtils.isBlank(path)) {
                if (getValue() instanceof Collection) {
                  ((Collection) getValue())
                      .forEach(
                          value -> {
                            boolQuery.addShouldClause(
                                new QueryBuilder().addWildcardQuery(key, value.toString()));
                          });
                } else {
                  boolQuery.addShouldClause(
                      new QueryBuilder().addWildcardQuery(key, getValue().toString()));
                }
              } else {
                if (getValue() instanceof Collection) {
                  ((Collection) getValue())
                      .forEach(
                          value -> {
                            final QueryBuilder wildcardQueryBuilder =
                                new QueryBuilder().addWildcardQuery(key, value.toString());
                            QueryBuilder nestedQuery =
                                new QueryBuilder().addNestedQuery(path, wildcardQueryBuilder);
                            boolQuery.addShouldClause(nestedQuery);
                          });
                } else {
                  boolQuery.addShouldClause(
                      new QueryBuilder()
                          .addNestedQuery(
                              path,
                              new QueryBuilder().addWildcardQuery(key, getValue().toString())));
                }
              }
            });
    return boolQuery;
  }
}
