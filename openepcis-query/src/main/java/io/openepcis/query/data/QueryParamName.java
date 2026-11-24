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
import io.openepcis.model.epcis.exception.UnsupportedQueryParameterException;
import io.openepcis.query.util.DateUtility;
import java.text.ParseException;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Date;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;

public enum QueryParamName {
  defaultGroup(
      OperatorType.EQ,
      ValueType.STRING,
      Map.of("metadata.capturedBy.defaultGroup", ""),
      false,
      false,
      false,
      true,
      "metadata"),
  visible(
      OperatorType.EQ,
      ValueType.BOOLEAN,
      Map.of("metadata.visible", ""),
      false,
      false,
      false,
      true,
      "metadata"),
  eventType(
      OperatorType.EQ, ValueType.STRING_LIST, Map.of("type", ""), false, false, false, false, ""),
  GE_eventTime(
      OperatorType.GE,
      ValueType.DATE_TIME,
      Map.of("eventTime", ""),
      false,
      false,
      false,
      false,
      ""),
  LT_eventTime(
      OperatorType.LT,
      ValueType.DATE_TIME,
      Map.of("eventTime", ""),
      false,
      false,
      false,
      false,
      ""),
  GE_recordTime(
      OperatorType.GE,
      ValueType.DATE_TIME,
      Map.of("recordTime", ""),
      false,
      false,
      false,
      false,
      ""),
  GE_recordTimeInternal(
      OperatorType.GE,
      ValueType.DATE_TIME,
      Map.of("recordTime", ""),
      false,
      false,
      false,
      false,
      ""),
  LT_recordTime(
      OperatorType.LT,
      ValueType.DATE_TIME,
      Map.of("recordTime", ""),
      false,
      false,
      false,
      false,
      ""),
  EQ_action(
      OperatorType.EQ, ValueType.STRING_LIST, Map.of("action", ""), false, false, false, false, ""),
  EQ_bizStep(
      OperatorType.EQ,
      ValueType.STRING_LIST,
      Map.of("bizStep.asURN", "", "bizStep.asURI", "", "bizStep.asCaptured", ""),
      true,
      "urn:epcglobal:cbv:bizstep:",
      false,
      false,
      false,
      ""),
  EQ_disposition(
      OperatorType.EQ,
      ValueType.STRING_LIST,
      Map.of("disposition.asURN", "", "disposition.asURI", "", "disposition.asCaptured", ""),
      true,
      "urn:epcglobal:cbv:disp:",
      false,
      false,
      false,
      ""),
  EQ_persistentDisposition_set(
      OperatorType.EQ,
      ValueType.STRING_LIST,
      Map.of(
          "persistentDisposition.set.asURN",
          "",
          "persistentDisposition.set.asURI",
          "",
          "persistentDisposition.set.asCaptured",
          ""),
      true,
      "urn:epcglobal:cbv:disp:",
      false,
      false,
      false,
      ""),
  EQ_persistentDisposition_unset(
      OperatorType.EQ,
      ValueType.STRING_LIST,
      Map.of(
          "persistentDisposition.unset.asURN",
          "",
          "persistentDisposition.unset.asURI",
          "",
          "persistentDisposition.unset.asCaptured",
          ""),
      true,
      "urn:epcglobal:cbv:disp:",
      false,
      false,
      false,
      ""),
  EQ_readPoint(
      OperatorType.EQ,
      ValueType.STRING_LIST,
      Map.of("readPoint.id.asCaptured", "", "readPoint.id.asURN", "", "readPoint.id.asURI", ""),
      false,
      false,
      false,
      false,
      ""),
  WD_readPoint(
      OperatorType.WD,
      ValueType.STRING_LIST,
      Map.of("readPoint.id", ""),
      false,
      false,
      false,
      false,
      ""),
  EQ_bizLocation(
      OperatorType.EQ,
      ValueType.STRING_LIST,
      Map.of(
          "bizLocation.id.asCaptured", "", "bizLocation.id.asURN", "", "bizLocation.id.asURI", ""),
      false,
      false,
      false,
      false,
      ""),
  WD_bizLocation(
      OperatorType.WD,
      ValueType.STRING_LIST,
      Map.of("bizLocation.id", ""),
      false,
      false,
      false,
      false,
      ""),
  EQ_bizTransaction_type(
      OperatorType.EQ,
      ValueType.STRING_LIST,
      Map.of(
          "bizTransactionList.type.asURN",
          "bizTransactionList",
          "bizTransactionList.type.asURI",
          "bizTransactionList",
          "bizTransactionList.type.asCaptured",
          "bizTransactionList",
          "bizTransactionList.bizTransaction",
          "bizTransactionList"),
      true,
      "urn:epcglobal:cbv:btt:",
      true,
      "^EQ_bizTransaction_[a-zA-Z][a-zA-Z0-9]*$",
      false,
      false,
      ""),
  EQ_source_type(
      OperatorType.EQ,
      ValueType.STRING_LIST,
      Map.of(
          "sourceList.type.asURN",
          "sourceList",
          "sourceList.type.asURI",
          "sourceList",
          "sourceList.type.asCaptured",
          "sourceList",
          "sourceList.source.asURN",
          "sourceList",
          "sourceList.source.canonicalDL",
          "sourceList",
          "sourceList.source.asCaptured",
          "sourceList"),
      true,
      "urn:epcglobal:cbv:sdt:",
      true,
      "^EQ_source_[a-zA-Z][a-zA-Z0-9]*$",
      false,
      false,
      ""),
  EQ_destination_type(
      OperatorType.EQ,
      ValueType.STRING_LIST,
      Map.of(
          "destinationList.type.asURN",
          "destinationList",
          "destinationList.type.asURI",
          "destinationList",
          "destinationList.type.asCaptured",
          "destinationList",
          "destinationList.destination.asURN",
          "destinationList",
          "destinationList.destination.canonicalDL",
          "destinationList",
          "destinationList.destination.asCaptured",
          "destinationList"),
      true,
      "urn:epcglobal:cbv:sdt:",
      true,
      "^EQ_destination_[a-zA-Z][a-zA-Z0-9]*$",
      false,
      false,
      ""),
  EQ_transformationID(
      OperatorType.EQ,
      ValueType.STRING_LIST,
      Map.of("transformationID", ""),
      false,
      false,
      false,
      false,
      ""),
  MATCH_epc(
      OperatorType.MATCH,
      ValueType.STRING_LIST,
      Map.of(
          "epcList.asURN",
          "",
          "childEPCs.asURN",
          "",
          "epcList.asURI",
          "",
          "childEPCs.asURI",
          "",
          "epcList.asCaptured",
          "",
          "childEPCs.asCaptured",
          ""),
      false,
      false,
      false,
      false,
      ""),
  MATCH_parentID(
      OperatorType.MATCH,
      ValueType.STRING_LIST,
      Map.of("parentID.asURN", "", "parentID.asURI", "", "parentID.asCaptured", ""),
      false,
      false,
      false,
      false,
      ""),
  MATCH_inputEPC(
      OperatorType.MATCH,
      ValueType.STRING_LIST,
      Map.of("inputEPCList.asURN", "", "inputEPCList.asURI", "", "inputEPCList.asCaptured", ""),
      false,
      false,
      false,
      false,
      ""),
  MATCH_outputEPC(
      OperatorType.MATCH,
      ValueType.STRING_LIST,
      Map.of("outputEPCList.asURN", "", "outputEPCList.asURI", "", "outputEPCList.asCaptured", ""),
      false,
      false,
      false,
      false,
      ""),

