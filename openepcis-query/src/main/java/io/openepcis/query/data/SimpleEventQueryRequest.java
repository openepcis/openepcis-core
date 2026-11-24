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

import static io.openepcis.model.epcis.exception.ExceptionMessages.EVENT_COUNT_LIMIT_MUST_BE_USED_ALONG_WITH_ORDER_BY;
import static io.openepcis.model.epcis.exception.ExceptionMessages.MAX_EVENT_COUNT_AND_EVENT_COUNT_LIMIT_ARE_MUTUALLY_EXCLUSIVE;
import io.openepcis.model.epcis.OrderByUserExtension;
import io.openepcis.model.epcis.exception.QueryParameterException;
import java.util.List;

public class SimpleEventQueryRequest extends EpcisQueryRequest {
  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SimpleEventQueryRequest.class);
  private String orderBy;
  private String orderDirection;
  private Integer eventCountLimit;
  private OrderByUserExtension orderByUX;

  public SimpleEventQueryRequest(List<QueryParam> params) throws QueryParameterException {
    super(params);
    validateQueryParameters();
  }

  @Override
  public void processParameter(QueryParam param) {
    super.processParameter(param);
    Object value = param.getValue();
    try {
      switch (param.getQueryParamName()) {
      case orderBy: 
        this.orderBy = (String) value;
        break;
      case orderDirection: 
        this.orderDirection = (String) value;
        break;
      case eventCountLimit: 
        // a query string brings it as text; the cast used to fail and the limit was dropped
        this.eventCountLimit = wholeNumber("eventCountLimit", value);
        break;
      default: 
        break;
      }
    } catch (ClassCastException ex) {
      log.error("Please ensure the query request format is correct", ex);
    }
  }

  public void validateQueryParameters() throws QueryParameterException {
    if (eventCountLimit != null && maxEventCount != null) {
      throw new QueryParameterException(MAX_EVENT_COUNT_AND_EVENT_COUNT_LIMIT_ARE_MUTUALLY_EXCLUSIVE);
    }
    if (orderBy == null && eventCountLimit != null) {
      throw new QueryParameterException(EVENT_COUNT_LIMIT_MUST_BE_USED_ALONG_WITH_ORDER_BY);
    }
  }

  public String getOrderBy() {
    return orderBy;
  }

  public void setOrderBy(String orderBy) {
    this.orderBy = orderBy;
  }

  public OrderByUserExtension getOrderByUX() {
    return orderByUX;
  }

  public void setOrderByUX(OrderByUserExtension orderByUX) {
    this.orderByUX = orderByUX;
  }

  public String getOrderDirection() {
    return orderDirection != null ? orderDirection.toUpperCase() : null;
  }

  public void setOrderDirection(String orderDirection) {
    this.orderDirection = orderDirection;
  }

  public Integer getEventCountLimit() {
    return eventCountLimit;
  }

  public void setEventCountLimit(Integer eventCountLimit) {
    this.eventCountLimit = eventCountLimit;
  }

  @Override
  public EpcisQueryType getQueryType() {
    return EpcisQueryType.SIMPLE_EVENT_QUERY;
  }
}
