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
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.openepcis.core.exception.ValidationException;
import io.openepcis.model.epcis.Action;
import io.openepcis.model.epcis.EPCISEvent;
import io.openepcis.model.epcis.constants.CBVUrnPrefix;
import io.openepcis.model.epcis.extension.OpenEPCISExtension;
import io.openepcis.model.epcis.format.CBVFormat;
import io.openepcis.model.epcis.format.EPCFormat;
import io.openepcis.model.epcis.format.FormatPreference;
import io.openepcis.repository.util.CBVFormatUtil;
import io.openepcis.repository.util.EventConvertor;
import org.apache.commons.collections4.MapUtils;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({@Type(value = ObjectEventES.class, name = "ObjectEvent"), @Type(value = TransformationEventES.class, name = "TransformationEvent"), @Type(value = AggregationEventES.class, name = "AggregationEvent"), @Type(value = AssociationEventES.class, name = "AssociationEvent"), @Type(value = TransactionEventES.class, name = "TransactionEvent")})
public class EPCISEventES implements ESModel<EPCISEvent> {
  private String eventID;
  private String hash;
  private MetadataES metadata;
  private String eventTimeZoneOffset;
  private OffsetDateTime eventTime;
  private OffsetDateTime recordTime;
  private Action action;
  private CbvES bizStep;
  private CbvES disposition;
  private PersistentDispositionES persistentDisposition;
  private ReadPointES readPoint;
  private BizLocationES bizLocation;
  /**
   * The error declaration, stored under the name everything else calls it.
   *
   * <p>Without the annotation Jackson writes this as {@code errorDeclarationES},
   * after the Java field. The index template maps {@code errorDeclaration} as a
   * nested object and every EPCIS query parameter that touches a correction —
   * {@code EXISTS_errorDeclaration}, {@code GE/LT_errorDeclaration_Time},
   * {@code EQ_errorReason}, {@code EQ_correctiveEventID} — addresses
   * {@code errorDeclaration.*}. So corrections were stored and then invisible:
   * none of those four could ever match, and the query answered an empty result
   * rather than an error. Measured on the serialised model, not deduced.
   *
   * <p>Nothing was ever written under {@code errorDeclaration}, so there is no
   * old shape to keep compatible with. Documents already indexed carry
   * {@code errorDeclarationES} and stay unfindable until they are reindexed —
   * an operational decision, not one this class can make.
   */
  @JsonProperty("errorDeclaration")
  private ErrorDeclarationES errorDeclarationES;
  private Object certificationInfo;
  private List<Map<String, Object>> extension;
  private List<Map<String, Object>> userExtensions;
  private List<Map<String, Object>> innerUserExtensions;
  private List<Object> contextInfo;
  private List<SourceListES> sourceList;
  private List<DestinationListES> destinationList;
  private List<SensorElementListES> sensorElementList;
  private String captureID;
  private Integer sequenceInEPCISDoc;

  public EPCISEventES(final EPCISEvent epcisEvent, Map<String, Object> metadata) throws ValidationException {
    this(epcisEvent, metadata, epcisEvent.getContextInfo());
  }