  EPC(
      OperatorType.MATCH,
      ValueType.STRING_LIST,
      Map.ofEntries(
          Map.entry("epcList.asURN", ""),
          Map.entry("childEPCs.asURN", ""),
          Map.entry("parentID.asURN", ""),
          Map.entry("inputEPCList.asURN", ""),
          Map.entry("outputEPCList.asURN", ""),
          Map.entry("epcList.asURI", ""),
          Map.entry("childEPCs.asURI", ""),
          Map.entry("parentID.asURI", ""),
          Map.entry("inputEPCList.asURI", ""),
          Map.entry("outputEPCList.asURI", ""),
          Map.entry("epcList.asCaptured", ""),
          Map.entry("childEPCs.asCaptured", ""),
          Map.entry("parentID.asCaptured", ""),
          Map.entry("inputEPCList.asCaptured", ""),
          Map.entry("outputEPCList.asCaptured", ""),
          Map.entry("epcClass.asURN", ""),
          Map.entry("quantityList.epcClass.asURN", "quantityList"),
          Map.entry("childQuantityList.epcClass.asURN", "childQuantityList"),
          Map.entry("epcClass.asURI", ""),
          Map.entry("quantityList.epcClass.asURI", "quantityList"),
          Map.entry("childQuantityList.epcClass.asURI", "childQuantityList"),
          Map.entry("epcClass.asCaptured", ""),
          Map.entry("quantityList.epcClass.asCaptured", "quantityList"),
          Map.entry("childQuantityList.epcClass.asCaptured", "childQuantityList"),
          // a lot appears in a TransformationEvent only through its input and output quantities;
          // without these GET /epcs/{lot}/events found nothing for a lot MATCH_anyEPCClass finds
          Map.entry("inputQuantityList.epcClass.asURN", "inputQuantityList"),
          Map.entry("outputQuantityList.epcClass.asURN", "outputQuantityList"),
          Map.entry("inputQuantityList.epcClass.asURI", "inputQuantityList"),
          Map.entry("outputQuantityList.epcClass.asURI", "outputQuantityList"),
          Map.entry("inputQuantityList.epcClass.asCaptured", "inputQuantityList"),
          Map.entry("outputQuantityList.epcClass.asCaptured", "outputQuantityList")),
      false,
      false,
      false,
      false,
      ""),
  MATCH_anyEPC(
      OperatorType.MATCH,
      ValueType.STRING_LIST,
      Map.ofEntries(
          // Per EPCIS 2.0 §8.2.7.6.4, MATCH_anyEPC matches parentID, epcList,
          // childEPCs, inputEPCList, AND outputEPCList. The latter two are
          // required to surface TransformationEvents in any-EPC searches.
          Map.entry("parentID.asURN", ""),
          Map.entry("parentID.asURI", ""),
          Map.entry("parentID.asCaptured", ""),
          Map.entry("epcList.asURN", ""),
          Map.entry("epcList.asURI", ""),
          Map.entry("epcList.asCaptured", ""),
          Map.entry("childEPCs.asURN", ""),
          Map.entry("childEPCs.asURI", ""),
          Map.entry("childEPCs.asCaptured", ""),
          Map.entry("inputEPCList.asURN", ""),
          Map.entry("inputEPCList.asURI", ""),
          Map.entry("inputEPCList.asCaptured", ""),
          Map.entry("outputEPCList.asURN", ""),
          Map.entry("outputEPCList.asURI", ""),
          Map.entry("outputEPCList.asCaptured", "")),
      false,
      false,
      false,
      false,
      ""),
  MATCH_epcClass(
      OperatorType.MATCH,
      ValueType.STRING_LIST,
      Map.of(
          "quantityList.epcClass.asURN",
          "quantityList",
          "childQuantityList.epcClass.asURN",
          "childQuantityList",
          "quantityList.epcClass.asURI",
          "quantityList",
          "childQuantityList.epcClass.asURI",
          "childQuantityList",
          "quantityList.epcClass.asCaptured",
          "quantityList",
          "childQuantityList.epcClass.asCaptured",
          "childQuantityList"),
      false,
      false,
      false,
      false,
      ""),
  MATCH_inputEPCClass(
      OperatorType.MATCH,
      ValueType.STRING_LIST,
      Map.of(
          "inputQuantityList.epcClass.asURN",
          "inputQuantityList",
          "inputQuantityList.epcClass.asURI",
          "inputQuantityList",
          "inputQuantityList.epcClass.asCaptured",
          "inputQuantityList"),
      false,
      false,
      false,
      false,
      ""),
  MATCH_outputEPCClass(
      OperatorType.MATCH,
      ValueType.STRING_LIST,
      Map.of(
          "outputQuantityList.epcClass.asURN",
          "outputQuantityList",
          "outputQuantityList.epcClass.asURI",
          "outputQuantityList",
          "outputQuantityList.epcClass.asCaptured",
          "outputQuantityList"),
      false,
      false,
      false,
      false,
      ""),
  MATCH_anyEPCClass(
      OperatorType.MATCH,
      ValueType.STRING_LIST,
      Map.ofEntries(
          Map.entry("quantityList.epcClass.asURN", "quantityList"),
          Map.entry("childQuantityList.epcClass.asURN", "childQuantityList"),
          Map.entry("inputQuantityList.epcClass.asURN", "inputQuantityList"),
          Map.entry("outputQuantityList.epcClass.asURN", "outputQuantityList"),
          Map.entry("quantityList.epcClass.asURI", "quantityList"),
          Map.entry("childQuantityList.epcClass.asURI", "childQuantityList"),
          Map.entry("inputQuantityList.epcClass.asURI", "inputQuantityList"),
          Map.entry("outputQuantityList.epcClass.asURI", "outputQuantityList"),
          Map.entry("quantityList.epcClass.asCaptured", "quantityList"),
          Map.entry("childQuantityList.epcClass.asCaptured", "childQuantityList"),
          Map.entry("inputQuantityList.epcClass.asCaptured", "inputQuantityList"),
          Map.entry("outputQuantityList.epcClass.asCaptured", "outputQuantityList")),
      false,
      false,
      false,
      false,
      ""),
  EQ_quantity(
      OperatorType.EQ,
      ValueType.LONG,
      Map.of("quantityList.quantity", "quantityList"),
      false,
      false,
      false,
      false,
      ""),
  EQ_quantity_uom(
      OperatorType.EQ,
      ValueType.PAIR,
      Map.of("quantityList.uom", "quantityList.quantity"),
      false,
      true,
      "^EQ_quantity_[A-Z0-9]{2,4}$",
      false,
      false,
      ""),
  GT_quantity(
      OperatorType.GT,
      ValueType.LONG,
      Map.of("quantityList.quantity", "quantityList"),
      false,
      false,
      false,
      false,
      ""),
  GT_quantity_uom(
      OperatorType.GT,
      ValueType.PAIR,
      Map.of("quantityList.uom", "quantityList.quantity"),
      false,
      true,
      "^GT_quantity_[A-Z0-9]{2,4}$",
      false,
      false,
      ""),
  GE_quantity(
      OperatorType.GE,
      ValueType.LONG,
      Map.of("quantityList.quantity", "quantityList"),
      false,
      false,
      false,
      false,
      ""),
  GE_quantity_uom(
      OperatorType.GE,
      ValueType.PAIR,
      Map.of("quantityList.uom", "quantityList.quantity"),
      false,
      true,
      "^GE_quantity_[A-Z0-9]{2,4}$",
      false,
      false,
      ""),
  LT_quantity(
      OperatorType.LT,
      ValueType.LONG,
      Map.of("quantityList.quantity", "quantityList"),
      false,
      false,
      false,
      false,
      ""),
  LT_quantity_uom(
      OperatorType.LT,
      ValueType.PAIR,
      Map.of("quantityList.uom", "quantityList.quantity"),
      false,
      true,
      "^LT_quantity_[A-Z0-9]{2,4}$",
      false,
      false,
      ""),
  LE_quantity(
      OperatorType.LE,
      ValueType.LONG,
      Map.of("quantityList.quantity", "quantityList"),
      false,
      false,
      false,
      false,
      ""),
  LE_quantity_uom(
      OperatorType.LE,
      ValueType.PAIR,
      Map.of("quantityList.uom", "quantityList.quantity"),
      false,
      true,
      "^LE_quantity_[A-Z0-9]{2,4}$",
      false,
      false,
      ""),
  EQ_eventID(
      OperatorType.EQ,
      ValueType.STRING_LIST,
      Map.of("eventID", ""),
      false,
      false,
      false,
      false,
      ""),
  // errorDeclaration is mapped nested, so the exists check has to run inside it, on a field every
  // declaration carries (declarationTime is required). A root-level exists on the nested path
  // matches nothing.
  EXISTS_errorDeclaration(
      OperatorType.EXISTS,
      ValueType.LONG,
      Map.of("errorDeclaration.declarationTime", "errorDeclaration"),
      false,
      false,
      false,
      false,
      ""),
  GE_errorDeclaration_Time(
      OperatorType.GE,
      ValueType.DATE_TIME,
      Map.of("errorDeclaration.declarationTime", "errorDeclaration"),
      false,
      false,
      false,
      false,
      ""),
  LT_errorDeclaration_Time(
      OperatorType.LT,
      ValueType.DATE_TIME,
      Map.of("errorDeclaration.declarationTime", "errorDeclaration"),
      false,
      false,
      false,
      false,
      ""),
  EQ_errorReason(
      OperatorType.EQ,
      ValueType.STRING_LIST,
      Map.of(
          "errorDeclaration.reason.asURN",
          "errorDeclaration",
          "errorDeclaration.reason.asURI",
          "errorDeclaration",
          "errorDeclaration.reason.asCaptured",
          "errorDeclaration"),
      false,
      false,
      false,
      false,
      ""),
  EQ_correctiveEventID(
      OperatorType.EQ,
      ValueType.STRING_LIST,
      Map.of("errorDeclaration.correctiveEventIDs", "errorDeclaration"),
      false,
      false,
      false,
      false,
      ""),
  perPage(ValueType.STRING, false, false, false, false, ""),
  orderBy(ValueType.STRING, false, false, false, false, ""),
  orderDirection(ValueType.STRING, false, false, false, false, ""),
  eventCountLimit(ValueType.LONG, false, false, false, false, ""),
  maxEventCount(ValueType.LONG, false, false, false, false, ""),
  EXISTS_sensorElementList(
      OperatorType.EXISTS,
      ValueType.LONG,
      Map.of("sensorElementList.sensorReport", "sensorElementList.sensorReport"),
      false,
      false,
      false,
      false,
      ""),
  GE_time(
      OperatorType.GE,
      ValueType.DATE_TIME,
      Map.of(
          "sensorElementList.sensorReport.time",
          "sensorElementList.sensorReport",
          "sensorElementList.sensorMetadata.time",
          "sensorElementList"),
      false,
      false,
      false,
      false,
      ""),
  LT_time(
      OperatorType.LT,
      ValueType.DATE_TIME,
      Map.of(
          "sensorElementList.sensorReport.time",
          "sensorElementList.sensorReport",
          "sensorElementList.sensorMetadata.time",
          "sensorElementList"),
      false,
      false,
      false,
      false,
      ""),
  GE_startTime(
      OperatorType.GE,
      ValueType.DATE_TIME,
      Map.of("sensorElementList.sensorMetadata.startTime", "sensorElementList"),
      false,
      false,
      false,
      false,
      ""),
  LT_startTime(
      OperatorType.LT,
      ValueType.DATE_TIME,
      Map.of("sensorElementList.sensorMetadata.startTime", "sensorElementList"),
      false,
      false,
      false,
      false,
      ""),
  GE_endTime(
      OperatorType.GE,
      ValueType.DATE_TIME,
      Map.of("sensorElementList.sensorMetadata.endTime", "sensorElementList"),
      false,
      false,
      false,
      false,
      ""),
  LT_endTime(
      OperatorType.LT,
      ValueType.DATE_TIME,
      Map.of("sensorElementList.sensorMetadata.endTime", "sensorElementList"),
      false,
      false,
      false,
      false,
      ""),
  EQ_type(
      OperatorType.EQ,
      ValueType.STRING_LIST,
      Map.of(
          "sensorElementList.sensorReport.type.asURN",
          "sensorElementList.sensorReport",
          "sensorElementList.sensorReport.type.asURI",
          "sensorElementList.sensorReport",
          "sensorElementList.sensorReport.type.asCaptured",
          "sensorElementList.sensorReport"),
      false,
      false,
      false,
      false,
      ""),
  EQ_deviceID(
      OperatorType.EQ,
      ValueType.STRING_LIST,
      Map.of(
          "sensorElementList.sensorReport.deviceID.asURN",
          "sensorElementList.sensorReport",
          "sensorElementList.sensorMetadata.deviceID.asURN",
          "sensorElementList",
          "sensorElementList.sensorReport.deviceID.asURI",
          "sensorElementList.sensorReport",
          "sensorElementList.sensorMetadata.deviceID.asURI",
          "sensorElementList",
          "sensorElementList.sensorReport.deviceID.asCaptured",
          "sensorElementList.sensorReport",
          "sensorElementList.sensorMetadata.deviceID.asCaptured",
          "sensorElementList"),
      false,
      false,
      false,
      false,
      ""),
  EQ_dataProcessingMethod(
      OperatorType.EQ,
      ValueType.STRING_LIST,
      Map.of(
          "sensorElementList.sensorReport.dataProcessingMethod",
          "sensorElementList.sensorReport",
          "sensorElementList.sensorMetadata.dataProcessingMethod",
          "sensorElementList"),
      false,
      false,
      false,
      false,
      ""),
  EQ_microorganism(
      OperatorType.EQ,
      ValueType.STRING_LIST,
      Map.of("sensorElementList.sensorReport.microorganism", "sensorElementList.sensorReport"),
      false,
      false,
      false,
      false,
      ""),
  EQ_chemicalSubstance(
      OperatorType.EQ,
      ValueType.STRING_LIST,
      Map.of("sensorElementList.sensorReport.chemicalSubstance", "sensorElementList.sensorReport"),
      false,
      false,
      false,
      false,
      ""),
  EQ_bizRules(
      OperatorType.EQ,
      ValueType.STRING_LIST,
      Map.of("sensorElementList.sensorMetadata.bizRules", "sensorElementList"),
      false,
      false,
      false,
      false,
      ""),
  EQ_value_uom(
      OperatorType.EQ,
      ValueType.PAIR,
      Map.of("sensorElementList.sensorReport.uom", "sensorElementList.sensorReport.value"),
      false,
      true,
      "^EQ_value_[A-Z0-9]{2,4}$",
      false,
      false,
      ""),
  GT_value_uom(
      OperatorType.GT,
      ValueType.PAIR,
      Map.of("sensorElementList.sensorReport.uom", "sensorElementList.sensorReport.value"),
      false,
      true,
      "^GT_value_[A-Z0-9]{2,4}$",
      false,
      false,
      ""),
  LE_value_uom(
      OperatorType.LE,
      ValueType.PAIR,
      Map.of("sensorElementList.sensorReport.uom", "sensorElementList.sensorReport.value"),
      false,
      true,
      "^LE_value_[A-Z0-9]{2,4}$",
      false,
      false,
      ""),
  GE_value_uom(
      OperatorType.GE,
      ValueType.PAIR,
      Map.of("sensorElementList.sensorReport.uom", "sensorElementList.sensorReport.value"),
      false,
      true,
      "GE_value_[A-Z0-9]{2,4}$",
      false,
      false,
      ""),
  LT_value_uom(
      OperatorType.LT,
      ValueType.PAIR,
      Map.of("sensorElementList.sensorReport.uom", "sensorElementList.sensorReport.value"),
      false,
      true,
      "LT_value_[A-Z0-9]{2,4}$",
      false,
      false,
      ""),
  GT_minValue_uom(
      OperatorType.GT,
      ValueType.PAIR,
      Map.of("sensorElementList.sensorReport.uom", "sensorElementList.sensorReport.minValue"),
      false,
      true,
      "GT_minValue_[A-Z0-9]{2,4}$",
      false,
      false,
      ""),
  LE_minValue_uom(
      OperatorType.LE,
      ValueType.PAIR,
      Map.of("sensorElementList.sensorReport.uom", "sensorElementList.sensorReport.minValue"),
      false,
      true,
      "LE_minValue_[A-Z0-9]{2,4}$",
      false,
      false,
      ""),
  GE_minValue_uom(
      OperatorType.GE,
      ValueType.PAIR,
      Map.of("sensorElementList.sensorReport.uom", "sensorElementList.sensorReport.minValue"),
      false,
      true,
      "GE_minValue_[A-Z0-9]{2,4}$",
      false,
      false,
      ""),
  LT_minValue_uom(
      OperatorType.LT,
      ValueType.PAIR,
      Map.of("sensorElementList.sensorReport.uom", "sensorElementList.sensorReport.minValue"),
      false,
      true,
      "LT_minValue_[A-Z0-9]{2,4}$",
      false,
      false,
      ""),
  GT_maxValue_uom(
      OperatorType.GT,
      ValueType.PAIR,
      Map.of("sensorElementList.sensorReport.uom", "sensorElementList.sensorReport.maxValue"),
      false,
      true,
      "GT_maxValue_[A-Z0-9]{2,4}$",
      false,
      false,
      ""),
  LE_maxValue_uom(
      OperatorType.LE,
      ValueType.PAIR,
      Map.of("sensorElementList.sensorReport.uom", "sensorElementList.sensorReport.maxValue"),
      false,
      true,
      "LE_maxValue_[A-Z0-9]{2,4}$",
      false,
      false,
      ""),
  GE_maxValue_uom(
      OperatorType.GE,
      ValueType.PAIR,
      Map.of("sensorElementList.sensorReport.uom", "sensorElementList.sensorReport.maxValue"),
      false,
      true,
      "GE_maxValue_[A-Z0-9]{2,4}$",
      false,
      false,
      ""),
  LT_maxValue_uom(
      OperatorType.LT,
      ValueType.PAIR,
      Map.of("sensorElementList.sensorReport.uom", "sensorElementList.sensorReport.maxValue"),
      false,
      true,
      "LT_maxValue_[A-Z0-9]{2,4}$",
      false,
      false,
      ""),
  GT_meanValue_uom(
      OperatorType.GT,
      ValueType.PAIR,
      Map.of("sensorElementList.sensorReport.uom", "sensorElementList.sensorReport.meanValue"),
      false,
      true,
      "GT_meanValue_[A-Z0-9]{2,4}$",
      false,
      false,
      ""),
  LE_meanValue_uom(
      OperatorType.LE,
      ValueType.PAIR,
      Map.of("sensorElementList.sensorReport.uom", "sensorElementList.sensorReport.meanValue"),
      false,
      true,
      "LE_meanValue_[A-Z0-9]{2,4}$",
      false,
      false,
      ""),
  GE_meanValue_uom(
      OperatorType.GE,
      ValueType.PAIR,
      Map.of("sensorElementList.sensorReport.uom", "sensorElementList.sensorReport.meanValue"),
      false,
      true,
      "GE_meanValue_[A-Z0-9]{2,4}$",
      false,
      false,
      ""),
  LT_meanValue_uom(
      OperatorType.LT,
      ValueType.PAIR,
      Map.of("sensorElementList.sensorReport.uom", "sensorElementList.sensorReport.meanValue"),
      false,
      true,
      "LT_meanValue_[A-Z0-9]{2,4}$",
      false,
      false,
      ""),
  EQ_stringValue(
      OperatorType.EQ,
      ValueType.STRING_LIST,
      Map.of(
          "sensorElementList.sensorReport.stringValue.keyword", "sensorElementList.sensorReport"),
      false,
      false,
      false,
      false,
      ""),
  EQ_booleanValue(
      OperatorType.EQ,
      ValueType.STRING_LIST,
      Map.of("sensorElementList.sensorReport.booleanValue", "sensorElementList.sensorReport"),
      false,
      false,
      false,
      false,
      ""),
  EQ_hexBinaryValue(
      OperatorType.EQ,
      ValueType.STRING_LIST,
      Map.of("sensorElementList.sensorReport.hexBinaryValue", "sensorElementList.sensorReport"),
      false,
      false,
      false,
      false,
      ""),
  EQ_uriValue(
      OperatorType.EQ,
      ValueType.STRING_LIST,
      Map.of("sensorElementList.sensorReport.uriValue", "sensorElementList.sensorReport"),
      false,
      false,
      false,
      false,
      ""),
  GE_sDev(
      OperatorType.GE,
      ValueType.FLOAT,
      Map.of("sensorElementList.sensorReport.sDev", "sensorElementList.sensorReport"),
      false,
      false,
      false,
      false,
      ""),
  LT_sDev(
      OperatorType.LT,
      ValueType.FLOAT,
      Map.of("sensorElementList.sensorReport.sDev", "sensorElementList.sensorReport"),
      false,
      false,
      false,
      false,
      ""),
  GE_percRank(
      OperatorType.GE,
      ValueType.FLOAT,
      Map.of("sensorElementList.sensorReport.percRank", "sensorElementList.sensorReport"),
      false,
      false,
      false,
      false,
      ""),
  LT_percRank(
      OperatorType.LT,
      ValueType.FLOAT,
      Map.of("sensorElementList.sensorReport.percRank", "sensorElementList.sensorReport"),
      false,
      false,
      false,
      false,
      ""),
  GE_percValue(
      OperatorType.GE,
      ValueType.FLOAT,
      Map.of("sensorElementList.sensorReport.percValue", "sensorElementList.sensorReport"),
      false,
      false,
      false,
      false,
      ""),
  LT_percValue(
      OperatorType.LT,
      ValueType.FLOAT,
      Map.of("sensorElementList.sensorReport.percValue", "sensorElementList.sensorReport"),
      false,
      false,
      false,
      false,
      ""),

