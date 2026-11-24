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
package io.openepcis.service.util;

import static io.openepcis.model.epcis.constants.EpcisEventFieldConstants.SENSOR_ELEMENT_LIST;
import static io.openepcis.model.epcis.constants.EpcisEventFieldConstants.SENSOR_METADATA;

import com.fasterxml.jackson.databind.JsonNode;
import io.openepcis.model.rest.EPCISEventTypes;
import java.util.Collection;
import java.util.function.Predicate;
import org.apache.commons.collections4.CollectionUtils;

public class EventPredicates {

  private EventPredicates() {}

  public static final Predicate<JsonNode> IS_SENSOR_EVENT = e -> e.has(SENSOR_ELEMENT_LIST);
  public static final Predicate<JsonNode> HAS_SENSOR_ELEMENT = e -> e.has(SENSOR_ELEMENT_LIST);
  public static final Predicate<JsonNode> HAS_SENSOR_METADATA = e -> e.has(SENSOR_METADATA);
  public static final Predicate<JsonNode> HAS_READ_POINT = e -> e.has(Constants.READ_POINT);
  public static final Predicate<JsonNode> HAS_BIZ_LOCATION = e -> e.has(Constants.BIZ_LOCATION);
  public static final Predicate<JsonNode> HAS_BIZ_STEP = e -> e.has(Constants.BIZ_STEP);
  public static final Predicate<JsonNode> HAS_DISPOSITION = e -> e.has(Constants.DISPOSITION);
  public static final Predicate<JsonNode> HAS_BIZ_TRANSACTION_LIST =
      e -> e.has(Constants.BIZ_TRANSACTION_LIST);
  public static final Predicate<JsonNode> HAS_SOURCE_LIST = e -> e.has(Constants.SOURCE_LIST);
  public static final Predicate<JsonNode> HAS_DESTINATION_LIST =
      e -> e.has(Constants.DESTINATION_LIST);
  public static final Predicate<JsonNode> HAS_EVENT_ID = e -> e.has(Constants.EVENT_ID);
  public static final Predicate<String> INVALID_EVENT_TYPE =
      e -> null == EPCISEventTypes.fromValue(e);
  public static final Predicate<JsonNode> IS_ERROR_DECLARATION =
      e -> e.has(Constants.ERROR_DECLARATION);
  public static final Predicate<JsonNode> HAS_ERROR_DECLARATION =
      e -> e.has(Constants.ERROR_DECLARATION);
  public static final Predicate<Collection> HAS_CORRECTIVE_EVENT_ID = CollectionUtils::isNotEmpty;
  public static final Predicate<JsonNode> HAS_ILMD = e -> e.has(Constants.ILMD);
}
