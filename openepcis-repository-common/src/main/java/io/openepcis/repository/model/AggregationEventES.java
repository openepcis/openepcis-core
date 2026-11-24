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
import io.openepcis.model.epcis.AggregationEvent;
import io.openepcis.model.epcis.extension.OpenEPCISExtension;
import io.openepcis.model.epcis.format.CBVFormat;
import io.openepcis.model.epcis.format.EPCFormat;
import io.openepcis.model.epcis.format.FormatPreference;
import io.openepcis.repository.util.CBVFormatUtil;
import io.openepcis.repository.util.EPCFormatUtil;
import io.openepcis.repository.util.EventConvertor;
import org.apache.commons.collections4.CollectionUtils;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class AggregationEventES extends EPCISEventES {
  private List<BizTransactionListES> bizTransactionList;
  private List<QuantityListES> childQuantityList;
  private EpcES parentID;
  private List<EpcES> childEPCs;

  public AggregationEventES(final AggregationEvent aggregationEvent, Map<String, Object> metadata) throws ValidationException {
    this(aggregationEvent, metadata, aggregationEvent.getContextInfo());
  }

  public AggregationEventES(final AggregationEvent aggregationEvent, Map<String, Object> metadata, List<Object> context) throws ValidationException {
    super(aggregationEvent, metadata, context);
    this.setAction(aggregationEvent.getAction());
    if (isNotEmpty(aggregationEvent.getBizTransactionList())) {
      this.setBizTransactionList(aggregationEvent.getBizTransactionList().stream().map(item -> new BizTransactionListES(item, context)).toList());
    }
    if (isNotEmpty(aggregationEvent.getChildQuantityList())) {
      this.setChildQuantityList(aggregationEvent.getChildQuantityList().stream().map(item -> new QuantityListES(item, context)).toList());
    }
    this.setParentID(EPCFormatUtil.buildEpc(aggregationEvent.getParentID(), context));
    this.setChildEPCs(EPCFormatUtil.buildListOfEpcES(aggregationEvent.getChildEPCs(), context));
  }

  @Override
  @JsonIgnore
  public AggregationEvent getCoreModel() {
    return AggregationEvent.aggregationEventBuilder().eventID(this.getEventID()).openEPCISExtension(OpenEPCISExtension.builder().hash(this.getHash()).captureID(this.getCaptureID()).sequenceInEPCISDoc(this.getSequenceInEPCISDoc()).build()).eventTimeZoneOffset(this.getEventTimeZoneOffset()).eventTime(this.getEventTime()).recordTime(this.getRecordTime()).action(this.getAction()).bizStep(CBVFormatUtil.getCbvInExpectedFormat(this.getBizStep(), CBVFormat.No_Preference.name(), this.getContextInfo())).disposition(CBVFormatUtil.getCbvInExpectedFormat(this.getDisposition(), CBVFormat.No_Preference.name(), this.getContextInfo())).readPoint(EventConvertor.getCoreModel(this.getReadPoint(), getContextInfo())).bizLocation(EventConvertor.getCoreModel(this.getBizLocation(), getContextInfo())).errorDeclaration(EventConvertor.getCoreModel(this.getErrorDeclarationES(), getContextInfo())).contextInfo(this.getContextInfo()).destinationList(EventConvertor.getCoreModel(this.getDestinationList(), getContextInfo())).sourceList(EventConvertor.getCoreModel(this.getSourceList(), getContextInfo())).sensorElementList(EventConvertor.getCoreModel(this.getSensorElementList(), getContextInfo())).bizTransactionList(EventConvertor.getCoreModel(this.getBizTransactionList(), getContextInfo())).childQuantityList(EventConvertor.getCoreModel(this.getChildQuantityList(), getContextInfo())).parentID(EPCFormatUtil.getEpcInExpectedFormat(this.getParentID(), EPCFormat.Always_GS1_Digital_Link.name(), getContextInfo())).childEPCs(EPCFormatUtil.getEpcAsCaptured(this.getChildEPCs())).certificationInfo(this.getCertificationInfo()).extension(EventConvertor.getCoreModelUserExtensions(this.getExtension())).userExtensions(EventConvertor.getCoreModelUserExtensions(this.getUserExtensions())).build();
  }

  @JsonIgnore
  @Override
  public AggregationEvent getCoreModel(FormatPreference preference) {
    return this.getCoreModel(preference, getContextInfo());
  }

  @JsonIgnore
  @Override
  public AggregationEvent getCoreModel(final FormatPreference preference, final List<Object> context) {
    final String epcFormat = preference.getEpcFormat() != null ? preference.getEpcFormat().name() : EPCFormat.Always_GS1_Digital_Link.name();
    final String cbvFormat = preference.getCbvFormat() != null ? preference.getCbvFormat().name() : CBVFormat.No_Preference.name();
    return AggregationEvent.aggregationEventBuilder().eventID(this.getEventID()).openEPCISExtension(OpenEPCISExtension.builder().hash(this.getHash()).captureID(this.getCaptureID()).sequenceInEPCISDoc(this.getSequenceInEPCISDoc()).build()).eventTimeZoneOffset(this.getEventTimeZoneOffset()).eventTime(this.getEventTime()).recordTime(this.getRecordTime()).action(this.getAction()).bizStep(CBVFormatUtil.getCbvInExpectedFormat(this.getBizStep(), cbvFormat, context)).disposition(CBVFormatUtil.getCbvInExpectedFormat(this.getDisposition(), cbvFormat, context)).readPoint(EventConvertor.getCoreModel(this.getReadPoint(), preference, context)).bizLocation(EventConvertor.getCoreModel(this.getBizLocation(), preference, context)).errorDeclaration(EventConvertor.getCoreModel(this.getErrorDeclarationES(), context)).contextInfo(this.getContextInfo()).destinationList(EventConvertor.getCoreModel(this.getDestinationList(), preference, context)).sourceList(EventConvertor.getCoreModel(this.getSourceList(), preference, context)).sensorElementList(EventConvertor.getCoreModel(this.getSensorElementList(), preference, context)).bizTransactionList(EventConvertor.getCoreModel(this.getBizTransactionList(), preference, context)).childQuantityList(EventConvertor.getCoreModel(this.getChildQuantityList(), preference, context)).parentID(EPCFormatUtil.getEpcInExpectedFormat(this.getParentID(), epcFormat, context)).childEPCs(EPCFormatUtil.getEpcAsExpected(this.getChildEPCs(), epcFormat, context)).certificationInfo(this.getCertificationInfo()).extension(EventConvertor.getCoreModelUserExtensionsWithFormat(this.getExtension(), epcFormat, context)).userExtensions(EventConvertor.getCoreModelUserExtensionsWithFormat(this.getUserExtensions(), epcFormat, context)).build();
  }

  @JsonIgnore
  public Set<EpcES> getEpcs() {
    final Set<EpcES> epcs = new HashSet<>();
    if (CollectionUtils.isNotEmpty(childEPCs)) epcs.addAll(childEPCs);
    if (CollectionUtils.isNotEmpty(childQuantityList)) epcs.addAll(childQuantityList.stream().map(QuantityListES::getEpcClass).toList());
    return epcs;
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) return true;
    if (!(o instanceof AggregationEventES)) return false;
    AggregationEventES other = (AggregationEventES) o;
    if (!other.canEqual((Object) this)) return false;
    if (!super.equals(o)) return false;
    Object this$bizTransactionList = this.getBizTransactionList();
    Object other$bizTransactionList = other.getBizTransactionList();
    if (this$bizTransactionList == null ? other$bizTransactionList != null : !this$bizTransactionList.equals(other$bizTransactionList)) return false;
    Object this$childQuantityList = this.getChildQuantityList();
    Object other$childQuantityList = other.getChildQuantityList();
    if (this$childQuantityList == null ? other$childQuantityList != null : !this$childQuantityList.equals(other$childQuantityList)) return false;
    Object this$parentID = this.getParentID();
    Object other$parentID = other.getParentID();
    if (this$parentID == null ? other$parentID != null : !this$parentID.equals(other$parentID)) return false;
    Object this$childEPCs = this.getChildEPCs();
    Object other$childEPCs = other.getChildEPCs();
    if (this$childEPCs == null ? other$childEPCs != null : !this$childEPCs.equals(other$childEPCs)) return false;
    return true;
  }

  protected boolean canEqual(Object other) {
    return other instanceof AggregationEventES;
  }

  @Override
  public int hashCode() {
    int PRIME = 59;
    int result = super.hashCode();
    Object $bizTransactionList = this.getBizTransactionList();
    result = result * PRIME + ($bizTransactionList == null ? 43 : $bizTransactionList.hashCode());
    Object $childQuantityList = this.getChildQuantityList();
    result = result * PRIME + ($childQuantityList == null ? 43 : $childQuantityList.hashCode());
    Object $parentID = this.getParentID();
    result = result * PRIME + ($parentID == null ? 43 : $parentID.hashCode());
    Object $childEPCs = this.getChildEPCs();
    result = result * PRIME + ($childEPCs == null ? 43 : $childEPCs.hashCode());
    return result;
  }

  @Override
  public String toString() {
    return "AggregationEventES(super=" + super.toString() + ", bizTransactionList=" + this.getBizTransactionList() + ", childQuantityList=" + this.getChildQuantityList() + ", parentID=" + this.getParentID() + ", childEPCs=" + this.getChildEPCs() + ")";
  }

  public List<BizTransactionListES> getBizTransactionList() {
    return this.bizTransactionList;
  }

  public List<QuantityListES> getChildQuantityList() {
    return this.childQuantityList;
  }

  public EpcES getParentID() {
    return this.parentID;
  }

  public List<EpcES> getChildEPCs() {
    return this.childEPCs;
  }

  public void setBizTransactionList(List<BizTransactionListES> bizTransactionList) {
    this.bizTransactionList = bizTransactionList;
  }

  public void setChildQuantityList(List<QuantityListES> childQuantityList) {
    this.childQuantityList = childQuantityList;
  }

  public void setParentID(EpcES parentID) {
    this.parentID = parentID;
  }

  public void setChildEPCs(List<EpcES> childEPCs) {
    this.childEPCs = childEPCs;
  }

  public AggregationEventES(List<BizTransactionListES> bizTransactionList, List<QuantityListES> childQuantityList, EpcES parentID, List<EpcES> childEPCs) {
    this.bizTransactionList = bizTransactionList;
    this.childQuantityList = childQuantityList;
    this.parentID = parentID;
    this.childEPCs = childEPCs;
  }

  public AggregationEventES() {
  }
}