  EXISTS_SENSORELEMENT_fieldname(
      OperatorType.EXISTS,
      ValueType.PAIR,
      Map.of("sensorElementList.userExtensions.expandedKey", "sensorElementList.userExtensions"),
      false,
      true,
      "^EXISTS_SENSORELEMENT_[a-z][a-zA-Z0-9]*\\:\\w+$",
      true,
      false,
      ""),
  EQ_SENSORELEMENT_fieldname(
      OperatorType.EQ,
      ValueType.PAIR,
      Map.of(
          "sensorElementList.innerUserExtensions.expandedKey",
          "sensorElementList.innerUserExtensions."),
      false,
      true,
      "^EQ_SENSORELEMENT_[a-z][a-zA-Z0-9]*\\:\\w+$",
      true,
      false,
      ""),
  GT_SENSORELEMENT_fieldname(
      OperatorType.GT,
      ValueType.PAIR,
      Map.of(
          "sensorElementList.innerUserExtensions.expandedKey",
          "sensorElementList.innerUserExtensions."),
      false,
      true,
      "^GT_SENSORELEMENT_[a-z][a-zA-Z0-9]*\\:\\w+$",
      true,
      false,
      ""),
  GE_SENSORELEMENT_fieldname(
      OperatorType.GE,
      ValueType.PAIR,
      Map.of(
          "sensorElementList.innerUserExtensions.expandedKey",
          "sensorElementList.innerUserExtensions."),
      false,
      true,
      "^GE_SENSORELEMENT_[a-z][a-zA-Z0-9]*\\:\\w+$",
      true,
      false,
      ""),
  LT_SENSORELEMENT_fieldname(
      OperatorType.LT,
      ValueType.PAIR,
      Map.of(
          "sensorElementList.innerUserExtensions.expandedKey",
          "sensorElementList.innerUserExtensions."),
      false,
      true,
      "^LT_SENSORELEMENT_[a-z][a-zA-Z0-9]*\\:\\w+$",
      true,
      false,
      ""),
  LE_SENSORELEMENT_fieldname(
      OperatorType.LE,
      ValueType.PAIR,
      Map.of(
          "sensorElementList.innerUserExtensions.expandedKey",
          "sensorElementList.innerUserExtensions."),
      false,
      true,
      "^LE_SENSORELEMENT_[a-z][a-zA-Z0-9]*\\:\\w+$",
      true,
      false,
      ""),
  EQ_INNER_SENSORELEMENT_fieldname(
      OperatorType.EQ,
      ValueType.PAIR,
      Map.of(
          "sensorElementList.innerUserExtensions.expandedKey",
          "sensorElementList.innerUserExtensions."),
      false,
      true,
      "^EQ_INNER_SENSORELEMENT_[a-z][a-zA-Z0-9]*\\:\\w+$",
      true,
      false,
      ""),
  GT_INNER_SENSORELEMENT_fieldname(
      OperatorType.GT,
      ValueType.PAIR,
      Map.of(
          "sensorElementList.innerUserExtensions.expandedKey",
          "sensorElementList.innerUserExtensions."),
      false,
      true,
      "^GT_INNER_SENSORELEMENT_[a-z][a-zA-Z0-9]*\\:\\w+$",
      true,
      false,
      ""),
  GE_INNER_SENSORELEMENT_fieldname(
      OperatorType.GE,
      ValueType.PAIR,
      Map.of(
          "sensorElementList.innerUserExtensions.expandedKey",
          "sensorElementList.innerUserExtensions."),
      false,
      true,
      "^GE_INNER_SENSORELEMENT_[a-z][a-zA-Z0-9]*\\:\\w+$",
      true,
      false,
      ""),
  LT_INNER_SENSORELEMENT_fieldname(
      OperatorType.LT,
      ValueType.PAIR,
      Map.of(
          "sensorElementList.innerUserExtensions.expandedKey",
          "sensorElementList.innerUserExtensions."),
      false,
      true,
      "^LT_INNER_SENSORELEMENT_[a-z][a-zA-Z0-9]*\\:\\w+$",
      true,
      false,
      ""),
  LE_INNER_SENSORELEMENT_fieldname(
      OperatorType.LE,
      ValueType.PAIR,
      Map.of(
          "sensorElementList.innerUserExtensions.expandedKey",
          "sensorElementList.innerUserExtensions."),
      false,
      true,
      "^LE_INNER_SENSORELEMENT_[a-z][a-zA-Z0-9]*\\:\\w+$",
      true,
      false,
      ""),

