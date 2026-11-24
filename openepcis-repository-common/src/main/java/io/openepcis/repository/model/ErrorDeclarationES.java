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
import io.openepcis.model.epcis.ErrorDeclaration;
import io.openepcis.model.epcis.constants.CBVUrnPrefix;
import io.openepcis.model.epcis.format.CBVFormat;
import io.openepcis.model.epcis.format.EPCFormat;
import io.openepcis.model.epcis.format.FormatPreference;
import io.openepcis.repository.util.CBVFormatUtil;
import io.openepcis.repository.util.EventConvertor;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.apache.commons.collections4.MapUtils;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorDeclarationES implements ESModel<ErrorDeclaration> {
  private CbvES reason;
  private List<String> correctiveEventIDs;
  private OffsetDateTime declarationTime;
  private List<Map<String, Object>> userExtensions;
  private List<Map<String, Object>> innerUserExtensions;

  public ErrorDeclarationES(final ErrorDeclaration errorDeclaration, List<Object> context) {
    this.setReason(CBVFormatUtil.buildCbvESForStandardVocab(errorDeclaration.getReason(), CBVUrnPrefix.ERROR_REASON, context));
    this.setCorrectiveEventIDs(errorDeclaration.getCorrectiveEventIDs());
    this.setDeclarationTime(errorDeclaration.getDeclarationTime());
    this.setUserExtensions(MapUtils.isNotEmpty(errorDeclaration.getUserExtensions()) ? EventConvertor.getUserExtensionsFromCoreModel(context, errorDeclaration.getUserExtensions()) : null);
    this.setInnerUserExtensions(MapUtils.isNotEmpty(errorDeclaration.getInnerUserExtensions()) ? EventConvertor.getUserExtensionsFromCoreModel(context, errorDeclaration.getInnerUserExtensions()) : null);
  }

  @Override
  @JsonIgnore
  public ErrorDeclaration getCoreModel() {
    return ErrorDeclaration.builder().reason(CBVFormatUtil.getCbvInExpectedFormat(this.getReason(), CBVFormat.No_Preference.name())).correctiveEventIDs(this.getCorrectiveEventIDs()).declarationTime(this.getDeclarationTime()).userExtensions(EventConvertor.getCoreModelUserExtensions(this.getUserExtensions())).innerUserExtensions((EventConvertor.getCoreModelUserExtensions(this.innerUserExtensions))).build();
  }

  @Override
  public ErrorDeclaration getCoreModel(List<Object> context) {
    return ErrorDeclaration.builder().reason(CBVFormatUtil.getCbvInExpectedFormat(this.getReason(), CBVFormat.No_Preference.name(), context)).correctiveEventIDs(this.getCorrectiveEventIDs()).declarationTime(this.getDeclarationTime()).userExtensions(EventConvertor.getCoreModelUserExtensions(this.getUserExtensions())).innerUserExtensions((EventConvertor.getCoreModelUserExtensions(this.innerUserExtensions))).build();
  }

  @Override
  public ErrorDeclaration getCoreModel(FormatPreference preference) {
    final String cbvFormat = preference.getCbvFormat() != null ? preference.getCbvFormat().name() : CBVFormat.No_Preference.name();
    return ErrorDeclaration.builder().reason(CBVFormatUtil.getCbvInExpectedFormat(this.getReason(), cbvFormat)).correctiveEventIDs(this.getCorrectiveEventIDs()).declarationTime(this.getDeclarationTime()).userExtensions(EventConvertor.getCoreModelUserExtensions(this.getUserExtensions())).innerUserExtensions((EventConvertor.getCoreModelUserExtensions(this.innerUserExtensions))).build();
  }

  @Override
  public ErrorDeclaration getCoreModel(final FormatPreference preference, final List<Object> context) {
    final String cbvFormat = preference.getCbvFormat() != null ? preference.getCbvFormat().name() : CBVFormat.No_Preference.name();
    final String epcFormat = preference.getEpcFormat() != null ? preference.getEpcFormat().name() : EPCFormat.Always_GS1_Digital_Link.name();
    return ErrorDeclaration.builder().reason(CBVFormatUtil.getCbvInExpectedFormat(this.getReason(), cbvFormat, context)).correctiveEventIDs(this.getCorrectiveEventIDs()).declarationTime(this.getDeclarationTime()).userExtensions(EventConvertor.getCoreModelUserExtensionsWithFormat(this.getUserExtensions(), epcFormat, context)).innerUserExtensions((EventConvertor.getCoreModelUserExtensionsWithFormat(this.innerUserExtensions, epcFormat, context))).build();
  }

  public CbvES getReason() {
    return this.reason;
  }

  public List<String> getCorrectiveEventIDs() {
    return this.correctiveEventIDs;
  }

  public OffsetDateTime getDeclarationTime() {
    return this.declarationTime;
  }

  public List<Map<String, Object>> getUserExtensions() {
    return this.userExtensions;
  }

  public List<Map<String, Object>> getInnerUserExtensions() {
    return this.innerUserExtensions;
  }

  public void setReason(CbvES reason) {
    this.reason = reason;
  }

  public void setCorrectiveEventIDs(List<String> correctiveEventIDs) {
    this.correctiveEventIDs = correctiveEventIDs;
  }

  public void setDeclarationTime(OffsetDateTime declarationTime) {
    this.declarationTime = declarationTime;
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
    if (!(o instanceof ErrorDeclarationES)) return false;
    ErrorDeclarationES other = (ErrorDeclarationES) o;
    if (!other.canEqual((Object) this)) return false;
    Object this$reason = this.getReason();
    Object other$reason = other.getReason();
    if (this$reason == null ? other$reason != null : !this$reason.equals(other$reason)) return false;
    Object this$correctiveEventIDs = this.getCorrectiveEventIDs();
    Object other$correctiveEventIDs = other.getCorrectiveEventIDs();
    if (this$correctiveEventIDs == null ? other$correctiveEventIDs != null : !this$correctiveEventIDs.equals(other$correctiveEventIDs)) return false;
    Object this$declarationTime = this.getDeclarationTime();
    Object other$declarationTime = other.getDeclarationTime();
    if (this$declarationTime == null ? other$declarationTime != null : !this$declarationTime.equals(other$declarationTime)) return false;
    Object this$userExtensions = this.getUserExtensions();
    Object other$userExtensions = other.getUserExtensions();
    if (this$userExtensions == null ? other$userExtensions != null : !this$userExtensions.equals(other$userExtensions)) return false;
    Object this$innerUserExtensions = this.getInnerUserExtensions();
    Object other$innerUserExtensions = other.getInnerUserExtensions();
    if (this$innerUserExtensions == null ? other$innerUserExtensions != null : !this$innerUserExtensions.equals(other$innerUserExtensions)) return false;
    return true;
  }

  protected boolean canEqual(Object other) {
    return other instanceof ErrorDeclarationES;
  }

  @Override
  public int hashCode() {
    int PRIME = 59;
    int result = 1;
    Object $reason = this.getReason();
    result = result * PRIME + ($reason == null ? 43 : $reason.hashCode());
    Object $correctiveEventIDs = this.getCorrectiveEventIDs();
    result = result * PRIME + ($correctiveEventIDs == null ? 43 : $correctiveEventIDs.hashCode());
    Object $declarationTime = this.getDeclarationTime();
    result = result * PRIME + ($declarationTime == null ? 43 : $declarationTime.hashCode());
    Object $userExtensions = this.getUserExtensions();
    result = result * PRIME + ($userExtensions == null ? 43 : $userExtensions.hashCode());
    Object $innerUserExtensions = this.getInnerUserExtensions();
    result = result * PRIME + ($innerUserExtensions == null ? 43 : $innerUserExtensions.hashCode());
    return result;
  }

  @Override
  public String toString() {
    return "ErrorDeclarationES(reason=" + this.getReason() + ", correctiveEventIDs=" + this.getCorrectiveEventIDs() + ", declarationTime=" + this.getDeclarationTime() + ", userExtensions=" + this.getUserExtensions() + ", innerUserExtensions=" + this.getInnerUserExtensions() + ")";
  }

  public ErrorDeclarationES(CbvES reason, List<String> correctiveEventIDs, OffsetDateTime declarationTime, List<Map<String, Object>> userExtensions, List<Map<String, Object>> innerUserExtensions) {
    this.reason = reason;
    this.correctiveEventIDs = correctiveEventIDs;
    this.declarationTime = declarationTime;
    this.userExtensions = userExtensions;
    this.innerUserExtensions = innerUserExtensions;
  }

  public ErrorDeclarationES() {
  }
}