  public EPCISEventES(final EPCISEvent epcisEvent, final Map<String, Object> metadata, final List<Object> context) throws ValidationException {
    setCaptureID(epcisEvent.getOpenEPCISExtension().getCaptureID());
    setSequenceInEPCISDoc(epcisEvent.getOpenEPCISExtension().getSequenceInEPCISDoc());
    setHash(epcisEvent.getOpenEPCISExtension().getHash());
    this.setEventTimeZoneOffset(epcisEvent.getEventTimeZoneOffset());
    this.setEventTime(epcisEvent.getEventTime());
    this.setRecordTime(epcisEvent.getRecordTime());
    if (MapUtils.isNotEmpty(metadata)) {
      this.setMetadata(MetadataES.fromMetadata(metadata, captureID, epcisEvent.getRecordTime()));
    }
    this.setAction(getAction());
    this.setBizStep(CBVFormatUtil.buildCbvESForStandardVocab(epcisEvent.getBizStep(), CBVUrnPrefix.BIZSTEP, context));
    this.setDisposition(CBVFormatUtil.buildCbvESForStandardVocab(epcisEvent.getDisposition(), CBVUrnPrefix.DISPOSITION, context));
    if (epcisEvent.getReadPoint() != null) this.setReadPoint(new ReadPointES(epcisEvent.getReadPoint(), context));
    if (epcisEvent.getBizLocation() != null) this.setBizLocation(new BizLocationES(epcisEvent.getBizLocation(), context));
    if (epcisEvent.getErrorDeclaration() != null) {
      this.setErrorDeclarationES(new ErrorDeclarationES(epcisEvent.getErrorDeclaration(), context));
    }
    if (MapUtils.isNotEmpty(epcisEvent.getExtension())) this.setExtension(EventConvertor.getUserExtensionsFromCoreModel(context, epcisEvent.getExtension()));
    if (MapUtils.isNotEmpty(epcisEvent.getUserExtensions())) {
      List<Map<String, Object>> userExtensionsModal = EventConvertor.getUserExtensionsFromCoreModel(context, epcisEvent.getUserExtensions());
      this.setUserExtensions(userExtensionsModal);
    }
    if (MapUtils.isNotEmpty(epcisEvent.getInnerUserExtensions())) this.setInnerUserExtensions(EventConvertor.getUserExtensionsFromCoreModel(context, epcisEvent.getInnerUserExtensions()));
    // Set the contextInfo values from received context
    this.setContextInfo(context);
    if (isNotEmpty(epcisEvent.getSourceList())) {
      this.setSourceList(epcisEvent.getSourceList().stream().map(item -> new SourceListES(item, context)).toList());
    }
    if (isNotEmpty(epcisEvent.getDestinationList())) {
      this.setDestinationList(epcisEvent.getDestinationList().stream().map(item -> new DestinationListES(item, context)).toList());
    }
    if (isNotEmpty(epcisEvent.getSensorElementList())) {
      this.setSensorElementList(epcisEvent.getSensorElementList().stream().map(sensorElementList -> new SensorElementListES(sensorElementList, context)).toList());
    }
    this.setEventID(epcisEvent.getEventID());
    this.setCertificationInfo(epcisEvent.getCertificationInfo());
  }

  @Override
  @JsonIgnore
  public EPCISEvent getCoreModel() {
    return EPCISEvent.builder().eventID(this.getEventID()).openEPCISExtension(OpenEPCISExtension.builder().hash(this.getHash()).captureID(this.getCaptureID()).sequenceInEPCISDoc(this.getSequenceInEPCISDoc()).build()).eventTimeZoneOffset(this.getEventTimeZoneOffset()).eventTime(this.getEventTime()).recordTime(this.getRecordTime()).bizStep(CBVFormatUtil.getCbvInExpectedFormat(this.getBizStep(), CBVFormat.Never_Translates.name(), getContextInfo())).disposition(CBVFormatUtil.getCbvInExpectedFormat(this.getDisposition(), CBVFormat.Never_Translates.name(), getContextInfo())).readPoint(EventConvertor.getCoreModel(this.getReadPoint(), getContextInfo())).bizLocation(EventConvertor.getCoreModel(this.getBizLocation(), getContextInfo())).errorDeclaration(EventConvertor.getCoreModel(this.getErrorDeclarationES(), getContextInfo())).contextInfo(this.getContextInfo() != null && !this.contextInfo.isEmpty() ? this.contextInfo : null).destinationList(EventConvertor.getCoreModel(this.getDestinationList(), getContextInfo())).sourceList(EventConvertor.getCoreModel(this.getSourceList(), getContextInfo())).sensorElementList(EventConvertor.getCoreModel(this.getSensorElementList(), getContextInfo())).certificationInfo(this.getCertificationInfo()).extension(EventConvertor.getCoreModelUserExtensions(this.getExtension())).userExtensions(EventConvertor.getCoreModelUserExtensions(this.getUserExtensions())).innerUserExtensions((EventConvertor.getCoreModelUserExtensions(this.innerUserExtensions))).build();
  }