  EXISTS_SENSORMETADATA_fieldname(
      OperatorType.EXISTS,
      ValueType.PAIR,
      Map.of(
          "sensorElementList.sensorMetadata.userExtensions.expandedKey",
          "sensorElementList.sensorMetadata.userExtensions"),
      false,
      true,
      "^EXISTS_SENSORMETADATA_[a-z][a-zA-Z0-9]*\\:\\w+$",
      true,
      false,
      ""),
  EQ_SENSORMETADATA_fieldname(
      OperatorType.EQ,
      ValueType.PAIR,
      Map.of(
          "sensorElementList.sensorMetadata.userExtensions.expandedKey",
          "sensorElementList.sensorMetadata.userExtensions."),
      false,
      true,
      "^EQ_SENSORMETADATA_[a-z][a-zA-Z0-9]*\\:\\w+$",
      true,
      false,
      ""),
  GE_SENSORMETADATA_fieldname(
      OperatorType.GE,
      ValueType.PAIR,
      Map.of(
          "sensorElementList.sensorMetadata.userExtensions.expandedKey",
          "sensorElementList.sensorMetadata.userExtensions."),
      false,
      true,
      "^GE_SENSORMETADATA_[a-z][a-zA-Z0-9]*\\:\\w+$",
      true,
      false,
      ""),
  GT_SENSORMETADATA_fieldname(
      OperatorType.GT,
      ValueType.PAIR,
      Map.of(
          "sensorElementList.sensorMetadata.userExtensions.expandedKey",
          "sensorElementList.sensorMetadata.userExtensions."),
      false,
      true,
      "^GT_SENSORMETADATA_[a-z][a-zA-Z0-9]*\\:\\w+$",
      true,
      false,
      ""),
  LE_SENSORMETADATA_fieldname(
      OperatorType.LE,
      ValueType.PAIR,
      Map.of(
          "sensorElementList.sensorMetadata.userExtensions.expandedKey",
          "sensorElementList.sensorMetadata.userExtensions."),
      false,
      true,
      "^LE_SENSORMETADATA_[a-z][a-zA-Z0-9]*\\:\\w+$",
      true,
      false,
      ""),
  LT_SENSORMETADATA_fieldname(
      OperatorType.LT,
      ValueType.PAIR,
      Map.of(
          "sensorElementList.sensorMetadata.userExtensions.expandedKey",
          "sensorElementList.sensorMetadata.userExtensions."),
      false,
      true,
      "^LT_SENSORMETADATA_[a-z][a-zA-Z0-9]*\\:\\w+$",
      true,
      false,
      ""),
  EQ_INNER_SENSORMETADATA_fieldname(
      OperatorType.EQ,
      ValueType.PAIR,
      Map.of(
          "sensorElementList.sensorMetadata.userExtensions.expandedKey",
          "sensorElementList.sensorMetadata.userExtensions."),
      false,
      true,
      "^EQ_INNER_SENSORMETADATA_[a-z][a-zA-Z0-9]*\\:\\w+$",
      true,
      false,
      ""),
  GE_INNER_SENSORMETADATA_fieldname(
      OperatorType.GE,
      ValueType.PAIR,
      Map.of(
          "sensorElementList.sensorMetadata.userExtensions.expandedKey",
          "sensorElementList.sensorMetadata.userExtensions."),
      false,
      true,
      "^GE_INNER_SENSORMETADATA_[a-z][a-zA-Z0-9]*\\:\\w+$",
      true,
      false,
      ""),
  GT_INNER_SENSORMETADATA_fieldname(
      OperatorType.GT,
      ValueType.PAIR,
      Map.of(
          "sensorElementList.sensorMetadata.userExtensions.expandedKey",
          "sensorElementList.sensorMetadata.userExtensions."),
      false,
      true,
      "^GT_INNER_SENSORMETADATA_[a-z][a-zA-Z0-9]*\\:\\w+$",
      true,
      false,
      ""),
  LE_INNER_SENSORMETADATA_fieldname(
      OperatorType.LE,
      ValueType.PAIR,
      Map.of(
          "sensorElementList.sensorMetadata.userExtensions.expandedKey",
          "sensorElementList.sensorMetadata.userExtensions."),
      false,
      true,
      "^LE_INNER_SENSORMETADATA_[a-z][a-zA-Z0-9]*\\:\\w+$",
      true,
      false,
      ""),
  LT_INNER_SENSORMETADATA_fieldname(
      OperatorType.LT,
      ValueType.PAIR,
      Map.of(
          "sensorElementList.sensorMetadata.userExtensions.expandedKey",
          "sensorElementList.sensorMetadata.userExtensions."),
      false,
      true,
      "^LT_INNER_SENSORMETADATA_[a-z][a-zA-Z0-9]*\\:\\w+$",
      true,
      false,
      ""),

