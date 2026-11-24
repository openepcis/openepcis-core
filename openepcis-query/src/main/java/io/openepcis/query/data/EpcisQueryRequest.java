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
package io.openepcis.query.data;

import io.openepcis.model.epcis.exception.ExceptionMessages;
import io.openepcis.model.epcis.exception.QueryParameterException;
import java.util.LinkedList;
import java.util.List;

public abstract class EpcisQueryRequest {
  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(EpcisQueryRequest.class);
  protected List<QueryParam> filters = new LinkedList<>();
  protected Integer maxEventCount;

  public EpcisQueryRequest(List<QueryParam> params) {
    if (params == null || params.isEmpty()) {
      throw new QueryParameterException(String.format(ExceptionMessages.VALUE_REQUIRED, "Parameters"));
    }
    for (QueryParam inputParam : params) {
      QueryParamName paramName = inputParam.getQueryParamName();
      if (!getQueryType().equals(paramName.getQueryType())) {
        throw new QueryParameterException(ExceptionMessages.INVALID_QUERY_PARAMETER_NAME);
      }
      if (!paramName.isNullable() && inputParam.getValue() == null) {
        continue;
      }
      processParameter(inputParam);
      if (paramName.skipParamFromFilters()) {
        continue;
      }
      if (!inputParam.getQueryParamName().isUserExtension()) {
        for (QueryParam filter : filters) {
          if (filter.equals(inputParam)) {
            throw new QueryParameterException(String.format(ExceptionMessages.DUPLICATE_VALUE, "name", inputParam.getName()));
          }
        }
      }
      this.filters.add(inputParam);
    }
  }

  public List<QueryParam> getFilters() {
    return filters;
  }

  public Integer getMaxEventCount() {
    return maxEventCount;
  }

  public void setMaxEventCount(Integer maxEventCount) {
    this.maxEventCount = maxEventCount;
  }

  public abstract EpcisQueryType getQueryType();

  public void processParameter(QueryParam param) {
    if (QueryParamName.maxEventCount.equals(param.getQueryParamName())) {
      this.maxEventCount = wholeNumber("maxEventCount", param.getValue());
    }
  }

  /**
   * A count parameter as a number, whichever way it arrived: a number from a JSON query, a string
   * from a query string, or a list of one from a multi-valued map. Anything else is the caller's
   * error (400), not ours.
   */
  protected static Integer wholeNumber(final String name, final Object value) {
    Object single = value instanceof java.util.Collection<?> list && list.size() == 1 ? list.iterator().next() : value;
    if (single == null) {
      return null;
    }
    if (single instanceof Integer i) {
      return i;
    }
    if (single instanceof Number n && n.doubleValue() == Math.rint(n.doubleValue())) {
      return n.intValue();
    }
    try {
      return Integer.valueOf(single.toString().trim());
    } catch (NumberFormatException e) {
      throw new QueryParameterException(name + " must be a whole number, got '" + single + "'");
    }
  }
}
