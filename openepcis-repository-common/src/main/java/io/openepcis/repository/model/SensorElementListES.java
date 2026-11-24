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
import io.openepcis.model.epcis.SensorElementList;
import io.openepcis.model.epcis.format.EPCFormat;
import io.openepcis.model.epcis.format.FormatPreference;
import io.openepcis.repository.util.EventConvertor;
import org.apache.commons.collections4.MapUtils;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class SensorElementListES implements ESModel<SensorElementList> {
  private SensorMetadataES sensorMetadata;
  private List<SensorReportES> sensorReport;
  private List<Map<String, Object>> userExtensions;
  private List<Map<String, Object>> innerUserExtensions;

  public SensorElementListES(final SensorElementList sensorElementList, final List<Object> context) throws ValidationException {
    if (sensorElementList.getSensorMetadata() != null) {
      this.setSensorMetadata(new SensorMetadataES(sensorElementList.getSensorMetadata(), context));
    }
    if (isNotEmpty(sensorElementList.getSensorReport())) {
      this.setSensorReport(sensorElementList.getSensorReport().stream().map(sr -> new SensorReportES(sr, context)).collect(Collectors.toList()));
    }
    if (MapUtils.isNotEmpty(sensorElementList.getUserExtensions())) {
      this.setUserExtensions(EventConvertor.getUserExtensionsFromCoreModel(context, sensorElementList.getUserExtensions()));
    }
    if (MapUtils.isNotEmpty(sensorElementList.getInnerUserExtensions())) {
      this.setInnerUserExtensions(EventConvertor.getUserExtensionsFromCoreModel(context, sensorElementList.getInnerUserExtensions()));
    }
  }

  @Override
  @JsonIgnore
  public SensorElementList getCoreModel() {
    return SensorElementList.builder().sensorMetadata(EventConvertor.getCoreModel(this.getSensorMetadata())).sensorReport(EventConvertor.getCoreModel(this.getSensorReport())).userExtensions(EventConvertor.getCoreModelUserExtensions(this.getUserExtensions())).innerUserExtensions(EventConvertor.getCoreModelUserExtensions(this.getInnerUserExtensions())).build();
  }

  @Override
  public SensorElementList getCoreModel(List<Object> context) {
    return SensorElementList.builder().sensorMetadata(EventConvertor.getCoreModel(this.getSensorMetadata(), context)).sensorReport(EventConvertor.getCoreModel(this.getSensorReport(), context)).userExtensions(EventConvertor.getCoreModelUserExtensions(this.getUserExtensions())).innerUserExtensions(EventConvertor.getCoreModelUserExtensions(this.getInnerUserExtensions())).build();
  }

  @Override
  public SensorElementList getCoreModel(FormatPreference preference) {
    return SensorElementList.builder().sensorMetadata(EventConvertor.getCoreModel(this.getSensorMetadata())).sensorReport(EventConvertor.getCoreModel(this.getSensorReport())).userExtensions(EventConvertor.getCoreModelUserExtensions(this.getUserExtensions())).innerUserExtensions(EventConvertor.getCoreModelUserExtensions(this.getInnerUserExtensions())).build();
  }

  @Override
  public SensorElementList getCoreModel(final FormatPreference preference, final List<Object> context) {
    final String epcFormat = preference.getEpcFormat() != null ? preference.getEpcFormat().name() : EPCFormat.Always_GS1_Digital_Link.name();
    return SensorElementList.builder().sensorMetadata(EventConvertor.getCoreModel(this.getSensorMetadata(), preference, context)).sensorReport(EventConvertor.getCoreModel(this.getSensorReport(), preference, context)).userExtensions(EventConvertor.getCoreModelUserExtensionsWithFormat(this.getUserExtensions(), epcFormat, context)).innerUserExtensions(EventConvertor.getCoreModelUserExtensionsWithFormat(this.getInnerUserExtensions(), epcFormat, context)).build();
  }

  public SensorMetadataES getSensorMetadata() {
    return this.sensorMetadata;
  }

  public List<SensorReportES> getSensorReport() {
    return this.sensorReport;
  }

  public List<Map<String, Object>> getUserExtensions() {
    return this.userExtensions;
  }

  public List<Map<String, Object>> getInnerUserExtensions() {
    return this.innerUserExtensions;
  }

  public void setSensorMetadata(SensorMetadataES sensorMetadata) {
    this.sensorMetadata = sensorMetadata;
  }

  public void setSensorReport(List<SensorReportES> sensorReport) {
    this.sensorReport = sensorReport;
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
    if (!(o instanceof SensorElementListES)) return false;
    SensorElementListES other = (SensorElementListES) o;
    if (!other.canEqual((Object) this)) return false;
    Object this$sensorMetadata = this.getSensorMetadata();
    Object other$sensorMetadata = other.getSensorMetadata();
    if (this$sensorMetadata == null ? other$sensorMetadata != null : !this$sensorMetadata.equals(other$sensorMetadata)) return false;
    Object this$sensorReport = this.getSensorReport();
    Object other$sensorReport = other.getSensorReport();
    if (this$sensorReport == null ? other$sensorReport != null : !this$sensorReport.equals(other$sensorReport)) return false;
    Object this$userExtensions = this.getUserExtensions();
    Object other$userExtensions = other.getUserExtensions();
    if (this$userExtensions == null ? other$userExtensions != null : !this$userExtensions.equals(other$userExtensions)) return false;
    Object this$innerUserExtensions = this.getInnerUserExtensions();
    Object other$innerUserExtensions = other.getInnerUserExtensions();
    if (this$innerUserExtensions == null ? other$innerUserExtensions != null : !this$innerUserExtensions.equals(other$innerUserExtensions)) return false;
    return true;
  }

  protected boolean canEqual(Object other) {
    return other instanceof SensorElementListES;
  }

  @Override
  public int hashCode() {
    int PRIME = 59;
    int result = 1;
    Object $sensorMetadata = this.getSensorMetadata();
    result = result * PRIME + ($sensorMetadata == null ? 43 : $sensorMetadata.hashCode());
    Object $sensorReport = this.getSensorReport();
    result = result * PRIME + ($sensorReport == null ? 43 : $sensorReport.hashCode());
    Object $userExtensions = this.getUserExtensions();
    result = result * PRIME + ($userExtensions == null ? 43 : $userExtensions.hashCode());
    Object $innerUserExtensions = this.getInnerUserExtensions();
    result = result * PRIME + ($innerUserExtensions == null ? 43 : $innerUserExtensions.hashCode());
    return result;
  }

  @Override
  public String toString() {
    return "SensorElementListES(sensorMetadata=" + this.getSensorMetadata() + ", sensorReport=" + this.getSensorReport() + ", userExtensions=" + this.getUserExtensions() + ", innerUserExtensions=" + this.getInnerUserExtensions() + ")";
  }

  public SensorElementListES(SensorMetadataES sensorMetadata, List<SensorReportES> sensorReport, List<Map<String, Object>> userExtensions, List<Map<String, Object>> innerUserExtensions) {
    this.sensorMetadata = sensorMetadata;
    this.sensorReport = sensorReport;
    this.userExtensions = userExtensions;
    this.innerUserExtensions = innerUserExtensions;
  }

  public SensorElementListES() {
  }
}