  EXISTS_SENSORREPORT_fieldname(
      OperatorType.EXISTS,
      ValueType.PAIR,
      Map.of(
          "sensorElementList.sensorReport.userExtensions.expandedKey",
          "sensorElementList.sensorReport.userExtensions"),
      false,
      true,
      "^EXISTS_SENSORREPORT_[a-z][a-zA-Z0-9]*\\:\\w+$",
      true,
      false,
      ""),
  EQ_SENSORREPORT_fieldname(
      OperatorType.EQ,
      ValueType.PAIR,
      Map.of(
          "sensorElementList.sensorReport.userExtensions.expandedKey",
          "sensorElementList.sensorReport.userExtensions."),
      false,
      true,
      "^EQ_SENSORREPORT_[a-z][a-zA-Z0-9]*\\:\\w+$",
      true,
      false,
      ""),
  GE_SENSORREPORT_fieldname(
      OperatorType.GE,
      ValueType.PAIR,
      Map.of(
          "sensorElementList.sensorReport.userExtensions.expandedKey",
          "sensorElementList.sensorReport.userExtensions."),
      false,
      true,
      "^GE_SENSORREPORT_[a-z][a-zA-Z0-9]*\\:\\w+$",
      true,
      false,
      ""),
  GT_SENSORREPORT_fieldname(
      OperatorType.GT,
      ValueType.PAIR,
      Map.of(
          "sensorElementList.sensorReport.userExtensions.expandedKey",
          "sensorElementList.sensorReport.userExtensions."),
      false,
      true,
      "^GT_SENSORREPORT_[a-z][a-zA-Z0-9]*\\:\\w+$",
      true,
      false,
      ""),
  LE_SENSORREPORT_fieldname(
      OperatorType.LE,
      ValueType.PAIR,
      Map.of(
          "sensorElementList.sensorReport.userExtensions.expandedKey",
          "sensorElementList.sensorReport.userExtensions."),
      false,
      true,
      "^LE_SENSORREPORT_[a-z][a-zA-Z0-9]*\\:\\w+$",
      true,
      false,
      ""),
  LT_SENSORREPORT_fieldname(
      OperatorType.LT,
      ValueType.PAIR,
      Map.of(
          "sensorElementList.sensorReport.userExtensions.expandedKey",
          "sensorElementList.sensorReport.userExtensions."),
      false,
      true,
      "^LT_SENSORREPORT_[a-z][a-zA-Z0-9]*\\:\\w+$",
      true,
      false,
      ""),
  EQ_INNER_SENSORREPORT_fieldname(
      OperatorType.EQ,
      ValueType.PAIR,
      Map.of(
          "sensorElementList.sensorReport.userExtensions.expandedKey",
          "sensorElementList.sensorReport.userExtensions."),
      false,
      true,
      "^EQ_INNER_SENSORREPORT_[a-z][a-zA-Z0-9]*\\:\\w+$",
      true,
      false,
      ""),
  GE_INNER_SENSORREPORT_fieldname(
      OperatorType.GE,
      ValueType.PAIR,
      Map.of(
          "sensorElementList.sensorReport.userExtensions.expandedKey",
          "sensorElementList.sensorReport.userExtensions."),
      false,
      true,
      "^GE_INNER_SENSORREPORT_[a-z][a-zA-Z0-9]*\\:\\w+$",
      true,
      false,
      ""),
  GT_INNER_SENSORREPORT_fieldname(
      OperatorType.GT,
      ValueType.PAIR,
      Map.of(
          "sensorElementList.sensorReport.userExtensions.expandedKey",
          "sensorElementList.sensorReport.userExtensions."),
      false,
      true,
      "^GT_INNER_SENSORREPORT_[a-z][a-zA-Z0-9]*\\:\\w+$",
      true,
      false,
      ""),
  LE_INNER_SENSORREPORT_fieldname(
      OperatorType.LE,
      ValueType.PAIR,
      Map.of(
          "sensorElementList.sensorReport.userExtensions.expandedKey",
          "sensorElementList.sensorReport.userExtensions."),
      false,
      true,
      "^LE_INNER_SENSORREPORT_[a-z][a-zA-Z0-9]*\\:\\w+$",
      true,
      false,
      ""),
  LT_INNER_SENSORREPORT_fieldname(
      OperatorType.LT,
      ValueType.PAIR,
      Map.of(
          "sensorElementList.sensorReport.userExtensions.expandedKey",
          "sensorElementList.sensorReport.userExtensions."),
      false,
      true,
      "^LT_INNER_SENSORREPORT_[a-z][a-zA-Z0-9]*\\:\\w+$",
      true,
      false,
      ""),

