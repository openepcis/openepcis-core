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
import io.openepcis.model.epcis.TransformationEvent;
import io.openepcis.model.epcis.format.CBVFormat;
import io.openepcis.model.epcis.format.EPCFormat;
import io.openepcis.model.epcis.format.FormatPreference;
import io.openepcis.repository.util.CBVFormatUtil;
import io.openepcis.repository.util.EPCFormatUtil;
import io.openepcis.repository.util.EventConvertor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class TransformationEventES extends EPCISEventES {
  private List<EpcES> inputEPCList;
  private List<EpcES> outputEPCList;
  private List<QuantityListES> inputQuantityList;
  private List<QuantityListES> outputQuantityList;
  private String transformationID;
  private List<BizTransactionListES> bizTransactionList;
  private IlmdES ilmd;
  private List<Map<String, Object>> ilmdXml;

  public TransformationEventES(final TransformationEvent transformationEvent, Map<String, Object> metadata) throws ValidationException {
    this(transformationEvent, metadata, transformationEvent.getContextInfo());
  }

  public TransformationEventES(final TransformationEvent transformationEvent, Map<String, Object> metadata, List<Object> context) throws ValidationException {
    super(transformationEvent, metadata, context);
    if (transformationEvent.getPersistentDisposition() != null) {
      this.setPersistentDisposition(new PersistentDispositionES(transformationEvent.getPersistentDisposition(), context));
    }
    this.setInputEPCList(EPCFormatUtil.buildListOfEpcES(transformationEvent.getInputEPCList(), context));
    this.setOutputEPCList(EPCFormatUtil.buildListOfEpcES(transformationEvent.getOutputEPCList(), context));
    if (isNotEmpty(transformationEvent.getInputQuantityList())) {
      this.setInputQuantityList(transformationEvent.getInputQuantityList().stream().map(item -> new QuantityListES(item, context)).toList());
    }
    if (isNotEmpty(transformationEvent.getOutputQuantityList())) {
      this.setOutputQuantityList(transformationEvent.getOutputQuantityList().stream().map(item -> new QuantityListES(item, context)).toList());
    }
    this.setTransformationID(transformationEvent.getTransformationID());
    if (isNotEmpty(transformationEvent.getBizTransactionList())) {
      this.setBizTransactionList(transformationEvent.getBizTransactionList().stream().map(item -> new BizTransactionListES(item, context)).toList());
    }
    if (transformationEvent.getIlmd() != null) {
      this.setIlmd(new IlmdES(transformationEvent.getIlmd(), context));
    }
    if (MapUtils.isNotEmpty(transformationEvent.getIlmdXml())) {
      final List<Map<String, Object>> ilmdXmlModel = EventConvertor.getUserExtensionsFromCoreModel(context, transformationEvent.getIlmdXml());
      this.setIlmdXml(ilmdXmlModel);
    }
  }

  @JsonIgnore
  @Override
  public TransformationEvent getCoreModel() {
    return TransformationEvent.transformationEventBuilder().eventID(this.getEventID()).hash(this.getHash()).captureId(this.getCaptureID()).sequenceInEPCISDoc(this.getSequenceInEPCISDoc()).eventTimeZoneOffset(this.getEventTimeZoneOffset()).eventTime(this.getEventTime()).recordTime(this.getRecordTime()).bizStep(CBVFormatUtil.getCbvInExpectedFormat(this.getBizStep(), CBVFormat.Never_Translates.name())).disposition(CBVFormatUtil.getCbvInExpectedFormat(this.getDisposition(), CBVFormat.No_Preference.name())).readPoint(EventConvertor.getCoreModel(this.getReadPoint())).bizLocation(EventConvertor.getCoreModel(this.getBizLocation())).errorDeclaration(EventConvertor.getCoreModel(this.getErrorDeclarationES())).contextInfo(this.getContextInfo()).persistentDisposition(EventConvertor.getCoreModel(this.getPersistentDisposition())).destinationList(EventConvertor.getCoreModel(this.getDestinationList(), getContextInfo())).sourceList(EventConvertor.getCoreModel(this.getSourceList(), getContextInfo())).sensorElementList(EventConvertor.getCoreModel(this.getSensorElementList(), getContextInfo())).inputEPCList(EPCFormatUtil.getEpcAsCaptured(this.getInputEPCList())).outputEPCList(EPCFormatUtil.getEpcAsCaptured(this.getOutputEPCList())).inputQuantityList(EventConvertor.getCoreModel(this.getInputQuantityList(), getContextInfo())).outputQuantityList(EventConvertor.getCoreModel(this.getOutputQuantityList(), getContextInfo())).transformationID(this.getTransformationID()).bizTransactionList(EventConvertor.getCoreModel(this.getBizTransactionList(), getContextInfo())).ilmd(EventConvertor.getCoreModel(this.getIlmd())).ilmdXml(EventConvertor.getCoreModelUserExtensions(this.getIlmdXml())).certificationInfo(this.getCertificationInfo()).extension(EventConvertor.getCoreModelUserExtensions(this.getExtension())).userExtensions(EventConvertor.getCoreModelUserExtensions(this.getUserExtensions())).build();
  }

  @JsonIgnore
  @Override
  public TransformationEvent getCoreModel(FormatPreference preference) {
    return this.getCoreModel(preference, getContextInfo());
  }

  @JsonIgnore
  @Override
  public TransformationEvent getCoreModel(final FormatPreference preference, final List<Object> context) {
    final String epcFormat = preference.getEpcFormat() != null ? preference.getEpcFormat().name() : EPCFormat.Always_GS1_Digital_Link.name();
    final String cbvFormat = preference.getCbvFormat() != null ? preference.getCbvFormat().name() : CBVFormat.No_Preference.name();
    return TransformationEvent.transformationEventBuilder().eventID(this.getEventID()).hash(this.getHash()).captureId(this.getCaptureID()).sequenceInEPCISDoc(this.getSequenceInEPCISDoc()).eventTimeZoneOffset(this.getEventTimeZoneOffset()).eventTime(this.getEventTime()).recordTime(this.getRecordTime()).bizStep(CBVFormatUtil.getCbvInExpectedFormat(this.getBizStep(), cbvFormat)).disposition(CBVFormatUtil.getCbvInExpectedFormat(this.getDisposition(), cbvFormat)).readPoint(EventConvertor.getCoreModel(this.getReadPoint(), preference, context)).bizLocation(EventConvertor.getCoreModel(this.getBizLocation(), preference, context)).errorDeclaration(EventConvertor.getCoreModel(this.getErrorDeclarationES(), context)).contextInfo(this.getContextInfo()).persistentDisposition(EventConvertor.getCoreModel(this.getPersistentDisposition(), context)).destinationList(EventConvertor.getCoreModel(this.getDestinationList(), preference, context)).sourceList(EventConvertor.getCoreModel(this.getSourceList(), preference, context)).sensorElementList(EventConvertor.getCoreModel(this.getSensorElementList(), preference, context)).inputEPCList(EPCFormatUtil.getEpcAsExpected(this.getInputEPCList(), epcFormat, context)).outputEPCList(EPCFormatUtil.getEpcAsExpected(this.getOutputEPCList(), epcFormat, context)).inputQuantityList(EventConvertor.getCoreModel(this.getInputQuantityList(), preference, context)).outputQuantityList(EventConvertor.getCoreModel(this.getOutputQuantityList(), preference, context)).transformationID(this.getTransformationID()).bizTransactionList(EventConvertor.getCoreModel(this.getBizTransactionList(), preference, context)).ilmd(EventConvertor.getCoreModel(this.getIlmd(), context)).ilmdXml(EventConvertor.getCoreModelUserExtensionsWithFormat(this.getIlmdXml(), epcFormat, context)).certificationInfo(this.getCertificationInfo()).extension(EventConvertor.getCoreModelUserExtensionsWithFormat(this.getExtension(), epcFormat, context)).userExtensions(EventConvertor.getCoreModelUserExtensionsWithFormat(this.getUserExtensions(), epcFormat, context)).build();
  }

  @JsonIgnore
  public Set<EpcES> getEpcs() {
    final Set<EpcES> epcs = new HashSet<>();
    if (CollectionUtils.isNotEmpty(inputEPCList)) epcs.addAll(inputEPCList);
    if (CollectionUtils.isNotEmpty(outputEPCList)) epcs.addAll(outputEPCList);
    if (CollectionUtils.isNotEmpty(inputQuantityList)) epcs.addAll(inputQuantityList.stream().map(QuantityListES::getEpcClass).toList());
    if (CollectionUtils.isNotEmpty(outputQuantityList)) epcs.addAll(outputQuantityList.stream().map(QuantityListES::getEpcClass).toList());
    return epcs;
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) return true;
    if (!(o instanceof TransformationEventES)) return false;
    TransformationEventES other = (TransformationEventES) o;
    if (!other.canEqual((Object) this)) return false;
    if (!super.equals(o)) return false;
    Object this$inputEPCList = this.getInputEPCList();
    Object other$inputEPCList = other.getInputEPCList();
    if (this$inputEPCList == null ? other$inputEPCList != null : !this$inputEPCList.equals(other$inputEPCList)) return false;
    Object this$outputEPCList = this.getOutputEPCList();
    Object other$outputEPCList = other.getOutputEPCList();
    if (this$outputEPCList == null ? other$outputEPCList != null : !this$outputEPCList.equals(other$outputEPCList)) return false;
    Object this$inputQuantityList = this.getInputQuantityList();
    Object other$inputQuantityList = other.getInputQuantityList();
    if (this$inputQuantityList == null ? other$inputQuantityList != null : !this$inputQuantityList.equals(other$inputQuantityList)) return false;
    Object this$outputQuantityList = this.getOutputQuantityList();
    Object other$outputQuantityList = other.getOutputQuantityList();
    if (this$outputQuantityList == null ? other$outputQuantityList != null : !this$outputQuantityList.equals(other$outputQuantityList)) return false;
    Object this$transformationID = this.getTransformationID();
    Object other$transformationID = other.getTransformationID();
    if (this$transformationID == null ? other$transformationID != null : !this$transformationID.equals(other$transformationID)) return false;
    Object this$bizTransactionList = this.getBizTransactionList();
    Object other$bizTransactionList = other.getBizTransactionList();
    if (this$bizTransactionList == null ? other$bizTransactionList != null : !this$bizTransactionList.equals(other$bizTransactionList)) return false;
    Object this$ilmd = this.getIlmd();
    Object other$ilmd = other.getIlmd();
    if (this$ilmd == null ? other$ilmd != null : !this$ilmd.equals(other$ilmd)) return false;
    Object this$ilmdXml = this.getIlmdXml();
    Object other$ilmdXml = other.getIlmdXml();
    if (this$ilmdXml == null ? other$ilmdXml != null : !this$ilmdXml.equals(other$ilmdXml)) return false;
    return true;
  }

  protected boolean canEqual(Object other) {
    return other instanceof TransformationEventES;
  }

  @Override
  public int hashCode() {
    int PRIME = 59;
    int result = super.hashCode();
    Object $inputEPCList = this.getInputEPCList();
    result = result * PRIME + ($inputEPCList == null ? 43 : $inputEPCList.hashCode());
    Object $outputEPCList = this.getOutputEPCList();
    result = result * PRIME + ($outputEPCList == null ? 43 : $outputEPCList.hashCode());
    Object $inputQuantityList = this.getInputQuantityList();
    result = result * PRIME + ($inputQuantityList == null ? 43 : $inputQuantityList.hashCode());
    Object $outputQuantityList = this.getOutputQuantityList();
    result = result * PRIME + ($outputQuantityList == null ? 43 : $outputQuantityList.hashCode());
    Object $transformationID = this.getTransformationID();
    result = result * PRIME + ($transformationID == null ? 43 : $transformationID.hashCode());
    Object $bizTransactionList = this.getBizTransactionList();
    result = result * PRIME + ($bizTransactionList == null ? 43 : $bizTransactionList.hashCode());
    Object $ilmd = this.getIlmd();
    result = result * PRIME + ($ilmd == null ? 43 : $ilmd.hashCode());
    Object $ilmdXml = this.getIlmdXml();
    result = result * PRIME + ($ilmdXml == null ? 43 : $ilmdXml.hashCode());
    return result;
  }

  @Override
  public String toString() {
    return "TransformationEventES(super=" + super.toString() + ", inputEPCList=" + this.getInputEPCList() + ", outputEPCList=" + this.getOutputEPCList() + ", inputQuantityList=" + this.getInputQuantityList() + ", outputQuantityList=" + this.getOutputQuantityList() + ", transformationID=" + this.getTransformationID() + ", bizTransactionList=" + this.getBizTransactionList() + ", ilmd=" + this.getIlmd() + ", ilmdXml=" + this.getIlmdXml() + ")";
  }

  public List<EpcES> getInputEPCList() {
    return this.inputEPCList;
  }

  public List<EpcES> getOutputEPCList() {
    return this.outputEPCList;
  }

  public List<QuantityListES> getInputQuantityList() {
    return this.inputQuantityList;
  }

  public List<QuantityListES> getOutputQuantityList() {
    return this.outputQuantityList;
  }

  public String getTransformationID() {
    return this.transformationID;
  }

  public List<BizTransactionListES> getBizTransactionList() {
    return this.bizTransactionList;
  }

  public IlmdES getIlmd() {
    return this.ilmd;
  }

  public List<Map<String, Object>> getIlmdXml() {
    return this.ilmdXml;
  }

  public void setInputEPCList(List<EpcES> inputEPCList) {
    this.inputEPCList = inputEPCList;
  }

  public void setOutputEPCList(List<EpcES> outputEPCList) {
    this.outputEPCList = outputEPCList;
  }

  public void setInputQuantityList(List<QuantityListES> inputQuantityList) {
    this.inputQuantityList = inputQuantityList;
  }

  public void setOutputQuantityList(List<QuantityListES> outputQuantityList) {
    this.outputQuantityList = outputQuantityList;
  }

  public void setTransformationID(String transformationID) {
    this.transformationID = transformationID;
  }

  public void setBizTransactionList(List<BizTransactionListES> bizTransactionList) {
    this.bizTransactionList = bizTransactionList;
  }

  public void setIlmd(IlmdES ilmd) {
    this.ilmd = ilmd;
  }

  public void setIlmdXml(List<Map<String, Object>> ilmdXml) {
    this.ilmdXml = ilmdXml;
  }

  public TransformationEventES(List<EpcES> inputEPCList, List<EpcES> outputEPCList, List<QuantityListES> inputQuantityList, List<QuantityListES> outputQuantityList, String transformationID, List<BizTransactionListES> bizTransactionList, IlmdES ilmd, List<Map<String, Object>> ilmdXml) {
    this.inputEPCList = inputEPCList;
    this.outputEPCList = outputEPCList;
    this.inputQuantityList = inputQuantityList;
    this.outputQuantityList = outputQuantityList;
    this.transformationID = transformationID;
    this.bizTransactionList = bizTransactionList;
    this.ilmd = ilmd;
    this.ilmdXml = ilmdXml;
  }

  public TransformationEventES() {
  }
}
