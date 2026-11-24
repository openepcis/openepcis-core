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
package io.openepcis.validation.service;

import static io.openepcis.model.epcis.constants.EpcisEventFieldConstants.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openepcis.model.epcis.exception.ExceptionMessages;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAccessor;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.collections4.CollectionUtils;

@ApplicationScoped
public final class SensorEventValidator {
  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SensorEventValidator.class);

  private SensorEventValidator() {
  }

  public static Set<String> validate(final ObjectNode sensorEvent) {
    return validateSensorElementList(sensorEvent);
  }

  private static Set<String> validateSensorElementList(final ObjectNode event) {
    final ArrayNode sensorElementList = (ArrayNode) event.get(SENSOR_ELEMENT_LIST);
    final Iterator<JsonNode> iterator = sensorElementList.elements();
    return Stream.generate(() -> null).takeWhile(x -> iterator.hasNext()).map(n -> iterator.next()).map(se -> validateSensorElement(se, ZonedDateTime.parse(event.get(EVENT_TIME).asText()))).filter(CollectionUtils::isNotEmpty).flatMap(Set::stream).collect(Collectors.toSet());
  }

  private static Set<String> validateSensorElement(final JsonNode sensorElement, TemporalAccessor eventTime) {
    final List<String> commonAttributes = List.of(TIME, DEVICE_ID, DEVICE_METADATA, DATA_PROCESSING_METHOD, RAW_DATA);
    final JsonNode sensorReportList = sensorElement.get(SENSOR_REPORT);
    final Set<String> validationErrors = new HashSet<>();
    if (sensorElement.has(SENSOR_METADATA)) {
      final JsonNode sensorMetaData = sensorElement.get(SENSOR_METADATA);
      /*
       The following inline attributes are permitted to appear both in a sensorMetaData as well as in a
       sensorReport element: dataProcessingMethod, deviceID, deviceMetaData, rawData, and time .
       If they are present in a sensorElement container, they SHALL either be specified in the
       sensorMetaData element or in the sensorReport element(s).
      */
      commonAttributes.forEach(attribute -> {
        if (sensorMetaData.has(attribute)) {
          sensorReportList.forEach(sensorReport -> validationErrors.addAll(validateSensorReportForCommonAttributes(sensorReport, Optional.of(attribute))));
        }
      });
      validationErrors.addAll(validateSensorMetaData(sensorMetaData, eventTime));
    }
    sensorReportList.forEach(sensorReport -> validationErrors.addAll(validateSensorReport(sensorReport)));
    return validationErrors;
  }

  private static Set<String> validateSensorReport(final JsonNode sensorReport) {
    final Set<String> exceptions = new HashSet<>();
    // If microorganism is present, a sensorReport element SHALL NOT include chemicalSubstance and
    // vice-versa.
    if (sensorReport.has(MICROORGANISM) && sensorReport.has(CHEMICAL_SUBSTANCE)) exceptions.add(ExceptionMessages.IF_MICROORGANISM_IS_PRESENT_SENSORREPORT_ELEMENT_SHALL_NOT_INCLUDE_CHEMICALSUBSTANCE);
    if ((sensorReport.has(MICROORGANISM) || sensorReport.has(CHEMICAL_SUBSTANCE)) && sensorReport.has(VALUE) && sensorReport.has(BOOLEAN_VALUE)) {
      final boolean boolValue = sensorReport.get(BOOLEAN_VALUE).asBoolean();
      final double value = sensorReport.get(VALUE).asDouble();
      if (value > 0 && !boolValue) {
        exceptions.add(ExceptionMessages.NON_ZERO_CONCENTRATION_VALUE_IS_INCOMPATIBLE_WITH_BOOLEANVALUE_FALSE_IN_SENSOR_REPORT);
      }
    }
    // percRank SHALL only be used in conjunction with the field percValue and vice-versa.
    if (sensorReport.has(PERC_VALUE) != sensorReport.has(PERC_RANK)) exceptions.add(ExceptionMessages.PERCRANK_SHALL_ONLY_BE_USED_IN_CONJUNCTION_WITH_FIELD_PERCVALUE);
    // sDev SHALL only be used in conjunction with the field meanValue.
    if (sensorReport.has(SDEV) && !(sensorReport.has(MEAN_VALUE))) exceptions.add(ExceptionMessages.SDEV_SHALL_ONLY_BE_USED_IN_CONJUNCTION_WITH_FIELD_MEANVALUE);
    if (List.of(VALUE, MIN_VALUE, MAX_VALUE, MEAN_VALUE, PERC_VALUE).stream().noneMatch(sensorReport::has)) {
      // If there is no value, minValue, maxValue, averageValue or percValue field present, uom
      // SHALL be omitted.
      if (sensorReport.has(UOM)) exceptions.add(ExceptionMessages.IF_THERE_IS_NO_VALUE_MINVALUE_MAXVALUE_AVERAGEVALUE_OR_PERCVALUE_FIELD_PRESENT_UOM_SHALL_BE_OMITTED);
      // If a component field is present, a sensorReport element MUST also include a float value and
      // the uom field.
      // float value can be any one of no value, minValue, maxValue, averageValue or percValue
      if (sensorReport.has(COMPONENT)) exceptions.add(ExceptionMessages.IF_COMPONENT_FIELD_IS_PRESENT_SENSORREPORT_ELEMENT_MUST_ALSO_INCLUDE_FLOAT_VALUE_AND_UOM);
    } else if (sensorReport.has(COMPONENT) && !(sensorReport.has(UOM))) exceptions.add(ExceptionMessages.IF_COMPONENT_FIELD_IS_PRESENT_SENSORREPORT_ELEMENT_MUST_ALSO_INCLUDE_FLOAT_VALUE_AND_UOM);
    return exceptions;
  }

  private static Set<String> validateSensorMetaData(final JsonNode sensorMetaData, final TemporalAccessor eventTimeObject) {
    final List<String> dateAttributes = List.of(TIME, START_TIME, END_TIME);
    final Set<String> exceptions = new HashSet<>();
    final ZonedDateTime eventTime = ZonedDateTime.from(JsonNode.class.isAssignableFrom(eventTimeObject.getClass()) ? ZonedDateTime.parse(((JsonNode) eventTimeObject).textValue()) : eventTimeObject);
    dateAttributes.forEach(attribute -> {
      if (sensorMetaData.has(attribute)) {
        // If startTime present, the startTime SHALL be less  than the eventTime value and  the
        // endTime value.
        if (attribute.equals(START_TIME)) {
          if (ZonedDateTime.parse(sensorMetaData.get(attribute).textValue()).compareTo(eventTime) >= 0) exceptions.add(ExceptionMessages.STARTTIME_SHALL_BE_LESS_THAN_EVENTTIME);
          if (ZonedDateTime.parse(sensorMetaData.get(attribute).textValue()).compareTo(ZonedDateTime.parse(sensorMetaData.get(END_TIME).textValue())) >= 0) exceptions.add(ExceptionMessages.STARTTIME_SHALL_BE_LESS_THAN_ENDTIME);
        } else 
        // If endTime  and time present, it SHALL be less than or equal to the eventTime value
        if (ZonedDateTime.parse(sensorMetaData.get(attribute).textValue()).compareTo(eventTime) > 0) exceptions.add(String.format(ExceptionMessages.TIME_VALUE_SHALL_BE_LESS_THAN_OR_EQUAL_TO_EVENTTIME_VALUE, attribute));
      }
    });
    return exceptions;
  }

  private static Set<String> validateSensorReportForCommonAttributes(final JsonNode sensorReport, final Optional<String> attribute) {
    if (attribute.isPresent() && sensorReport.has(attribute.get())) {
      return Set.of(String.format(ExceptionMessages.IN_SENSORELEMENT_CONTAINER_ATTRIBUTE_SHALL_EITHER_BE_SPECIFIED_IN_SENSORMETADATA_ELEMENT_OR_SENSORREPORT_ELEMENT, attribute.get()));
    }
    return Set.of();
  }
}
