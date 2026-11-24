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
package io.openepcis.query.service.validator;

import static io.openepcis.model.epcis.constants.EpcisEventFieldConstants.QUERY;
import static io.openepcis.model.epcis.constants.QueryParameterConstants.*;
import io.openepcis.core.exception.MarshallingException;
import io.openepcis.model.epcis.exception.ExceptionMessages;
import io.openepcis.model.epcis.exception.NoSuchEventTypeException;
import io.openepcis.model.epcis.exception.QueryParameterException;
import io.openepcis.model.epcis.exception.QueryValidationException;
import io.openepcis.query.data.QueryParamName;
import io.openepcis.query.data.ValueType;
import io.openepcis.query.util.UnitConverter;
import io.openepcis.repository.api.QueryRepository;
import io.openepcis.service.util.EventPredicates;
import io.openepcis.validation.service.JsonSchemaValidator;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONException;
import org.json.JSONObject;

@ApplicationScoped
public class NamedQueryValidator {
  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(NamedQueryValidator.class);
  private final JsonSchemaValidator querySchemaValidator;
  private final QueryRepository queryRepository;
  private final UnitConverter unitConverter;

  public void validateEPCISQuery(final String epcisQueryAsString) {
    if (StringUtils.isBlank(epcisQueryAsString)) throw new QueryParameterException(String.format(ExceptionMessages.VALUE_REQUIRED, PARAMETERS));
    final JSONObject queryData = getJSONObject(epcisQueryAsString);
    querySchemaValidator.validateAgainstQueryJSONSchema(queryData.get(QUERY).toString());
    if (!(queryData.has(QUERY)) || StringUtils.isBlank(queryData.get(QUERY).toString())) throw new QueryParameterException(String.format(ExceptionMessages.VALUE_REQUIRED, PARAMETERS));
  }

  public Uni<Boolean> validateQueryName(final String queryName) {
    if (StringUtils.isBlank(queryName)) throw new QueryValidationException(ExceptionMessages.QUERY_NAME_MUST);
    return queryRepository.isQueryExists(queryName);
  }

  public void validateQueryParameters(final Map<String, Object> epcisQuery) {
    if (epcisQuery.containsKey(ORDER_DIRECTION) && !(epcisQuery.containsKey(ORDERBY))) throw new QueryParameterException(ExceptionMessages.ORDERBY_ORDERDIRECTION_MUST_ALWAYS_BE_PAIRED);
    if (epcisQuery.containsKey(EVENT_COUNT_LIMIT) && epcisQuery.containsKey(MAX_EVENT_COUNT)) {
      throw new QueryParameterException(ExceptionMessages.MAX_EVENT_COUNT_AND_EVENT_COUNT_LIMIT_ARE_MUTUALLY_EXCLUSIVE);
    }
    if (!(epcisQuery.containsKey(ORDERBY)) && epcisQuery.containsKey(EVENT_COUNT_LIMIT)) {
      throw new QueryParameterException(ExceptionMessages.EVENT_COUNT_LIMIT_MUST_BE_USED_ALONG_WITH_ORDER_BY);
    }
    epcisQuery.forEach((k, v) -> {
      QueryParamName param = QueryParamName.parse(k);
      if (param.isRegex() && param.getValueType() == ValueType.PAIR && !param.isUserExtension()) {
        unitConverter.relatedUOM(k.substring(k.lastIndexOf("_") + 1));
      }
    });
  }

  public void validateForEventTypesEndPoint(String eventType, Map<String, Object> params) {
    if (EventPredicates.INVALID_EVENT_TYPE.test(eventType)) {
      log.debug("{} is not a valid event type.", eventType);
      throw new NoSuchEventTypeException(String.format("%s is not a valid event type.", eventType));
    }
    if (params.containsKey("eventType") && !StringUtils.equalsIgnoreCase(eventType, params.get("eventType").toString())) {
      log.debug(ExceptionMessages.EVENT_TYPE_NOT_ALLOWED_HERE);
      throw new QueryValidationException(ExceptionMessages.EVENT_TYPE_NOT_ALLOWED_HERE);
    }
  }

  private JSONObject getJSONObject(String body) {
    try {
      return new JSONObject(body);
    } catch (JSONException e) {
      log.info(e.getLocalizedMessage());
      throw new MarshallingException(e.getLocalizedMessage());
    }
  }

  public NamedQueryValidator(JsonSchemaValidator querySchemaValidator, QueryRepository queryRepository, UnitConverter unitConverter) {
    this.querySchemaValidator = querySchemaValidator;
    this.queryRepository = queryRepository;
    this.unitConverter = unitConverter;
  }
}