  @Override
  public EPCISEvent getCoreModel(List<Object> context) {
    return getCoreModel();
  }

  @Override
  public EPCISEvent getCoreModel(FormatPreference preference) {
    return this.getCoreModel(preference, getContextInfo());
  }

  @Override
  public EPCISEvent getCoreModel(final FormatPreference preference, final List<Object> context) {
    final String epcFormat = preference.getEpcFormat() != null ? preference.getEpcFormat().name() : EPCFormat.Always_GS1_Digital_Link.name();
    return EPCISEvent.builder().eventID(this.getEventID()).openEPCISExtension(OpenEPCISExtension.builder().hash(this.getHash()).captureID(this.getCaptureID()).sequenceInEPCISDoc(this.getSequenceInEPCISDoc()).build()).eventTimeZoneOffset(this.getEventTimeZoneOffset()).eventTime(this.getEventTime()).recordTime(this.getRecordTime()).bizStep(CBVFormatUtil.getCbvInExpectedFormat(this.getBizStep(), CBVFormat.No_Preference.name(), context)).disposition(CBVFormatUtil.getCbvInExpectedFormat(this.getDisposition(), CBVFormat.No_Preference.name(), context)).readPoint(EventConvertor.getCoreModel(this.getReadPoint(), preference, context)).bizLocation(EventConvertor.getCoreModel(this.getBizLocation(), preference, context)).errorDeclaration(EventConvertor.getCoreModel(this.getErrorDeclarationES(), context)).contextInfo(this.getContextInfo()).destinationList(EventConvertor.getCoreModel(this.getDestinationList(), preference, context)).sourceList(EventConvertor.getCoreModel(this.getSourceList(), preference, context)).sensorElementList(EventConvertor.getCoreModel(this.getSensorElementList(), preference, context)).certificationInfo(this.getCertificationInfo()).extension(EventConvertor.getCoreModelUserExtensionsWithFormat(this.getExtension(), epcFormat, context)).userExtensions(EventConvertor.getCoreModelUserExtensionsWithFormat(this.getUserExtensions(), epcFormat, context)).innerUserExtensions((EventConvertor.getCoreModelUserExtensionsWithFormat(this.innerUserExtensions, epcFormat, context))).build();
  }

  @JsonIgnore
  public Set<EpcES> getEpcs(EPCISEventES event) {
    final Set<EpcES> epcs = new HashSet<>();
    if (event instanceof ObjectEventES objectEventES) {
      return objectEventES.getEpcs();
    } else if (event instanceof TransactionEventES transactionEventES) {
      return transactionEventES.getEpcs();
    } else if (event instanceof AssociationEventES associationEventES) {
      return associationEventES.getEpcs();
    } else if (event instanceof AggregationEventES aggregationEventES) {
      return aggregationEventES.getEpcs();
    } else if (event instanceof TransformationEventES transformationEventES) {
      return transformationEventES.getEpcs();
    }
    return epcs;
  }

  public String getEventID() {
    return this.eventID;
  }

  public String getHash() {
    return this.hash;
  }

  public MetadataES getMetadata() {
    return this.metadata;
  }

  public String getEventTimeZoneOffset() {
    return this.eventTimeZoneOffset;
  }

  public OffsetDateTime getEventTime() {
    return this.eventTime;
  }

  public OffsetDateTime getRecordTime() {
    return this.recordTime;
  }

  public Action getAction() {
    return this.action;
  }

  public CbvES getBizStep() {
    return this.bizStep;
  }

  public CbvES getDisposition() {
    return this.disposition;
  }

  public PersistentDispositionES getPersistentDisposition() {
    return this.persistentDisposition;
  }

