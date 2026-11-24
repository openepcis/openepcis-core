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

import io.openepcis.query.data.QueryParam;
import io.openepcis.query.data.ValueType;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class FilterFactory {
  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(FilterFactory.class);

  public AbstractFilter createFilter(QueryParam param) {
    switch (param.getQueryParamName().getOperator()) {
      case MATCH -> {
        return new MatchFilter(param);
      }
      case EQ -> {
        if (param.getQueryParamName().getValueType().equals(ValueType.PAIR)) return new MultiEqFilter(param);
        return new EqFilter(param);
      }
      case GE, LT, GT, LE -> {
        if (param.getQueryParamName().getValueType().equals(ValueType.PAIR)) return new MultiRangeOperatorFilter(param);
        return new RangeOperatorFilter(param);
      }
      case EXISTS -> {
        if (param.getQueryParamName().isUserExtension()) return new ExistUserExtensionFieldFilter(param);
        return new ExistFieldFilter(param);
      }
      default -> {
        return null;
      }
    }
  }

  public FilterFactory() {
  }
}