  EQ_readPoint_fieldname(
      OperatorType.EQ,
      ValueType.PAIR,
      Map.of("readPoint.userExtensions.path", "readPoint.userExtensions."),
      false,
      true,
      "^EQ_readPoint_[a-z][a-zA-Z0-9]*(?::\\w+)(?:\\.[a-z][a-zA-Z0-9]*:\\w+)*$",
      true,
      false,
      ""),
  EQ_INNER_readPoint_fieldname(
      OperatorType.EQ,
      ValueType.PAIR,
      Map.of("readPoint.innerUserExtensions.expandedKey", "readPoint.innerUserExtensions."),
      false,
      true,
      "^EQ_INNER_readPoint_[a-z][a-zA-Z0-9]*\\:\\w+$",
      true,
      false,
      ""),
  EQ_bizLocation_fieldname(
      OperatorType.EQ,
      ValueType.PAIR,
      Map.of("bizLocation.userExtensions.path", "bizLocation.userExtensions."),
      false,
      true,
      "^EQ_bizLocation_[a-z][a-zA-Z0-9]*(?::\\w+)(?:\\.[a-z][a-zA-Z0-9]*:\\w+)*$",
      true,
      false,
      ""),
  EQ_INNER_bizLocation_fieldname(
      OperatorType.EQ,
      ValueType.PAIR,
      Map.of("bizLocation.innerUserExtensions.expandedKey", "bizLocation.innerUserExtensions."),
      false,
      true,
      "^EQ_INNER_bizLocation_[a-z][a-zA-Z0-9]*\\:\\w+$",
      true,
      false,
      ""),
  EQ_fieldname(
      OperatorType.EQ,
      ValueType.PAIR,
      Map.of("innerUserExtensions.path", "innerUserExtensions."),
      false,
      true,
      "^EQ_[a-z][a-zA-Z0-9]*(?::[a-zA-Z]+)(?:\\.[a-z][a-zA-Z0-9]*:[a-zA-Z]+)*$",
      true,
      false,
      ""),
  GT_fieldname(
      OperatorType.GT,
      ValueType.PAIR,
      Map.of("innerUserExtensions.path", "innerUserExtensions."),
      false,
      true,
      "^GT_[a-z][a-zA-Z0-9]*(?::[a-zA-Z]+)(?:\\.[a-z][a-zA-Z0-9]*:[a-zA-Z]+)*$",
      true,
      false,
      ""),
  GE_fieldname(
      OperatorType.GE,
      ValueType.PAIR,
      Map.of("innerUserExtensions.path", "innerUserExtensions."),
      false,
      true,
      "^GE_[a-z][a-zA-Z0-9]*(?::[a-zA-Z]+)(?:\\.[a-z][a-zA-Z0-9]*:[a-zA-Z]+)*$",
      true,
      false,
      ""),
  LT_fieldname(
      OperatorType.LT,
      ValueType.PAIR,
      Map.of("innerUserExtensions.path", "innerUserExtensions."),
      false,
      true,
      "^LT_[a-z][a-zA-Z0-9]*(?::[a-zA-Z]+)(?:\\.[a-z][a-zA-Z0-9]*:[a-zA-Z]+)*$",
      true,
      false,
      ""),
  LE_fieldname(
      OperatorType.LE,
      ValueType.PAIR,
      Map.of("innerUserExtensions.path", "innerUserExtensions."),
      false,
      true,
      "^LE_[a-z][a-zA-Z0-9]*(?::[a-zA-Z]+)(?:\\.[a-z][a-zA-Z0-9]*:[a-zA-Z]+)*$",
      true,
      false,
      ""),
  EXISTS_fieldname(
      OperatorType.EXISTS,
      ValueType.PAIR,
      Map.of("userExtensions.path", "userExtensions."),
      false,
      true,
      "^EXISTS_[a-z][a-zA-Z0-9]*(?::[a-zA-Z]+)(?:\\.[a-z][a-zA-Z0-9]*:[a-zA-Z]+)*$",
      true,
      false,
      ""),
  EXISTS_INNER_fieldname(
      OperatorType.EXISTS,
      ValueType.PAIR,
      Map.of("innerUserExtensions.expandedKey", "innerUserExtensions"),
      false,
      true,
      "^EXISTS_INNER_[a-z][a-zA-Z0-9]*\\:\\w+$",
      true,
      false,
      ""),
  EQ_INNER_fieldname(
      OperatorType.EQ,
      ValueType.PAIR,
      Map.of("innerUserExtensions.expandedKey", "innerUserExtensions."),
      false,
      true,
      "^EQ_INNER_[a-z][a-zA-Z0-9]*\\:\\w+$",
      true,
      false,
      ""),
  GT_INNER_fieldname(
      OperatorType.GT,
      ValueType.PAIR,
      Map.of("innerUserExtensions.expandedKey", "innerUserExtensions."),
      false,
      true,
      "^GT_INNER_[a-z][a-zA-Z0-9]*\\:\\w+$",
      true,
      false,
      ""),
  GE_INNER_fieldname(
      OperatorType.GE,
      ValueType.PAIR,
      Map.of("innerUserExtensions.expandedKey", "innerUserExtensions."),
      false,
      true,
      "^GE_INNER_[a-z][a-zA-Z0-9]*\\:\\w+$",
      true,
      false,
      ""),
  LT_INNER_fieldname(
      OperatorType.LT,
      ValueType.PAIR,
      Map.of("innerUserExtensions.expandedKey", "innerUserExtensions."),
      false,
      true,
      "^LT_INNER_[a-z][a-zA-Z0-9]*\\:\\w+$",
      true,
      false,
      ""),
  LE_INNER_fieldname(
      OperatorType.LE,
      ValueType.PAIR,
      Map.of("innerUserExtensions.expandedKey", "innerUserExtensions."),
      false,
      true,
      "^LE_INNER_[a-z][a-zA-Z0-9]*\\:\\w+$",
      true,
      false,
      ""),
  EQ_ILMD_fieldname(
      OperatorType.EQ,
      ValueType.PAIR,
      Map.of("ilmd.userExtensions.path", "ilmd.userExtensions."),
      false,
      true,
      "^EQ_ILMD_[a-z][a-zA-Z0-9]*(?::\\w+)(?:\\.[a-z][a-zA-Z0-9]*:\\w+)*$",
      true,
      false,
      ""),
  GT_ILMD_fieldname(
      OperatorType.GT,
      ValueType.PAIR,
      Map.of("ilmd.userExtensions.path", "ilmd.userExtensions."),
      false,
      true,
      "^GT_ILMD_[a-z][a-zA-Z0-9]*(?::\\w+)(?:\\.[a-z][a-zA-Z0-9]*:\\w+)*$",
      true,
      false,
      ""),
  GE_ILMD_fieldname(
      OperatorType.GE,
      ValueType.PAIR,
      Map.of("ilmd.userExtensions.path", "ilmd.userExtensions."),
      false,
      true,
      "^GE_ILMD_[a-z][a-zA-Z0-9]*(?::\\w+)(?:\\.[a-z][a-zA-Z0-9]*:\\w+)*$",
      true,
      false,
      ""),
  LT_ILMD_fieldname(
      OperatorType.LT,
      ValueType.PAIR,
      Map.of("ilmd.userExtensions.path", "ilmd.userExtensions."),
      false,
      true,
      "^LT_ILMD_[a-z][a-zA-Z0-9]*(?::\\w+)(?:\\.[a-z][a-zA-Z0-9]*:\\w+)*$",
      true,
      false,
      ""),
  LE_ILMD_fieldname(
      OperatorType.LE,
      ValueType.PAIR,
      Map.of("ilmd.userExtensions.path", "ilmd.userExtensions."),
      false,
      true,
      "^LE_ILMD_[a-z][a-zA-Z0-9]*(?::\\w+)(?:\\.[a-z][a-zA-Z0-9]*:\\w+)*$",
      true,
      false,
      ""),
  EXISTS_ILMD_fieldname(
      OperatorType.EXISTS,
      ValueType.PAIR,
      Map.of("ilmd.userExtensions.path", "ilmd.userExtensions"),
      false,
      true,
      "^EXISTS_ILMD_[a-z][a-zA-Z0-9]*(?::\\w+)(?:\\.[a-z][a-zA-Z0-9]*:\\w+)*$",
      true,
      false,
      ""),
  EXISTS_INNER_ILMD_fieldname(
      OperatorType.EXISTS,
      ValueType.PAIR,
      Map.of("ilmd.innerUserExtensions.expandedKey", "ilmd.innerUserExtensions"),
      false,
      true,
      "^EXISTS_INNER_ILMD_[a-z][a-zA-Z0-9]*\\:\\w+(?:\\.[a-z][a-zA-Z0-9]*\\:\\w+)?+$",
      true,
      false,
      ""),
  EQ_INNER_ILMD_fieldname(
      OperatorType.EQ,
      ValueType.PAIR,
      Map.of("ilmd.innerUserExtensions.expandedKey", "ilmd.innerUserExtensions."),
      false,
      true,
      "^EQ_INNER_ILMD_[a-z][a-zA-Z0-9]*\\:\\w+(?:\\.[a-z][a-zA-Z0-9]*\\:\\w+)?+$",
      true,
      false,
      ""),
  GT_INNER_ILMD_fieldname(
      OperatorType.GT,
      ValueType.PAIR,
      Map.of("ilmd.innerUserExtensions.expandedKey", "ilmd.innerUserExtensions."),
      false,
      true,
      "^GT_INNER_ILMD_[a-z][a-zA-Z0-9]*\\:\\w+(?:\\.[a-z][a-zA-Z0-9]*\\:\\w+)?+$",
      true,
      false,
      ""),
  GE_INNER_ILMD_fieldname(
      OperatorType.GE,
      ValueType.PAIR,
      Map.of("ilmd.innerUserExtensions.expandedKey", "ilmd.innerUserExtensions."),
      false,
      true,
      "^GE_INNER_ILMD_[a-z][a-zA-Z0-9]*\\:\\w+(?:\\.[a-z][a-zA-Z0-9]*\\:\\w+)?+$",
      true,
      false,
      ""),
  LT_INNER_ILMD_fieldname(
      OperatorType.LT,
      ValueType.PAIR,
      Map.of("ilmd.innerUserExtensions.expandedKey", "ilmd.innerUserExtensions."),
      false,
      true,
      "^LT_INNER_ILMD_[a-z][a-zA-Z0-9]*\\:\\w+(?:\\.[a-z][a-zA-Z0-9]*\\:\\w+)?+$",
      true,
      false,
      ""),
  LE_INNER_ILMD_fieldname(
      OperatorType.LE,
      ValueType.PAIR,
      Map.of("ilmd.innerUserExtensions.expandedKey", "ilmd.innerUserExtensions."),
      false,
      true,
      "^LE_INNER_ILMD_[a-z][a-zA-Z0-9]*\\:\\w+(?:\\.[a-z][a-zA-Z0-9]*\\:\\w+)?+$",
      true,
      false,
      "");

