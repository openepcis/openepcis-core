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
import org.apache.commons.lang3.StringUtils;

public class ExistFieldFilter extends AbstractFilter {

  public ExistFieldFilter(QueryParam key) {
    super(key);
  }

  @Override
  public QueryBuilder getQuery() {
    if (isExplicitlyOff(getQueryParam().getValue())) {
      // The GS1 OpenAPI reads EXISTS_errorDeclaration=false as "regardless of a declaration",
      // the same as leaving the parameter out.
      return null;
    }
    final QueryBuilder boolQuery = new QueryBuilder().addBoolQuery();
    getQueryParam()
        .getQueryParamName()
        .getKeys()
        .forEach(
            (key, path) -> {
              if (StringUtils.isBlank(path)) {
                boolQuery.addShouldClause(new QueryBuilder().addExistsQuery(key));
              } else {
                boolQuery.addShouldClause(
                    new QueryBuilder()
                        .addNestedQuery(path, new QueryBuilder().addExistsQuery(key)));
              }
            });

    return boolQuery;
  }

  static boolean isExplicitlyOff(Object value) {
    final Object single =
        value instanceof java.util.Collection<?> c && c.size() == 1 ? c.iterator().next() : value;
    return single != null && "false".equalsIgnoreCase(single.toString().trim());
  }
}
