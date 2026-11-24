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

public class EqFilterForUserExtensionFields extends AbstractFilter {

  public EqFilterForUserExtensionFields(QueryParam key) {
    super(key, key.getValue());
  }

  @Override
  public QueryBuilder getQuery() {
    final QueryBuilder boolQuery = new QueryBuilder().addBoolQuery();

    getQueryParam()
        .getQueryParamName()
        .getKeys()
        .forEach((key, value) -> new QueryBuilder().addTermQuery(key, "list"));

    getQueryParam()
        .getQueryParamName()
        .getKeys()
        .forEach(
            (key, path) -> {
              QueryBuilder query;
              if (getValue() instanceof Collection) {
                query =
                    new QueryBuilder().addTermsQuery(key, ((Collection<?>) getValue()).toArray());
              } else {
                query = new QueryBuilder().addTermQuery(key, getValue());
              }

              if (StringUtils.isBlank(path)) {
                boolQuery.addShouldClause(query);
              } else {
                QueryBuilder nestedQuery = new QueryBuilder().addNestedQuery(path, query);
                boolQuery.addShouldClause(nestedQuery);
              }
            });
    return boolQuery;
  }
}