  private final ValueType valueType;
  private final boolean appendURIPrefix;
  private final boolean isRegex;
  private final boolean isUserExtension;
  private EpcisQueryType queryType = EpcisQueryType.SIMPLE_EVENT_QUERY;
  private OperatorType operator;
  private Map<String, String> keys;
  private String uriPrefix;
  private String pattern;
  private final boolean isNested;
  private final String nestedPath;

  QueryParamName(
      final OperatorType operator,
      final ValueType valueType,
      final Map<String, String> keys,
      final boolean appendURIPrefix,
      final String uriPrefix,
      final boolean isRegex,
      final String pattern,
      final boolean isUserExtension,
      boolean isNested,
      String nestedPath) {
    this.operator = operator;
    this.valueType = valueType;
    this.keys = keys;
    this.appendURIPrefix = appendURIPrefix;
    this.uriPrefix = uriPrefix;
    this.isRegex = isRegex;
    this.pattern = pattern;
    this.isUserExtension = isUserExtension;
    this.isNested = isNested;
    this.nestedPath = nestedPath;
  }

  QueryParamName(
      final OperatorType operator,
      final ValueType valueType,
      final Map<String, String> keys,
      final boolean appendURIPrefix,
      final String uriPrefix,
      final boolean isRegex,
      final boolean isUserExtension,
      boolean isNested,
      String nestedPath) {
    this.operator = operator;
    this.valueType = valueType;
    this.keys = keys;
    this.appendURIPrefix = appendURIPrefix;
    this.uriPrefix = uriPrefix;
    this.isRegex = isRegex;
    this.isUserExtension = isUserExtension;
    this.isNested = isNested;
    this.nestedPath = nestedPath;
  }

