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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Streams;
import io.openepcis.model.epcis.*;
import io.openepcis.model.epcis.constants.EpcisEventFieldConstants;
import io.openepcis.repository.util.EventConvertor;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;

public class UserExtensionExtractor {
  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(UserExtensionExtractor.class);
  private static final ObjectMapper objectMapper = new ObjectMapper();

  private UserExtensionExtractor() {
    // Bunch of static methods so there should be no need to create object of this class
  }

  public static void extractUserExtensionsFromSensorElement(final ObjectNode eventJson, final EPCISEvent event, Map<String, Object> contextAsMap, Set<UserExtensionField> newUserExtensionFields) {
    final ArrayNode sensorElementArray = (ArrayNode) eventJson.get(EpcisEventFieldConstants.SENSOR_ELEMENT_LIST);
    String path = "sensorElementList";
    for (int i = 0; i < sensorElementArray.size(); i++) {
      final JsonNode sensorElement = sensorElementArray.get(i);
      Map<String, Object> userExtensions = getUserExtensions(sensorElement, path, contextAsMap, newUserExtensionFields);
      event.getSensorElementList().get(i).setUserExtensions(userExtensions);
      userExtensions = getInnerUserExtensions(event.getSensorElementList().get(i).getUserExtensions(), sensorElement, new HashMap<>(), path, contextAsMap, newUserExtensionFields);
      event.getSensorElementList().get(i).setInnerUserExtensions(userExtensions);
      setUserExtensionInSensorMetadata(event, sensorElement, i, contextAsMap, newUserExtensionFields);
      setUserExtensionsInSensorReport(event, sensorElement, i, contextAsMap, newUserExtensionFields);
    }
  }

  public static Map<String, Object> getInnerUserExtensions(final Map<String, Object> userExtensions, final JsonNode element, final Map<String, Object> innerUserExtensions, String path, Map<String, Object> context, Set<UserExtensionField> newUserExtensionFields) {
    if (element.isObject()) {
      return getInnerUserExtensions(userExtensions, (ObjectNode) element, innerUserExtensions, path, context, newUserExtensionFields);
    } else if (element.isArray()) {
      return getInnerUserExtensions(userExtensions, (ArrayNode) element, innerUserExtensions, path, context, newUserExtensionFields);
    }
    return innerUserExtensions;
  }

  public static Map<String, Object> getInnerUserExtensions(final Map<String, Object> userExtensions, final ObjectNode element, final Map<String, Object> innerUserExtensions, String path, Map<String, Object> context, Set<UserExtensionField> newUserExtensionFields) {
    userExtensions.forEach((key, value) -> {
      final String p;
      if (StringUtils.isNotBlank(path)) {
        p = path + "." + key.substring(key.lastIndexOf(".") + 1);
      } else {
        p = key.substring(key.lastIndexOf(".") + 1);
      }
      final JsonNode obj = element.get(key.substring(key.lastIndexOf(".") + 1));
      if (obj.isObject()) {
        innerUserExtensions.putAll(getAllInnerUserExtension((ObjectNode) obj, p));
      } else if (obj.isArray()) {
        innerUserExtensions.putAll(getAllInnerUserExtension((ArrayNode) obj, p));
      }
    });
    EventConvertor.mapUserExtensionToDataType(context, innerUserExtensions, newUserExtensionFields);
    return innerUserExtensions;
  }

  private static void setUserExtensionsInSensorReport(final EPCISEvent event, final JsonNode sensorElement, final int index, Map<String, Object> contextAsMap, Set<UserExtensionField> newUserExtensionFields) {
    final ArrayNode sensorReportArray = (ArrayNode) sensorElement.get(EpcisEventFieldConstants.SENSOR_REPORT);
    String path = "sensorElementList.sensorReport";
    for (int k = 0; k < sensorReportArray.size(); k++) {
      final JsonNode sensorReport = sensorReportArray.get(k);
      Map<String, Object> userExtensions = getUserExtensions(sensorReport, path, contextAsMap, newUserExtensionFields);
      event.getSensorElementList().get(index).getSensorReport().get(k).setUserExtensions(userExtensions);
      userExtensions = getInnerUserExtensions(event.getSensorElementList().get(index).getSensorReport().get(k).getUserExtensions(), sensorReport, new HashMap<>(), path, contextAsMap, newUserExtensionFields);
      event.getSensorElementList().get(index).getSensorReport().get(k).setInnerUserExtensions(userExtensions);
    }
  }

