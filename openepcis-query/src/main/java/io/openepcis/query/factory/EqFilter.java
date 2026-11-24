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
import io.openepcis.query.data.QueryParamName;
import io.openepcis.query.data.TypeQueryParam;
import java.util.Collection;
import java.util.List;
import org.apache.commons.lang3.StringUtils;

public class EqFilter extends AbstractFilter {

  final List<String> specificKeys =
      List.of(
          "bizTransactionList.type.asURI",
          "bizTransactionList.type.asURN",
          "bizTransactionList.type.asCaptured",
          "sourceList.type.asURN",
          "sourceList.type.asURI",
          "sourceList.type.asCaptured",
          "destinationList.type.asURN",
          "destinationList.type.asURI",
          "destinationList.type.asCaptured");

  public EqFilter(QueryParam key) {
    super(key, key.getValue());
  }

  public QueryBuilder getQuery() {
    final QueryBuilder boolQuery = new QueryBuilder().addBoolQuery();
    final QueryParam queryParam = getQueryParam();
    final QueryParamName queryParamName = getQueryParam().getQueryParamName();

    queryParamName
        .getKeys()
        .forEach(
            (key, path) -> {
              QueryBuilder query;

              if (specificKeys.contains(key)) {
                query =
                    new QueryBuilder()
                        .addTermQuery(key, ((TypeQueryParam) queryParam).getTypeValue());
              } else {
                if (getValue() instanceof Collection) {
                  query = new QueryBuilder().addTermsQuery(key, getValue());
                } else {
                  query = new QueryBuilder().addTermQuery(key, getValue());
                }
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
