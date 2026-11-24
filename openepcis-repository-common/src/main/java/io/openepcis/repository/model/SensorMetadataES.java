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
import io.openepcis.core.exception.ValidationException;
import io.openepcis.model.epcis.SensorMetadata;
import io.openepcis.model.epcis.format.EPCFormat;
import io.openepcis.model.epcis.format.FormatPreference;
import io.openepcis.repository.util.EPCFormatUtil;
import io.openepcis.repository.util.EventConvertor;
import org.apache.commons.collections4.MapUtils;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class SensorMetadataES implements ESModel<SensorMetadata> {
  private OffsetDateTime time;
  private EpcES deviceID;
  private EpcES deviceMetaData;
  private EpcES rawData;
  private OffsetDateTime startTime;
  private OffsetDateTime endTime;
  private URI dataProcessingMethod;
  private URI bizRules;
  private List<Map<String, Object>> userExtensions;
  private List<Map<String, Object>> innerUserExtensions;

  public SensorMetadataES(final SensorMetadata sensorMetaData, List<Object> context) throws ValidationException {
    this.setTime(sensorMetaData.getTime());
    if (null != sensorMetaData.getDeviceID()) this.setDeviceID(EPCFormatUtil.buildEpc(sensorMetaData.getDeviceID().toString(), context));
    if (null != sensorMetaData.getDeviceMetadata()) this.setDeviceMetaData(EPCFormatUtil.buildEpc(sensorMetaData.getDeviceMetadata().toString(), context));
    if (null != sensorMetaData.getRawData()) this.setRawData(EPCFormatUtil.buildEpc(sensorMetaData.getRawData().toString(), context));
    this.setStartTime(sensorMetaData.getStartTime());
    this.setEndTime(sensorMetaData.getEndTime());
    this.setDataProcessingMethod(sensorMetaData.getDataProcessingMethod());
    this.setBizRules(sensorMetaData.getBizRules());
    if (MapUtils.isNotEmpty(sensorMetaData.getUserExtensions())) {
      this.setUserExtensions(EventConvertor.getUserExtensionsFromCoreModel(context, sensorMetaData.getUserExtensions()));
    }
    if (MapUtils.isNotEmpty(sensorMetaData.getInnerUserExtensions())) {
      this.setInnerUserExtensions(EventConvertor.getUserExtensionsFromCoreModel(context, sensorMetaData.getInnerUserExtensions()));
    }
  }

  @Override
  @JsonIgnore
  public SensorMetadata getCoreModel() {
    return SensorMetadata.builder().time(this.getTime()).deviceID(EPCFormatUtil.getEpcAsCaptured(this.getDeviceID())).deviceMetadata(EPCFormatUtil.getEpcAsCaptured(this.getDeviceMetaData())).rawData(EPCFormatUtil.getEpcAsCaptured(this.getRawData())).startTime(this.getStartTime()).endTime(this.getEndTime()).dataProcessingMethod(this.getDataProcessingMethod()).bizRules(this.getBizRules()).userExtensions(EventConvertor.getCoreModelUserExtensions(this.getUserExtensions())).innerUserExtensions(EventConvertor.getCoreModelUserExtensions(this.getInnerUserExtensions())).build();
  }

  @Override
  public SensorMetadata getCoreModel(List<Object> context) {
    return getCoreModel();
  }

  @Override
  public SensorMetadata getCoreModel(FormatPreference preference) {
    return SensorMetadata.builder().time(this.getTime()).deviceID(EPCFormatUtil.getEpcAsExpected(this.getDeviceID(), preference.getEpcFormat().name())).deviceMetadata(EPCFormatUtil.getEpcAsExpected(this.getDeviceMetaData(), preference.getEpcFormat().name())).rawData(EPCFormatUtil.getEpcAsExpected(this.getRawData(), preference.getEpcFormat().name())).startTime(this.getStartTime()).endTime(this.getEndTime()).dataProcessingMethod(this.getDataProcessingMethod()).bizRules(this.getBizRules()).userExtensions(EventConvertor.getCoreModelUserExtensions(this.getUserExtensions())).innerUserExtensions(EventConvertor.getCoreModelUserExtensions(this.getInnerUserExtensions())).build();
  }

  @Override
  public SensorMetadata getCoreModel(final FormatPreference preference, final List<Object> context) {
    final String epcFormat = preference.getEpcFormat() != null ? preference.getEpcFormat().name() : EPCFormat.Always_GS1_Digital_Link.name();
    return SensorMetadata.builder().time(this.getTime()).deviceID(EPCFormatUtil.getEpcAsExpected(this.getDeviceID(), epcFormat, context)).deviceMetadata(EPCFormatUtil.getEpcAsExpected(this.getDeviceMetaData(), epcFormat, context)).rawData(EPCFormatUtil.getEpcAsExpected(this.getRawData(), epcFormat, context)).startTime(this.getStartTime()).endTime(this.getEndTime()).dataProcessingMethod(this.getDataProcessingMethod()).bizRules(this.getBizRules()).userExtensions(EventConvertor.getCoreModelUserExtensionsWithFormat(this.getUserExtensions(), epcFormat, context)).innerUserExtensions(EventConvertor.getCoreModelUserExtensionsWithFormat(this.getInnerUserExtensions(), epcFormat, context)).build();
  }

  public OffsetDateTime getTime() {
    return this.time;
  }

  public EpcES getDeviceID() {
    return this.deviceID;
  }

  public EpcES getDeviceMetaData() {
    return this.deviceMetaData;
  }

  public EpcES getRawData() {
    return this.rawData;
  }

  public OffsetDateTime getStartTime() {
    return this.startTime;
  }

  public OffsetDateTime getEndTime() {
    return this.endTime;
  }

  public URI getDataProcessingMethod() {
    return this.dataProcessingMethod;
  }

  public URI getBizRules() {
    return this.bizRules;
  }

  public List<Map<String, Object>> getUserExtensions() {
    return this.userExtensions;
  }

  public List<Map<String, Object>> getInnerUserExtensions() {
    return this.innerUserExtensions;
  }

  public void setTime(OffsetDateTime time) {
    this.time = time;
  }

  public void setDeviceID(EpcES deviceID) {
    this.deviceID = deviceID;
  }

  public void setDeviceMetaData(EpcES deviceMetaData) {
    this.deviceMetaData = deviceMetaData;
  }

  public void setRawData(EpcES rawData) {
    this.rawData = rawData;
  }

  public void setStartTime(OffsetDateTime startTime) {
    this.startTime = startTime;
  }

  public void setEndTime(OffsetDateTime endTime) {
    this.endTime = endTime;
  }

  public void setDataProcessingMethod(URI dataProcessingMethod) {
    this.dataProcessingMethod = dataProcessingMethod;
  }

  public void setBizRules(URI bizRules) {
    this.bizRules = bizRules;
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
    if (!(o instanceof SensorMetadataES)) return false;
    SensorMetadataES other = (SensorMetadataES) o;
    if (!other.canEqual((Object) this)) return false;
    Object this$time = this.getTime();
    Object other$time = other.getTime();
    if (this$time == null ? other$time != null : !this$time.equals(other$time)) return false;
    Object this$deviceID = this.getDeviceID();
    Object other$deviceID = other.getDeviceID();
    if (this$deviceID == null ? other$deviceID != null : !this$deviceID.equals(other$deviceID)) return false;
    Object this$deviceMetaData = this.getDeviceMetaData();
    Object other$deviceMetaData = other.getDeviceMetaData();
    if (this$deviceMetaData == null ? other$deviceMetaData != null : !this$deviceMetaData.equals(other$deviceMetaData)) return false;
    Object this$rawData = this.getRawData();
    Object other$rawData = other.getRawData();
    if (this$rawData == null ? other$rawData != null : !this$rawData.equals(other$rawData)) return false;
    Object this$startTime = this.getStartTime();
    Object other$startTime = other.getStartTime();
    if (this$startTime == null ? other$startTime != null : !this$startTime.equals(other$startTime)) return false;
    Object this$endTime = this.getEndTime();
    Object other$endTime = other.getEndTime();
    if (this$endTime == null ? other$endTime != null : !this$endTime.equals(other$endTime)) return false;
    Object this$dataProcessingMethod = this.getDataProcessingMethod();
    Object other$dataProcessingMethod = other.getDataProcessingMethod();
    if (this$dataProcessingMethod == null ? other$dataProcessingMethod != null : !this$dataProcessingMethod.equals(other$dataProcessingMethod)) return false;
    Object this$bizRules = this.getBizRules();
    Object other$bizRules = other.getBizRules();
    if (this$bizRules == null ? other$bizRules != null : !this$bizRules.equals(other$bizRules)) return false;
    Object this$userExtensions = this.getUserExtensions();
    Object other$userExtensions = other.getUserExtensions();
    if (this$userExtensions == null ? other$userExtensions != null : !this$userExtensions.equals(other$userExtensions)) return false;
    Object this$innerUserExtensions = this.getInnerUserExtensions();
    Object other$innerUserExtensions = other.getInnerUserExtensions();
    if (this$innerUserExtensions == null ? other$innerUserExtensions != null : !this$innerUserExtensions.equals(other$innerUserExtensions)) return false;
    return true;
  }

  protected boolean canEqual(Object other) {
    return other instanceof SensorMetadataES;
  }

  @Override
  public int hashCode() {
    int PRIME = 59;
    int result = 1;
    Object $time = this.getTime();
    result = result * PRIME + ($time == null ? 43 : $time.hashCode());
    Object $deviceID = this.getDeviceID();
    result = result * PRIME + ($deviceID == null ? 43 : $deviceID.hashCode());
    Object $deviceMetaData = this.getDeviceMetaData();
    result = result * PRIME + ($deviceMetaData == null ? 43 : $deviceMetaData.hashCode());
    Object $rawData = this.getRawData();
    result = result * PRIME + ($rawData == null ? 43 : $rawData.hashCode());
    Object $startTime = this.getStartTime();
    result = result * PRIME + ($startTime == null ? 43 : $startTime.hashCode());
    Object $endTime = this.getEndTime();
    result = result * PRIME + ($endTime == null ? 43 : $endTime.hashCode());
    Object $dataProcessingMethod = this.getDataProcessingMethod();
    result = result * PRIME + ($dataProcessingMethod == null ? 43 : $dataProcessingMethod.hashCode());
    Object $bizRules = this.getBizRules();
    result = result * PRIME + ($bizRules == null ? 43 : $bizRules.hashCode());
    Object $userExtensions = this.getUserExtensions();
    result = result * PRIME + ($userExtensions == null ? 43 : $userExtensions.hashCode());
    Object $innerUserExtensions = this.getInnerUserExtensions();
    result = result * PRIME + ($innerUserExtensions == null ? 43 : $innerUserExtensions.hashCode());
    return result;
  }

  @Override
  public String toString() {
    return "SensorMetadataES(time=" + this.getTime() + ", deviceID=" + this.getDeviceID() + ", deviceMetaData=" + this.getDeviceMetaData() + ", rawData=" + this.getRawData() + ", startTime=" + this.getStartTime() + ", endTime=" + this.getEndTime() + ", dataProcessingMethod=" + this.getDataProcessingMethod() + ", bizRules=" + this.getBizRules() + ", userExtensions=" + this.getUserExtensions() + ", innerUserExtensions=" + this.getInnerUserExtensions() + ")";
  }

  public SensorMetadataES(OffsetDateTime time, EpcES deviceID, EpcES deviceMetaData, EpcES rawData, OffsetDateTime startTime, OffsetDateTime endTime, URI dataProcessingMethod, URI bizRules, List<Map<String, Object>> userExtensions, List<Map<String, Object>> innerUserExtensions) {
    this.time = time;
    this.deviceID = deviceID;
    this.deviceMetaData = deviceMetaData;
    this.rawData = rawData;
    this.startTime = startTime;
    this.endTime = endTime;
    this.dataProcessingMethod = dataProcessingMethod;
    this.bizRules = bizRules;
    this.userExtensions = userExtensions;
    this.innerUserExtensions = innerUserExtensions;
  }

  public SensorMetadataES() {
  }
}
