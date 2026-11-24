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
package io.openepcis.repository.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openepcis.core.exception.ValidationException;
import io.openepcis.model.epcis.SensorReport;
import io.openepcis.model.epcis.constants.CBVUrnPrefix;
import io.openepcis.model.epcis.format.CBVFormat;
import io.openepcis.model.epcis.format.EPCFormat;
import io.openepcis.model.epcis.format.FormatPreference;
import io.openepcis.repository.util.CBVFormatUtil;
import io.openepcis.repository.util.EPCFormatUtil;
import io.openepcis.repository.util.EventConvertor;
import org.apache.commons.collections4.MapUtils;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class SensorReportES implements ESModel<SensorReport> {
  private CbvES type;
  private String exception;
  private EpcES deviceID;
  private EpcES deviceMetadata;
  private EpcES rawData;
  private URI dataProcessingMethod;
  private OffsetDateTime time;
  private URI microorganism;
  private URI chemicalSubstance;
  private Double value;
  private String component;
  private String stringValue;
  private Boolean booleanValue;
  private String hexBinaryValue;
  private URI uriValue;
  private Double minValue;
  private Double maxValue;
  private Double meanValue;
  @JsonProperty("sDev")
  private Double sDev;
  private Double percRank;
  private Double percValue;
  private String uom;
  private List<Map<String, Object>> userExtensions;
  private List<Map<String, Object>> innerUserExtensions;

  public SensorReportES(final SensorReport sensorReport, final List<Object> context) throws ValidationException {
    if (null != sensorReport.getType()) {
      this.setType(CBVFormatUtil.buildCbvESForStandardVocab(sensorReport.getType().toString(), CBVUrnPrefix.SENSOR_TYPE, context));
    }
    this.setException(sensorReport.getException());
    if (null != sensorReport.getDeviceID()) this.setDeviceID(EPCFormatUtil.buildEpc(sensorReport.getDeviceID().toString(), context));
    if (null != sensorReport.getDeviceMetadata()) this.setDeviceMetadata(EPCFormatUtil.buildEpc(sensorReport.getDeviceMetadata().toString(), context));
    if (null != sensorReport.getRawData()) this.setRawData(EPCFormatUtil.buildEpc(sensorReport.getRawData().toString(), context));
    this.setDataProcessingMethod(sensorReport.getDataProcessingMethod());
    this.setTime(sensorReport.getTime());
    this.setMicroorganism(sensorReport.getMicroorganism());
    this.setChemicalSubstance(sensorReport.getChemicalSubstance());
    this.setValue(sensorReport.getValue());
    this.setComponent(sensorReport.getComponent());
    this.setStringValue(sensorReport.getStringValue());
    this.setBooleanValue(sensorReport.getBooleanValue());
    this.setHexBinaryValue(sensorReport.getHexBinaryValue());
    this.setUriValue(sensorReport.getUriValue());
    this.setMinValue(sensorReport.getMinValue());
    this.setMaxValue(sensorReport.getMaxValue());
    this.setMeanValue(sensorReport.getMeanValue());
    this.setSDev(sensorReport.getSDev());
    this.setPercRank(sensorReport.getPercRank());
    this.setPercValue(sensorReport.getPercValue());
    this.setUom(sensorReport.getUom());
    if (MapUtils.isNotEmpty(sensorReport.getUserExtensions())) {
      this.setUserExtensions(EventConvertor.getUserExtensionsFromCoreModel(context, sensorReport.getUserExtensions()));
    }
    if (MapUtils.isNotEmpty(sensorReport.getInnerUserExtensions())) {
      this.setInnerUserExtensions(EventConvertor.getUserExtensionsFromCoreModel(context, sensorReport.getInnerUserExtensions()));
    }
  }

  @Override
  @JsonIgnore
  public SensorReport getCoreModel() {
    return SensorReport.builder().type(URI.create(CBVFormatUtil.getCbvInExpectedFormat(this.getType(), CBVFormat.No_Preference.name()))).exception(this.exception).deviceID(EPCFormatUtil.getEpcAsCaptured(this.getDeviceID())).deviceMetadata(EPCFormatUtil.getEpcAsCaptured(this.getDeviceMetadata())).rawData(EPCFormatUtil.getEpcAsCaptured(this.getRawData())).dataProcessingMethod(this.getDataProcessingMethod()).time(this.getTime()).microorganism(this.getMicroorganism()).chemicalSubstance(this.getChemicalSubstance()).value(this.getValue()).component(this.getComponent()).stringValue(this.getStringValue()).booleanValue(this.getBooleanValue()).hexBinaryValue(this.getHexBinaryValue()).uriValue(this.getUriValue()).minValue(this.getMinValue()).maxValue(this.getMaxValue()).meanValue(this.getMeanValue()).sDev(this.getSDev()).percRank(this.getPercRank()).percValue(this.getPercValue()).uom(this.getUom()).userExtensions(EventConvertor.getCoreModelUserExtensions(this.getUserExtensions())).innerUserExtensions(EventConvertor.getCoreModelUserExtensions(this.getInnerUserExtensions())).build();
  }

  @Override
  public SensorReport getCoreModel(List<Object> context) {
    return SensorReport.builder().type(URI.create(CBVFormatUtil.getCbvInExpectedFormat(this.getType(), CBVFormat.No_Preference.name(), context))).exception(this.exception).deviceID(EPCFormatUtil.getEpcAsCaptured(this.getDeviceID())).deviceMetadata(EPCFormatUtil.getEpcAsCaptured(this.getDeviceMetadata())).rawData(EPCFormatUtil.getEpcAsCaptured(this.getRawData())).dataProcessingMethod(this.getDataProcessingMethod()).time(this.getTime()).microorganism(this.getMicroorganism()).chemicalSubstance(this.getChemicalSubstance()).value(this.getValue()).component(this.getComponent()).stringValue(this.getStringValue()).booleanValue(this.getBooleanValue()).hexBinaryValue(this.getHexBinaryValue()).uriValue(this.getUriValue()).minValue(this.getMinValue()).maxValue(this.getMaxValue()).meanValue(this.getMeanValue()).sDev(this.getSDev()).percRank(this.getPercRank()).percValue(this.getPercValue()).uom(this.getUom()).userExtensions(EventConvertor.getCoreModelUserExtensions(this.getUserExtensions())).innerUserExtensions(EventConvertor.getCoreModelUserExtensions(this.getInnerUserExtensions())).build();
  }

  @Override
  public SensorReport getCoreModel(FormatPreference preference) {
    return SensorReport.builder().type(this.type != null ? URI.create(Objects.requireNonNull(CBVFormatUtil.getCbvInExpectedFormat(this.getType(), preference.getCbvFormat().name()))) : null).exception(this.exception).deviceID(EPCFormatUtil.getEpcAsExpected(this.getDeviceID(), preference.getEpcFormat().name())).deviceMetadata(EPCFormatUtil.getEpcAsExpected(this.getDeviceMetadata(), preference.getEpcFormat().name())).rawData(EPCFormatUtil.getEpcAsExpected(this.getRawData(), preference.getEpcFormat().name())).dataProcessingMethod(this.getDataProcessingMethod()).time(this.getTime()).microorganism(this.getMicroorganism()).chemicalSubstance(this.getChemicalSubstance()).value(this.getValue()).component(this.getComponent()).stringValue(this.getStringValue()).booleanValue(this.getBooleanValue()).hexBinaryValue(this.getHexBinaryValue()).uriValue(this.getUriValue()).minValue(this.getMinValue()).maxValue(this.getMaxValue()).meanValue(this.getMeanValue()).sDev(this.getSDev()).percRank(this.getPercRank()).percValue(this.getPercValue()).uom(this.getUom()).userExtensions(EventConvertor.getCoreModelUserExtensions(this.getUserExtensions())).innerUserExtensions(EventConvertor.getCoreModelUserExtensions(this.getInnerUserExtensions())).build();
  }

  @Override
  public SensorReport getCoreModel(final FormatPreference preference, final List<Object> context) {
    final String epcFormat = preference.getEpcFormat() != null ? preference.getEpcFormat().name() : EPCFormat.Always_GS1_Digital_Link.name();
    return SensorReport.builder().type(this.type != null ? URI.create(Objects.requireNonNull(CBVFormatUtil.getCbvInExpectedFormat(this.getType(), preference.getCbvFormat().name(), context))) : null).exception(this.exception).deviceID(EPCFormatUtil.getEpcAsExpected(this.getDeviceID(), preference.getEpcFormat().name(), context)).deviceMetadata(EPCFormatUtil.getEpcAsExpected(this.getDeviceMetadata(), preference.getEpcFormat().name(), context)).rawData(EPCFormatUtil.getEpcAsExpected(this.getRawData(), preference.getEpcFormat().name(), context)).dataProcessingMethod(this.getDataProcessingMethod()).time(this.getTime()).microorganism(this.getMicroorganism()).chemicalSubstance(this.getChemicalSubstance()).value(this.getValue()).component(this.getComponent()).stringValue(this.getStringValue()).booleanValue(this.getBooleanValue()).hexBinaryValue(this.getHexBinaryValue()).uriValue(this.getUriValue()).minValue(this.getMinValue()).maxValue(this.getMaxValue()).meanValue(this.getMeanValue()).sDev(this.getSDev()).percRank(this.getPercRank()).percValue(this.getPercValue()).uom(this.getUom()).userExtensions(EventConvertor.getCoreModelUserExtensionsWithFormat(this.getUserExtensions(), epcFormat, context)).innerUserExtensions(EventConvertor.getCoreModelUserExtensionsWithFormat(this.getInnerUserExtensions(), epcFormat, context)).build();
  }

  public CbvES getType() {
    return this.type;
  }

  public String getException() {
    return this.exception;
  }

  public EpcES getDeviceID() {
    return this.deviceID;
  }

  public EpcES getDeviceMetadata() {
    return this.deviceMetadata;
  }

  public EpcES getRawData() {
    return this.rawData;
  }

  public URI getDataProcessingMethod() {
    return this.dataProcessingMethod;
  }

  public OffsetDateTime getTime() {
    return this.time;
  }

  public URI getMicroorganism() {
    return this.microorganism;
  }

  public URI getChemicalSubstance() {
    return this.chemicalSubstance;
  }

  public Double getValue() {
    return this.value;
  }

  public String getComponent() {
    return this.component;
  }

  public String getStringValue() {
    return this.stringValue;
  }

  public Boolean getBooleanValue() {
    return this.booleanValue;
  }

  public String getHexBinaryValue() {
    return this.hexBinaryValue;
  }

  public URI getUriValue() {
    return this.uriValue;
  }

  public Double getMinValue() {
    return this.minValue;
  }

  public Double getMaxValue() {
    return this.maxValue;
  }

  public Double getMeanValue() {
    return this.meanValue;
  }

  public Double getSDev() {
    return this.sDev;
  }

  public Double getPercRank() {
    return this.percRank;
  }

  public Double getPercValue() {
    return this.percValue;
  }

  public String getUom() {
    return this.uom;
  }

  public List<Map<String, Object>> getUserExtensions() {
    return this.userExtensions;
  }

  public List<Map<String, Object>> getInnerUserExtensions() {
    return this.innerUserExtensions;
  }

  public void setType(CbvES type) {
    this.type = type;
  }

  public void setException(String exception) {
    this.exception = exception;
  }

  public void setDeviceID(EpcES deviceID) {
    this.deviceID = deviceID;
  }

  public void setDeviceMetadata(EpcES deviceMetadata) {
    this.deviceMetadata = deviceMetadata;
  }

  public void setRawData(EpcES rawData) {
    this.rawData = rawData;
  }

  public void setDataProcessingMethod(URI dataProcessingMethod) {
    this.dataProcessingMethod = dataProcessingMethod;
  }

  public void setTime(OffsetDateTime time) {
    this.time = time;
  }

  public void setMicroorganism(URI microorganism) {
    this.microorganism = microorganism;
  }

  public void setChemicalSubstance(URI chemicalSubstance) {
    this.chemicalSubstance = chemicalSubstance;
  }

  public void setValue(Double value) {
    this.value = value;
  }

  public void setComponent(String component) {
    this.component = component;
  }

  public void setStringValue(String stringValue) {
    this.stringValue = stringValue;
  }

  public void setBooleanValue(Boolean booleanValue) {
    this.booleanValue = booleanValue;
  }

  public void setHexBinaryValue(String hexBinaryValue) {
    this.hexBinaryValue = hexBinaryValue;
  }

  public void setUriValue(URI uriValue) {
    this.uriValue = uriValue;
  }

  public void setMinValue(Double minValue) {
    this.minValue = minValue;
  }

  public void setMaxValue(Double maxValue) {
    this.maxValue = maxValue;
  }

  public void setMeanValue(Double meanValue) {
    this.meanValue = meanValue;
  }

  public void setSDev(Double sDev) {
    this.sDev = sDev;
  }

  public void setPercRank(Double percRank) {
    this.percRank = percRank;
  }

  public void setPercValue(Double percValue) {
    this.percValue = percValue;
  }

  public void setUom(String uom) {
    this.uom = uom;
  }

  public void setUserExtensions(List<Map<String, Object>> userExtensions) {
    this.userExtensions = userExtensions;
  }

  public void setInnerUserExtensions(List<Map<String, Object>> innerUserExtensions) {
    this.innerUserExtensions = innerUserExtensions;
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) return true;
    if (!(o instanceof SensorReportES)) return false;
    SensorReportES other = (SensorReportES) o;
    if (!other.canEqual((Object) this)) return false;
    Object this$value = this.getValue();
    Object other$value = other.getValue();
    if (this$value == null ? other$value != null : !this$value.equals(other$value)) return false;
    Object this$booleanValue = this.getBooleanValue();
    Object other$booleanValue = other.getBooleanValue();
    if (this$booleanValue == null ? other$booleanValue != null : !this$booleanValue.equals(other$booleanValue)) return false;
    Object this$minValue = this.getMinValue();
    Object other$minValue = other.getMinValue();
    if (this$minValue == null ? other$minValue != null : !this$minValue.equals(other$minValue)) return false;
    Object this$maxValue = this.getMaxValue();
    Object other$maxValue = other.getMaxValue();
    if (this$maxValue == null ? other$maxValue != null : !this$maxValue.equals(other$maxValue)) return false;
    Object this$meanValue = this.getMeanValue();
    Object other$meanValue = other.getMeanValue();
    if (this$meanValue == null ? other$meanValue != null : !this$meanValue.equals(other$meanValue)) return false;
    Object this$sDev = this.getSDev();
    Object other$sDev = other.getSDev();
    if (this$sDev == null ? other$sDev != null : !this$sDev.equals(other$sDev)) return false;
    Object this$percRank = this.getPercRank();
    Object other$percRank = other.getPercRank();
    if (this$percRank == null ? other$percRank != null : !this$percRank.equals(other$percRank)) return false;
    Object this$percValue = this.getPercValue();
    Object other$percValue = other.getPercValue();
    if (this$percValue == null ? other$percValue != null : !this$percValue.equals(other$percValue)) return false;
    Object this$type = this.getType();
    Object other$type = other.getType();
    if (this$type == null ? other$type != null : !this$type.equals(other$type)) return false;
    Object this$exception = this.getException();
    Object other$exception = other.getException();
    if (this$exception == null ? other$exception != null : !this$exception.equals(other$exception)) return false;
    Object this$deviceID = this.getDeviceID();
    Object other$deviceID = other.getDeviceID();
    if (this$deviceID == null ? other$deviceID != null : !this$deviceID.equals(other$deviceID)) return false;
    Object this$deviceMetadata = this.getDeviceMetadata();
    Object other$deviceMetadata = other.getDeviceMetadata();
    if (this$deviceMetadata == null ? other$deviceMetadata != null : !this$deviceMetadata.equals(other$deviceMetadata)) return false;
    Object this$rawData = this.getRawData();
    Object other$rawData = other.getRawData();
    if (this$rawData == null ? other$rawData != null : !this$rawData.equals(other$rawData)) return false;
    Object this$dataProcessingMethod = this.getDataProcessingMethod();
    Object other$dataProcessingMethod = other.getDataProcessingMethod();
    if (this$dataProcessingMethod == null ? other$dataProcessingMethod != null : !this$dataProcessingMethod.equals(other$dataProcessingMethod)) return false;
    Object this$time = this.getTime();
    Object other$time = other.getTime();
    if (this$time == null ? other$time != null : !this$time.equals(other$time)) return false;
    Object this$microorganism = this.getMicroorganism();
    Object other$microorganism = other.getMicroorganism();
    if (this$microorganism == null ? other$microorganism != null : !this$microorganism.equals(other$microorganism)) return false;
    Object this$chemicalSubstance = this.getChemicalSubstance();
    Object other$chemicalSubstance = other.getChemicalSubstance();
    if (this$chemicalSubstance == null ? other$chemicalSubstance != null : !this$chemicalSubstance.equals(other$chemicalSubstance)) return false;
    Object this$component = this.getComponent();
    Object other$component = other.getComponent();
    if (this$component == null ? other$component != null : !this$component.equals(other$component)) return false;
    Object this$stringValue = this.getStringValue();
    Object other$stringValue = other.getStringValue();
    if (this$stringValue == null ? other$stringValue != null : !this$stringValue.equals(other$stringValue)) return false;
    Object this$hexBinaryValue = this.getHexBinaryValue();
    Object other$hexBinaryValue = other.getHexBinaryValue();
    if (this$hexBinaryValue == null ? other$hexBinaryValue != null : !this$hexBinaryValue.equals(other$hexBinaryValue)) return false;
    Object this$uriValue = this.getUriValue();
    Object other$uriValue = other.getUriValue();
    if (this$uriValue == null ? other$uriValue != null : !this$uriValue.equals(other$uriValue)) return false;
    Object this$uom = this.getUom();
    Object other$uom = other.getUom();
    if (this$uom == null ? other$uom != null : !this$uom.equals(other$uom)) return false;
    Object this$userExtensions = this.getUserExtensions();
    Object other$userExtensions = other.getUserExtensions();
    if (this$userExtensions == null ? other$userExtensions != null : !this$userExtensions.equals(other$userExtensions)) return false;
    Object this$innerUserExtensions = this.getInnerUserExtensions();
    Object other$innerUserExtensions = other.getInnerUserExtensions();
    if (this$innerUserExtensions == null ? other$innerUserExtensions != null : !this$innerUserExtensions.equals(other$innerUserExtensions)) return false;
    return true;
  }

  protected boolean canEqual(Object other) {
    return other instanceof SensorReportES;
  }

  @Override
  public int hashCode() {
    int PRIME = 59;
    int result = 1;
    Object $value = this.getValue();
    result = result * PRIME + ($value == null ? 43 : $value.hashCode());
    Object $booleanValue = this.getBooleanValue();
    result = result * PRIME + ($booleanValue == null ? 43 : $booleanValue.hashCode());
    Object $minValue = this.getMinValue();
    result = result * PRIME + ($minValue == null ? 43 : $minValue.hashCode());
    Object $maxValue = this.getMaxValue();
    result = result * PRIME + ($maxValue == null ? 43 : $maxValue.hashCode());
    Object $meanValue = this.getMeanValue();
    result = result * PRIME + ($meanValue == null ? 43 : $meanValue.hashCode());
    Object $sDev = this.getSDev();
    result = result * PRIME + ($sDev == null ? 43 : $sDev.hashCode());
    Object $percRank = this.getPercRank();
    result = result * PRIME + ($percRank == null ? 43 : $percRank.hashCode());
    Object $percValue = this.getPercValue();
    result = result * PRIME + ($percValue == null ? 43 : $percValue.hashCode());
    Object $type = this.getType();
    result = result * PRIME + ($type == null ? 43 : $type.hashCode());
    Object $exception = this.getException();
    result = result * PRIME + ($exception == null ? 43 : $exception.hashCode());
    Object $deviceID = this.getDeviceID();
    result = result * PRIME + ($deviceID == null ? 43 : $deviceID.hashCode());
    Object $deviceMetadata = this.getDeviceMetadata();
    result = result * PRIME + ($deviceMetadata == null ? 43 : $deviceMetadata.hashCode());
    Object $rawData = this.getRawData();
    result = result * PRIME + ($rawData == null ? 43 : $rawData.hashCode());
    Object $dataProcessingMethod = this.getDataProcessingMethod();
    result = result * PRIME + ($dataProcessingMethod == null ? 43 : $dataProcessingMethod.hashCode());
    Object $time = this.getTime();
    result = result * PRIME + ($time == null ? 43 : $time.hashCode());
    Object $microorganism = this.getMicroorganism();
    result = result * PRIME + ($microorganism == null ? 43 : $microorganism.hashCode());
    Object $chemicalSubstance = this.getChemicalSubstance();
    result = result * PRIME + ($chemicalSubstance == null ? 43 : $chemicalSubstance.hashCode());
    Object $component = this.getComponent();
    result = result * PRIME + ($component == null ? 43 : $component.hashCode());
    Object $stringValue = this.getStringValue();
    result = result * PRIME + ($stringValue == null ? 43 : $stringValue.hashCode());
    Object $hexBinaryValue = this.getHexBinaryValue();
    result = result * PRIME + ($hexBinaryValue == null ? 43 : $hexBinaryValue.hashCode());
    Object $uriValue = this.getUriValue();
    result = result * PRIME + ($uriValue == null ? 43 : $uriValue.hashCode());
    Object $uom = this.getUom();
    result = result * PRIME + ($uom == null ? 43 : $uom.hashCode());
    Object $userExtensions = this.getUserExtensions();
    result = result * PRIME + ($userExtensions == null ? 43 : $userExtensions.hashCode());
    Object $innerUserExtensions = this.getInnerUserExtensions();
    result = result * PRIME + ($innerUserExtensions == null ? 43 : $innerUserExtensions.hashCode());
    return result;
  }

  @Override
  public String toString() {
    return "SensorReportES(type=" + this.getType() + ", exception=" + this.getException() + ", deviceID=" + this.getDeviceID() + ", deviceMetadata=" + this.getDeviceMetadata() + ", rawData=" + this.getRawData() + ", dataProcessingMethod=" + this.getDataProcessingMethod() + ", time=" + this.getTime() + ", microorganism=" + this.getMicroorganism() + ", chemicalSubstance=" + this.getChemicalSubstance() + ", value=" + this.getValue() + ", component=" + this.getComponent() + ", stringValue=" + this.getStringValue() + ", booleanValue=" + this.getBooleanValue() + ", hexBinaryValue=" + this.getHexBinaryValue() + ", uriValue=" + this.getUriValue() + ", minValue=" + this.getMinValue() + ", maxValue=" + this.getMaxValue() + ", meanValue=" + this.getMeanValue() + ", sDev=" + this.getSDev() + ", percRank=" + this.getPercRank() + ", percValue=" + this.getPercValue() + ", uom=" + this.getUom() + ", userExtensions=" + this.getUserExtensions() + ", innerUserExtensions=" + this.getInnerUserExtensions() + ")";
  }

  public SensorReportES(CbvES type, String exception, EpcES deviceID, EpcES deviceMetadata, EpcES rawData, URI dataProcessingMethod, OffsetDateTime time, URI microorganism, URI chemicalSubstance, Double value, String component, String stringValue, Boolean booleanValue, String hexBinaryValue, URI uriValue, Double minValue, Double maxValue, Double meanValue, Double sDev, Double percRank, Double percValue, String uom, List<Map<String, Object>> userExtensions, List<Map<String, Object>> innerUserExtensions) {
    this.type = type;
    this.exception = exception;
    this.deviceID = deviceID;
    this.deviceMetadata = deviceMetadata;
    this.rawData = rawData;
    this.dataProcessingMethod = dataProcessingMethod;
    this.time = time;
    this.microorganism = microorganism;
    this.chemicalSubstance = chemicalSubstance;
    this.value = value;
    this.component = component;
    this.stringValue = stringValue;
    this.booleanValue = booleanValue;
    this.hexBinaryValue = hexBinaryValue;
    this.uriValue = uriValue;
    this.minValue = minValue;
    this.maxValue = maxValue;
    this.meanValue = meanValue;
    this.sDev = sDev;
    this.percRank = percRank;
    this.percValue = percValue;
    this.uom = uom;
    this.userExtensions = userExtensions;
    this.innerUserExtensions = innerUserExtensions;
  }

  public SensorReportES() {
  }
}
