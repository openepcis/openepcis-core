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
package io.openepcis.query.service.util;

import static io.openepcis.model.epcis.constants.QueryParameterConstants.CONTEXT;
import io.openepcis.query.data.QueryParam;
import io.openepcis.query.factory.QueryParamMapper;
import io.openepcis.query.util.UnitConverter;
import io.openepcis.repository.Constants;
import io.openepcis.service.common.ContextBuilder;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@ApplicationScoped
public class QueryParamUtil {
  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(QueryParamUtil.class);
  private final UnitConverter unitConverter;

  public Uni<List<QueryParam>> getListOfQueryParams(final Map<String, Object> params, Optional<String> gs1Extensions, String defaultGroup) {
    params.put(Constants.DEFAULT_GROUP, defaultGroup);
    return getListOfQueryParams(params, false, gs1Extensions);
  }

  public Uni<List<QueryParam>> getListOfQueryParams(Map<String, Object> params, boolean shouldAllowSingleValues, Optional<String> gs1Extensions) {
    return Uni.createFrom().emitter(emitter -> {
      try {
        if (params.containsKey(CONTEXT) || gs1Extensions.isPresent()) emitter.complete(params.entrySet().stream().filter(k -> !k.getKey().equals(CONTEXT)).map(gs1Extensions.isPresent() ? new QueryParamMapper(shouldAllowSingleValues, unitConverter, params.get(CONTEXT), ContextBuilder.getGs1ExtensionAsMap(gs1Extensions)) : new QueryParamMapper(shouldAllowSingleValues, unitConverter, params.get(CONTEXT), Optional.empty())).collect(Collectors.toList()));
         else emitter.complete(params.entrySet().stream().map(ContextBuilder.getGs1ExtensionAsMap(gs1Extensions).isPresent() ? new QueryParamMapper(shouldAllowSingleValues, unitConverter, ContextBuilder.getGs1ExtensionAsMap(gs1Extensions)) : new QueryParamMapper(shouldAllowSingleValues, unitConverter, Optional.empty())).collect(Collectors.toList()));
      } catch (RuntimeException e) {
        emitter.fail(e);
      }
    });
  }

  public QueryParamUtil(UnitConverter unitConverter) {
    this.unitConverter = unitConverter;
  }
}