  private static void setUserExtensionInSensorMetadata(final EPCISEvent event, final JsonNode sensorElement, final int index, Map<String, Object> contextAsMap, Set<UserExtensionField> newUserExtensionFields) {
    if (EventPredicates.HAS_SENSOR_METADATA.test(sensorElement)) {
      String path = "sensorElementList.sensorMetadata";
      final JsonNode sensorMetadata = sensorElement.get(EpcisEventFieldConstants.SENSOR_METADATA);
      Map<String, Object> userExtensions = getUserExtensions(sensorMetadata, path, contextAsMap, newUserExtensionFields);
      if (MapUtils.isNotEmpty(userExtensions)) {
        event.getSensorElementList().get(index).getSensorMetadata().setUserExtensions(userExtensions);
        userExtensions = getInnerUserExtensions(event.getSensorElementList().get(index).getSensorMetadata().getUserExtensions(), sensorMetadata, new HashMap<>(), path, contextAsMap, newUserExtensionFields);
        event.getSensorElementList().get(index).getSensorMetadata().setInnerUserExtensions(userExtensions);
      }
    }
  }

  public static Map<String, Object> getAllInnerUserExtension(final ObjectNode json, String path) {
    return getAllInnerUserExtension(json, new HashMap<>(), path);
  }

  public static Map<String, Object> getAllInnerUserExtension(final ArrayNode arr, String path) {
    return getAllInnerUserExtension(arr, new HashMap<>(), path);
  }

  private static Map<String, Object> getAllInnerUserExtension(final ArrayNode arr, Map<String, Object> userExtensions, String path) {
    for (int i = 0; i < arr.size(); i++) {
      final JsonNode obj = arr.get(i);
      if (obj.isObject()) {
        final Map<String, Object> tempUXs = getAllInnerUserExtension((ObjectNode) arr.get(i), path);
        aggregateValuesForDuplicateKeys(userExtensions, tempUXs);
        userExtensions.putAll(tempUXs);
      }
      if (obj.isArray()) {
        final Map<String, Object> tempUXs = getAllInnerUserExtension((ArrayNode) arr.get(i), path);
        aggregateValuesForDuplicateKeys(userExtensions, tempUXs);
        userExtensions.putAll(tempUXs);
      }
    }
    return userExtensions;
  }

  private static void aggregateValuesForDuplicateKeys(Map<String, Object> userExtensions, Map<String, Object> tempUXs) {
    tempUXs.keySet().stream().filter(userExtensions::containsKey).forEach(k -> {
      final List v = new ArrayList();
      if (userExtensions.get(k) instanceof Collection) {
        v.addAll((Collection) userExtensions.get(k));
      } else {
        v.add(userExtensions.get(k));
      }
      if (tempUXs.get(k) instanceof Collection) {
        v.addAll((Collection) tempUXs.get(k));
      } else {
        v.add(tempUXs.get(k));
      }
      tempUXs.put(k, v);
    });
  }

  private static Map<String, Object> getAllInnerUserExtension(final ObjectNode json, Map<String, Object> userExtensions, String path) {
    for (String key : Streams.stream(json.fieldNames()).toList()) {
      final JsonNode obj = json.get(key.substring(key.lastIndexOf(".") + 1));
      if (obj.isObject()) {
        final Map<String, Object> tempUXs = getAllInnerUserExtension((ObjectNode) json.get(key), path + "." + key);
        aggregateValuesForDuplicateKeys(userExtensions, tempUXs);
        userExtensions.putAll(tempUXs);
      }
      if (obj.isArray()) {
        final Map<String, Object> tempUXs = getAllInnerUserExtension((ArrayNode) json.get(key), path + "." + key);
        aggregateValuesForDuplicateKeys(userExtensions, tempUXs);
        userExtensions.putAll(tempUXs);
      }
    }
    userExtensions.putAll(getUserExtensions(json, path));
    return userExtensions;
  }

  public static Map<String, Object> getUserExtensions(final JsonNode jsonObject, String path, Map<String, Object> contextAsMap, Set<UserExtensionField> newUserExtensionFields) {
    if (jsonObject.isArray()) {
      return getUserExtensions((ArrayNode) jsonObject, path, contextAsMap, newUserExtensionFields);
    } else if (jsonObject.isObject()) {
      return getUserExtensions((ObjectNode) jsonObject, path, contextAsMap, newUserExtensionFields);
    }
    return Collections.emptyMap();
  }

  public static Map<String, Object> getUserExtensions(final ObjectNode jsonObject, String path, Map<String, Object> contextAsMap, Set<UserExtensionField> newUserExtensionFields) {
    final Map<String, Object> userExtensions = objectMapper.convertValue(jsonObject, new TypeReference<Map<String, Object>>() {
    }).entrySet().stream().filter(e -> e.getKey().contains(Constants.JSON_LD_USER_EXTENSION_FIELD_SEPARATOR)).collect(Collectors.toMap(e -> path + "." + (e.getKey()), Map.Entry::getValue));
    if (contextAsMap != null) {
      EventConvertor.mapUserExtensionToDataType(contextAsMap, userExtensions, newUserExtensionFields);
    }
    return userExtensions;
  }

  public static Map<String, Object> getUserExtensions(final ObjectNode jsonObject, String path) {
    return objectMapper.convertValue(jsonObject, new TypeReference<Map<String, Object>>() {
    }).entrySet().stream().filter(e -> e.getKey().contains(Constants.JSON_LD_USER_EXTENSION_FIELD_SEPARATOR)).collect(Collectors.toMap(e -> path + "." + (e.getKey()), Map.Entry::getValue));
  }