  public ReadPointES getReadPoint() {
    return this.readPoint;
  }

  public BizLocationES getBizLocation() {
    return this.bizLocation;
  }

  public ErrorDeclarationES getErrorDeclarationES() {
    return this.errorDeclarationES;
  }

  public Object getCertificationInfo() {
    return this.certificationInfo;
  }

  public List<Map<String, Object>> getExtension() {
    return this.extension;
  }

  public List<Map<String, Object>> getUserExtensions() {
    return this.userExtensions;
  }

  public List<Map<String, Object>> getInnerUserExtensions() {
    return this.innerUserExtensions;
  }

  public List<Object> getContextInfo() {
    return this.contextInfo;
  }

  public List<SourceListES> getSourceList() {
    return this.sourceList;
  }

  public List<DestinationListES> getDestinationList() {
    return this.destinationList;
  }

  public List<SensorElementListES> getSensorElementList() {
    return this.sensorElementList;
  }

  public String getCaptureID() {
    return this.captureID;
  }

  public Integer getSequenceInEPCISDoc() {
    return this.sequenceInEPCISDoc;
  }

  public void setEventID(String eventID) {
    this.eventID = eventID;
  }

  public void setHash(String hash) {
    this.hash = hash;
  }

  public void setMetadata(MetadataES metadata) {
    this.metadata = metadata;
  }

  public void setEventTimeZoneOffset(String eventTimeZoneOffset) {
    this.eventTimeZoneOffset = eventTimeZoneOffset;
  }

  public void setEventTime(OffsetDateTime eventTime) {
    this.eventTime = eventTime;
  }

  public void setRecordTime(OffsetDateTime recordTime) {
    this.recordTime = recordTime;
  }

  public void setAction(Action action) {
    this.action = action;
  }

  public void setBizStep(CbvES bizStep) {
    this.bizStep = bizStep;
  }

  public void setDisposition(CbvES disposition) {
    this.disposition = disposition;
  }

  public void setPersistentDisposition(PersistentDispositionES persistentDisposition) {
    this.persistentDisposition = persistentDisposition;
  }

  public void setReadPoint(ReadPointES readPoint) {
    this.readPoint = readPoint;
  }

  public void setBizLocation(BizLocationES bizLocation) {
    this.bizLocation = bizLocation;
  }

  public void setErrorDeclarationES(ErrorDeclarationES errorDeclarationES) {
    this.errorDeclarationES = errorDeclarationES;
  }

  public void setCertificationInfo(Object certificationInfo) {
    this.certificationInfo = certificationInfo;
  }

  public void setExtension(List<Map<String, Object>> extension) {
    this.extension = extension;
  }

  public void setUserExtensions(List<Map<String, Object>> userExtensions) {
    this.userExtensions = userExtensions;
  }

  public void setInnerUserExtensions(List<Map<String, Object>> innerUserExtensions) {
    this.innerUserExtensions = innerUserExtensions;
  }

  public void setContextInfo(List<Object> contextInfo) {
    this.contextInfo = contextInfo;
  }

  public void setSourceList(List<SourceListES> sourceList) {
    this.sourceList = sourceList;
  }

  public void setDestinationList(List<DestinationListES> destinationList) {
    this.destinationList = destinationList;
  }

  public void setSensorElementList(List<SensorElementListES> sensorElementList) {
    this.sensorElementList = sensorElementList;
  }

  public void setCaptureID(String captureID) {
    this.captureID = captureID;
  }

