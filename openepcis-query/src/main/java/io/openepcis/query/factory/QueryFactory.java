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

import io.openepcis.opentelemetry.logging.OpenEPCISLogger;
import io.openepcis.query.builder.QueryBuilder;
import io.openepcis.query.data.QueryParam;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

@ApplicationScoped
public class QueryFactory {
  private FilterFactory filterFactory;
  private static final OpenEPCISLogger log = OpenEPCISLogger.getLogger(QueryFactory.class);

  @SuppressWarnings("rawtypes")
  public QueryBuilder createQuery(QueryParam param) {
    final AbstractFilter filter = filterFactory.createFilter(param);
    return filter.getQuery();
  }

  public QueryBuilder createMainQuery(List<QueryParam> params) {
    final QueryBuilder mainQuery = new QueryBuilder().addBoolQuery();
    for (QueryParam param : params) {
      if (param.getQueryParamName().isNested()) {
        final QueryBuilder nestedQuery = createNestedQuery(param);
        if (nestedQuery != null) {
          mainQuery.addMustClause(nestedQuery);
        }
      } else {
        final QueryBuilder query = createQuery(param);
        if (query != null) {
          mainQuery.addMustClause(query);
        }
      }
    }
    if (mainQuery.build().isEmpty()) {
      return new QueryBuilder().addMatchAllQuery();
    }
    return mainQuery;
  }

  public QueryBuilder createNestedQuery(QueryParam param) {
    return new QueryBuilder().addNestedQuery(param.getQueryParamName().getNestedPath(), createQuery(param));
  }

  public QueryFactory(FilterFactory filterFactory) {
    this.filterFactory = filterFactory;
  }
}