  public static Map<String, Object> getUserExtensions(final ObjectNode jsonObject) {
    return objectMapper.convertValue(jsonObject, new TypeReference<Map<String, Object>>() {
    }).entrySet().stream().filter(e -> e.getKey().contains(Constants.JSON_LD_USER_EXTENSION_FIELD_SEPARATOR)).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  public static void extractUserExtensionFromReadPoint(final ObjectNode individualEvent, final EPCISEvent e, Map<String, Object> contextAsMap, Set<UserExtensionField> newUserExtensionFields) {
    String path = "readPoint";
    final ObjectNode readPoint = (ObjectNode) individualEvent.get(Constants.READ_POINT);
    Map<String, Object> userExtensions = getUserExtensions(readPoint, path, contextAsMap, newUserExtensionFields);
    e.getReadPoint().setUserExtensions(userExtensions);
    if (MapUtils.isNotEmpty(e.getReadPoint().getUserExtensions())) {
      userExtensions = getInnerUserExtensions(e.getReadPoint().getUserExtensions(), readPoint, new HashMap<>(), path, contextAsMap, newUserExtensionFields);
      e.getReadPoint().setInnerUserExtensions(userExtensions);
    }
  }

  public static void extractUserExtensionFromBizLocation(final ObjectNode individualEvent, final EPCISEvent e, Map<String, Object> contextAsMap, Set<UserExtensionField> newUserExtensionFields) {
    String path = "bizLocation";
    final ObjectNode bizLocation = (ObjectNode) individualEvent.get(Constants.BIZ_LOCATION);
    Map<String, Object> userExtensions = getUserExtensions(bizLocation, path, contextAsMap, newUserExtensionFields);
    e.getBizLocation().setUserExtensions(userExtensions);
    if (MapUtils.isNotEmpty(e.getBizLocation().getUserExtensions())) {
      userExtensions = getInnerUserExtensions(e.getBizLocation().getUserExtensions(), bizLocation, new HashMap<>(), path, contextAsMap, newUserExtensionFields);
      e.getBizLocation().setInnerUserExtensions(userExtensions);
    }
  }

  public static void extractUserExtensionFromErrorDeclaration(final ObjectNode individualEvent, final EPCISEvent e, Map<String, Object> contextAsMap, Set<UserExtensionField> newUserExtensionFields) {
    String path = "errorDeclaration";
    final ObjectNode errorDeclaration = (ObjectNode) individualEvent.get(Constants.ERROR_DECLARATION);
    Map<String, Object> userExtensions = getUserExtensions(errorDeclaration, path, contextAsMap, newUserExtensionFields);
    if (MapUtils.isNotEmpty(userExtensions)) {
      e.getErrorDeclaration().setUserExtensions(userExtensions);
      userExtensions = getInnerUserExtensions(e.getErrorDeclaration().getUserExtensions(), errorDeclaration, new HashMap<>(), path, contextAsMap, newUserExtensionFields);
      e.getErrorDeclaration().setInnerUserExtensions(userExtensions);
    }
  }

  // Method to check for ILMD from ObjectEvent & TransformationEvent and set the respective
  // userExtension and inner userExtensions
  public static void extractUserExtensionFromIlmd(final ObjectNode individualEvent, final EPCISEvent epcisEvent, final Map<String, Object> contextAsMap, final Set<UserExtensionField> newUserExtensionFields) {
    final ObjectNode ilmd = (ObjectNode) individualEvent.get(Constants.ILMD);
    Map<String, Object> userExtensions = getUserExtensions(ilmd, Constants.ILMD, contextAsMap, newUserExtensionFields);
    if (epcisEvent instanceof ObjectEvent objectEvent && MapUtils.isNotEmpty(userExtensions)) {
      if (objectEvent.getIlmd() == null) {
        objectEvent.setIlmd(new Ilmd());
      }
      objectEvent.getIlmd().setUserExtensions(userExtensions);
      userExtensions = getInnerUserExtensions(objectEvent.getIlmd().getUserExtensions(), ilmd, new HashMap<>(), Constants.ILMD, contextAsMap, newUserExtensionFields);
      objectEvent.getIlmd().setInnerUserExtensions(userExtensions);
    } else if (epcisEvent instanceof TransformationEvent transformationEvent && MapUtils.isNotEmpty(userExtensions)) {
      if (transformationEvent.getIlmd() == null) {
        transformationEvent.setIlmd(new Ilmd());
      }
      transformationEvent.getIlmd().setUserExtensions(userExtensions);
      userExtensions = getInnerUserExtensions(transformationEvent.getIlmd().getUserExtensions(), ilmd, new HashMap<>(), Constants.ILMD, contextAsMap, newUserExtensionFields);
      transformationEvent.getIlmd().setInnerUserExtensions(userExtensions);
    }
  }
}