  public void setSequenceInEPCISDoc(Integer sequenceInEPCISDoc) {
    this.sequenceInEPCISDoc = sequenceInEPCISDoc;
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) return true;
    if (!(o instanceof EPCISEventES)) return false;
    EPCISEventES other = (EPCISEventES) o;
    if (!other.canEqual((Object) this)) return false;
    Object this$sequenceInEPCISDoc = this.getSequenceInEPCISDoc();
    Object other$sequenceInEPCISDoc = other.getSequenceInEPCISDoc();
    if (this$sequenceInEPCISDoc == null ? other$sequenceInEPCISDoc != null : !this$sequenceInEPCISDoc.equals(other$sequenceInEPCISDoc)) return false;
    Object this$eventID = this.getEventID();
    Object other$eventID = other.getEventID();
    if (this$eventID == null ? other$eventID != null : !this$eventID.equals(other$eventID)) return false;
    Object this$hash = this.getHash();
    Object other$hash = other.getHash();
    if (this$hash == null ? other$hash != null : !this$hash.equals(other$hash)) return false;
    Object this$metadata = this.getMetadata();
    Object other$metadata = other.getMetadata();
    if (this$metadata == null ? other$metadata != null : !this$metadata.equals(other$metadata)) return false;
    Object this$eventTimeZoneOffset = this.getEventTimeZoneOffset();
    Object other$eventTimeZoneOffset = other.getEventTimeZoneOffset();
    if (this$eventTimeZoneOffset == null ? other$eventTimeZoneOffset != null : !this$eventTimeZoneOffset.equals(other$eventTimeZoneOffset)) return false;
    Object this$eventTime = this.getEventTime();
    Object other$eventTime = other.getEventTime();
    if (this$eventTime == null ? other$eventTime != null : !this$eventTime.equals(other$eventTime)) return false;
    Object this$recordTime = this.getRecordTime();
    Object other$recordTime = other.getRecordTime();
    if (this$recordTime == null ? other$recordTime != null : !this$recordTime.equals(other$recordTime)) return false;
    Object this$action = this.getAction();
    Object other$action = other.getAction();
    if (this$action == null ? other$action != null : !this$action.equals(other$action)) return false;
    Object this$bizStep = this.getBizStep();
    Object other$bizStep = other.getBizStep();
    if (this$bizStep == null ? other$bizStep != null : !this$bizStep.equals(other$bizStep)) return false;
    Object this$disposition = this.getDisposition();
    Object other$disposition = other.getDisposition();
    if (this$disposition == null ? other$disposition != null : !this$disposition.equals(other$disposition)) return false;
    Object this$persistentDisposition = this.getPersistentDisposition();
    Object other$persistentDisposition = other.getPersistentDisposition();
    if (this$persistentDisposition == null ? other$persistentDisposition != null : !this$persistentDisposition.equals(other$persistentDisposition)) return false;
    Object this$readPoint = this.getReadPoint();
    Object other$readPoint = other.getReadPoint();
    if (this$readPoint == null ? other$readPoint != null : !this$readPoint.equals(other$readPoint)) return false;
    Object this$bizLocation = this.getBizLocation();
    Object other$bizLocation = other.getBizLocation();
    if (this$bizLocation == null ? other$bizLocation != null : !this$bizLocation.equals(other$bizLocation)) return false;
    Object this$errorDeclarationES = this.getErrorDeclarationES();
    Object other$errorDeclarationES = other.getErrorDeclarationES();
    if (this$errorDeclarationES == null ? other$errorDeclarationES != null : !this$errorDeclarationES.equals(other$errorDeclarationES)) return false;
    Object this$certificationInfo = this.getCertificationInfo();
    Object other$certificationInfo = other.getCertificationInfo();
    if (this$certificationInfo == null ? other$certificationInfo != null : !this$certificationInfo.equals(other$certificationInfo)) return false;
    Object this$extension = this.getExtension();
    Object other$extension = other.getExtension();
    if (this$extension == null ? other$extension != null : !this$extension.equals(other$extension)) return false;
    Object this$userExtensions = this.getUserExtensions();
    Object other$userExtensions = other.getUserExtensions();
    if (this$userExtensions == null ? other$userExtensions != null : !this$userExtensions.equals(other$userExtensions)) return false;
    Object this$innerUserExtensions = this.getInnerUserExtensions();
    Object other$innerUserExtensions = other.getInnerUserExtensions();
    if (this$innerUserExtensions == null ? other$innerUserExtensions != null : !this$innerUserExtensions.equals(other$innerUserExtensions)) return false;
    Object this$contextInfo = this.getContextInfo();
    Object other$contextInfo = other.getContextInfo();
    if (this$contextInfo == null ? other$contextInfo != null : !this$contextInfo.equals(other$contextInfo)) return false;
    Object this$sourceList = this.getSourceList();
    Object other$sourceList = other.getSourceList();
    if (this$sourceList == null ? other$sourceList != null : !this$sourceList.equals(other$sourceList)) return false;
    Object this$destinationList = this.getDestinationList();
    Object other$destinationList = other.getDestinationList();
    if (this$destinationList == null ? other$destinationList != null : !this$destinationList.equals(other$destinationList)) return false;
    Object this$sensorElementList = this.getSensorElementList();
    Object other$sensorElementList = other.getSensorElementList();
    if (this$sensorElementList == null ? other$sensorElementList != null : !this$sensorElementList.equals(other$sensorElementList)) return false;
    Object this$captureID = this.getCaptureID();
    Object other$captureID = other.getCaptureID();
    if (this$captureID == null ? other$captureID != null : !this$captureID.equals(other$captureID)) return false;
    return true;
  }

  protected boolean canEqual(Object other) {
    return other instanceof EPCISEventES;
  }

  @Override
  public int hashCode() {
    int PRIME = 59;
    int result = 1;
    Object $sequenceInEPCISDoc = this.getSequenceInEPCISDoc();
    result = result * PRIME + ($sequenceInEPCISDoc == null ? 43 : $sequenceInEPCISDoc.hashCode());
    Object $eventID = this.getEventID();
    result = result * PRIME + ($eventID == null ? 43 : $eventID.hashCode());
    Object $hash = this.getHash();
    result = result * PRIME + ($hash == null ? 43 : $hash.hashCode());
    Object $metadata = this.getMetadata();
    result = result * PRIME + ($metadata == null ? 43 : $metadata.hashCode());
    Object $eventTimeZoneOffset = this.getEventTimeZoneOffset();
    result = result * PRIME + ($eventTimeZoneOffset == null ? 43 : $eventTimeZoneOffset.hashCode());
    Object $eventTime = this.getEventTime();
    result = result * PRIME + ($eventTime == null ? 43 : $eventTime.hashCode());
    Object $recordTime = this.getRecordTime();
    result = result * PRIME + ($recordTime == null ? 43 : $recordTime.hashCode());
    Object $action = this.getAction();
    result = result * PRIME + ($action == null ? 43 : $action.hashCode());
    Object $bizStep = this.getBizStep();
    result = result * PRIME + ($bizStep == null ? 43 : $bizStep.hashCode());
    Object $disposition = this.getDisposition();
    result = result * PRIME + ($disposition == null ? 43 : $disposition.hashCode());
    Object $persistentDisposition = this.getPersistentDisposition();
    result = result * PRIME + ($persistentDisposition == null ? 43 : $persistentDisposition.hashCode());
    Object $readPoint = this.getReadPoint();
    result = result * PRIME + ($readPoint == null ? 43 : $readPoint.hashCode());
    Object $bizLocation = this.getBizLocation();
    result = result * PRIME + ($bizLocation == null ? 43 : $bizLocation.hashCode());
    Object $errorDeclarationES = this.getErrorDeclarationES();
    result = result * PRIME + ($errorDeclarationES == null ? 43 : $errorDeclarationES.hashCode());
    Object $certificationInfo = this.getCertificationInfo();
    result = result * PRIME + ($certificationInfo == null ? 43 : $certificationInfo.hashCode());
    Object $extension = this.getExtension();
    result = result * PRIME + ($extension == null ? 43 : $extension.hashCode());
    Object $userExtensions = this.getUserExtensions();
    result = result * PRIME + ($userExtensions == null ? 43 : $userExtensions.hashCode());
    Object $innerUserExtensions = this.getInnerUserExtensions();
    result = result * PRIME + ($innerUserExtensions == null ? 43 : $innerUserExtensions.hashCode());
    Object $contextInfo = this.getContextInfo();
    result = result * PRIME + ($contextInfo == null ? 43 : $contextInfo.hashCode());
    Object $sourceList = this.getSourceList();
    result = result * PRIME + ($sourceList == null ? 43 : $sourceList.hashCode());
    Object $destinationList = this.getDestinationList();
    result = result * PRIME + ($destinationList == null ? 43 : $destinationList.hashCode());
    Object $sensorElementList = this.getSensorElementList();
    result = result * PRIME + ($sensorElementList == null ? 43 : $sensorElementList.hashCode());
    Object $captureID = this.getCaptureID();
    result = result * PRIME + ($captureID == null ? 43 : $captureID.hashCode());
    return result;
  }

  @Override
  public String toString() {
    return "EPCISEventES(eventID=" + this.getEventID() + ", hash=" + this.getHash() + ", metadata=" + this.getMetadata() + ", eventTimeZoneOffset=" + this.getEventTimeZoneOffset() + ", eventTime=" + this.getEventTime() + ", recordTime=" + this.getRecordTime() + ", action=" + this.getAction() + ", bizStep=" + this.getBizStep() + ", disposition=" + this.getDisposition() + ", persistentDisposition=" + this.getPersistentDisposition() + ", readPoint=" + this.getReadPoint() + ", bizLocation=" + this.getBizLocation() + ", errorDeclarationES=" + this.getErrorDeclarationES() + ", certificationInfo=" + this.getCertificationInfo() + ", extension=" + this.getExtension() + ", userExtensions=" + this.getUserExtensions() + ", innerUserExtensions=" + this.getInnerUserExtensions() + ", contextInfo=" + this.getContextInfo() + ", sourceList=" + this.getSourceList() + ", destinationList=" + this.getDestinationList() + ", sensorElementList=" + this.getSensorElementList() + ", captureID=" + this.getCaptureID() + ", sequenceInEPCISDoc=" + this.getSequenceInEPCISDoc() + ")";
  }

  public EPCISEventES(String eventID, String hash, MetadataES metadata, String eventTimeZoneOffset, OffsetDateTime eventTime, OffsetDateTime recordTime, Action action, CbvES bizStep, CbvES disposition, PersistentDispositionES persistentDisposition, ReadPointES readPoint, BizLocationES bizLocation, ErrorDeclarationES errorDeclarationES, Object certificationInfo, List<Map<String, Object>> extension, List<Map<String, Object>> userExtensions, List<Map<String, Object>> innerUserExtensions, List<Object> contextInfo, List<SourceListES> sourceList, List<DestinationListES> destinationList, List<SensorElementListES> sensorElementList, String captureID, Integer sequenceInEPCISDoc) {
    this.eventID = eventID;
    this.hash = hash;
    this.metadata = metadata;
    this.eventTimeZoneOffset = eventTimeZoneOffset;
    this.eventTime = eventTime;
    this.recordTime = recordTime;
    this.action = action;
    this.bizStep = bizStep;
    this.disposition = disposition;
    this.persistentDisposition = persistentDisposition;
    this.readPoint = readPoint;
    this.bizLocation = bizLocation;
    this.errorDeclarationES = errorDeclarationES;
    this.certificationInfo = certificationInfo;
    this.extension = extension;
    this.userExtensions = userExtensions;
    this.innerUserExtensions = innerUserExtensions;
    this.contextInfo = contextInfo;
    this.sourceList = sourceList;
    this.destinationList = destinationList;
    this.sensorElementList = sensorElementList;
    this.captureID = captureID;
    this.sequenceInEPCISDoc = sequenceInEPCISDoc;
  }

  public EPCISEventES() {
  }
}
