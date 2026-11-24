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

import io.openepcis.model.epcis.exception.QueryParameterException;

public class QueryParam {

  protected QueryParamName queryParamName;
  protected Object value;

  public QueryParam() {}

  public QueryParam(QueryParam param) {
    this(param.getName(), param.getValue());
  }

  public QueryParam(String paramName, Object value) throws QueryParameterException {
    this.queryParamName = QueryParamName.parse(paramName);
    this.value = value;
  }

  public Object getValue() {
    return value;
  }

  public void setValue(Object value) {
    this.value = value;
  }

  public QueryParamName getQueryParamName() {
    return queryParamName;
  }

  public void setQueryParamName(QueryParamName queryParamName) {
    this.queryParamName = queryParamName;
  }

  public String getName() {
    return queryParamName.name();
  }

  public void setName(String name) throws QueryParameterException {
    queryParamName = QueryParamName.parse(name);
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((queryParamName == null) ? 0 : queryParamName.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    QueryParam other = (QueryParam) obj;
    if (queryParamName != other.queryParamName) return false;
    return true;
  }
}