  QueryParamName(
      final OperatorType operator,
      final ValueType valueType,
      final Map<String, String> keys,
      final boolean appendURIPrefix,
      final boolean isRegex,
      final boolean isUserExtension,
      boolean isNested,
      String nestedPath) {
    this.operator = operator;
    this.valueType = valueType;
    this.keys = keys;
    this.appendURIPrefix = appendURIPrefix;
    this.isRegex = isRegex;
    this.isUserExtension = isUserExtension;
    this.isNested = isNested;
    this.nestedPath = nestedPath;
  }

  QueryParamName(
      final EpcisQueryType queryType,
      final OperatorType operator,
      final ValueType valueType,
      final Map<String, String> keys,
      final boolean appendURIPrefix,
      final boolean isRegex,
      final boolean isUserExtension,
      boolean isNested,
      String nestedPath) {
    this.queryType = queryType;
    this.operator = operator;
    this.valueType = valueType;
    this.keys = keys;
    this.appendURIPrefix = appendURIPrefix;
    this.isRegex = isRegex;
    this.isUserExtension = isUserExtension;
    this.isNested = isNested;
    this.nestedPath = nestedPath;
  }

  QueryParamName(
      final OperatorType operator,
      ValueType valueType,
      Map<String, String> keys,
      final boolean appendURIPrefix,
      final boolean isRegex,
      final String pattern,
      final boolean isUserExtension,
      boolean isNested,
      String nestedPath) {
    this.operator = operator;
    this.valueType = valueType;
    this.keys = keys;
    this.appendURIPrefix = appendURIPrefix;
    this.isRegex = isRegex;
    this.pattern = pattern;
    this.isUserExtension = isUserExtension;
    this.isNested = isNested;
    this.nestedPath = nestedPath;
  }

  QueryParamName(
      final ValueType valueType,
      final boolean appendURIPrefix,
      final boolean isRegex,
      final boolean isUserExtension,
      boolean isNested,
      String nestedPath) {
    this.valueType = valueType;
    this.appendURIPrefix = appendURIPrefix;
    this.isRegex = isRegex;
    this.isUserExtension = isUserExtension;
    this.isNested = isNested;
    this.nestedPath = nestedPath;
  }

  public static QueryParamName parse(String queryParamName) {
    if (StringUtils.isEmpty(queryParamName)) {
      throw new QueryParameterException(ExceptionMessages.INVALID_QUERY_PARAMETER_NAME);
    }

    queryParamName = queryParamName.trim();

    if (queryParamName.equals(WD_bizLocation.name())
        || queryParamName.equals(WD_readPoint.name())) {
      throw new UnsupportedQueryParameterException(
          String.format(ExceptionMessages.UNSUPPORTED_QUERY_PARAMETER, queryParamName));
    }

    for (QueryParamName param : QueryParamName.values()) {
      if (param.isRegex()) {
        Pattern p = Pattern.compile(param.getPattern());
        Matcher m = p.matcher(queryParamName);
        if (m.matches()) return param;
      } else if (param.toString().equals(queryParamName)) {
        return param;

      } else if (queryParamName.equals("@context")) return param;
    }

    throw new QueryParameterException(ExceptionMessages.INVALID_QUERY_PARAMETER_NAME);
  }

  public ValueType getValueType() {
    return valueType;
  }

  public OperatorType getOperator() {
    return operator;
  }

  public Map<String, String> getKeys() {
    return keys;
  }

  public boolean isRegex() {
    return isRegex;
  }

  public boolean isNested() {
    return isNested;
  }

  public String getNestedPath() {
    return nestedPath;
  }

  public Boolean isAppendURIPrefix() {
    return appendURIPrefix;
  }

  public String getPattern() {
    return pattern;
  }

  @SuppressWarnings({"unused"})
  public boolean validate(Object value) throws ParseException {
    try {
      switch (valueType) {
        case STRING:
          return value instanceof String;
        case STRING_LIST:
          return ((Collection<?>) value).stream().allMatch(v -> v instanceof String);
        case LONG:
          Long.valueOf(value.toString());
          return true;
        case PAIR:
          return value instanceof Map;
        case FLOAT:
          Float.valueOf(value.toString());
          return true;
        case DATE_TIME:
          if (value instanceof Date) {
            return true;
          }
          if (value instanceof OffsetDateTime) {
            return true;
          }
          final DateUtility dateUtility = DateUtility.getInstance();
          if (value instanceof Collection<?>) {
            // Every element has to be a timestamp. Filtering the collection down to
            // its strings and parsing only those let a list of anything else — a
            // number, a map — validate as a date and travel on to the query builder.
            for (Object element : (Collection<?>) value) {
              if (element instanceof Date || element instanceof OffsetDateTime) {
                continue;
              }
              if (element instanceof String) {
                dateUtility.parse((String) element);
                continue;
              }
              return false;
            }
            return true;
          } else if (value instanceof String) {
            dateUtility.parse((String) value);
            return true;
          }
          // Anything else is not a timestamp. Without this the switch fell through
          // into BOOLEAN and cast the value to String, so a wrong type was reported
          // as a boolean problem.
          return false;

        case BOOLEAN:
          if (value instanceof Boolean) {
            return true;
          }
          final String booleanValue = (String) value;
          return booleanValue.equalsIgnoreCase("true") || booleanValue.equalsIgnoreCase("false");
        default:
          return false;
      }
    } catch (NumberFormatException exception) {
      throw new QueryParameterException(
          String.format(ExceptionMessages.INVALID_VALUE, "Value", value));
    }
  }

  public EpcisQueryType getQueryType() {
    return queryType;
  }

  public boolean isNullable() {
    return EXISTS_sensorElementList == this
        || EXISTS_fieldname == this
        || EXISTS_INNER_fieldname == this;
  }

  public String getUriPrefix() {
    return uriPrefix;
  }

  public boolean isUserExtension() {
    return isUserExtension;
  }

  public boolean skipParamFromFilters() {
    return maxEventCount == this
        || perPage == this
        || eventCountLimit == this
        || orderBy == this
        || orderDirection == this;
  }
}
