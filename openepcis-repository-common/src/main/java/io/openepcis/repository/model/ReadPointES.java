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
import io.openepcis.core.exception.ValidationException;
import io.openepcis.model.epcis.ReadPoint;
import io.openepcis.model.epcis.format.EPCFormat;
import io.openepcis.model.epcis.format.FormatPreference;
import io.openepcis.repository.util.EPCFormatUtil;
import io.openepcis.repository.util.EventConvertor;
import org.apache.commons.collections4.MapUtils;
import java.util.List;
import java.util.Map;

public class ReadPointES implements ESModel<ReadPoint> {
  private EpcES id;
  private List<Map<String, Object>> userExtensions;
  private List<Map<String, Object>> innerUserExtensions;

  public ReadPointES(final ReadPoint readPoint, List<Object> context) throws ValidationException {
    this.setId(EPCFormatUtil.buildEpc(readPoint.getId().toString(), context));
    this.setUserExtensions(MapUtils.isNotEmpty(readPoint.getUserExtensions()) ? EventConvertor.getUserExtensionsFromCoreModel(context, readPoint.getUserExtensions()) : null);
    this.setInnerUserExtensions(MapUtils.isNotEmpty(readPoint.getInnerUserExtensions()) ? EventConvertor.getUserExtensionsFromCoreModel(context, readPoint.getInnerUserExtensions()) : null);
  }

  @Override
  @JsonIgnore
  public ReadPoint getCoreModel() {
    return ReadPoint.builder().id(EPCFormatUtil.getEpcAsExpected(this.getId(), EPCFormat.Always_GS1_Digital_Link.name())).userExtensions(EventConvertor.getCoreModelUserExtensions(this.getUserExtensions())).innerUserExtensions((EventConvertor.getCoreModelUserExtensions(this.innerUserExtensions))).build();
  }

  @Override
  @JsonIgnore
  public ReadPoint getCoreModel(List<Object> context) {
    return ReadPoint.builder().id(EPCFormatUtil.getEpcAsExpected(this.getId(), EPCFormat.Always_GS1_Digital_Link.name(), context)).userExtensions(EventConvertor.getCoreModelUserExtensions(this.getUserExtensions())).innerUserExtensions((EventConvertor.getCoreModelUserExtensions(this.innerUserExtensions))).build();
  }

  @Override
  public ReadPoint getCoreModel(FormatPreference preference) {
    return ReadPoint.builder().id(EPCFormatUtil.getEpcAsExpected(this.getId(), preference.getEpcFormat().name())).userExtensions(EventConvertor.getCoreModelUserExtensions(this.getUserExtensions())).innerUserExtensions((EventConvertor.getCoreModelUserExtensions(this.innerUserExtensions))).build();
  }

  @Override
  public ReadPoint getCoreModel(FormatPreference preference, List<Object> context) {
    final String epcFormat = preference.getEpcFormat() != null ? preference.getEpcFormat().name() : EPCFormat.Always_GS1_Digital_Link.name();
    return ReadPoint.builder().id(EPCFormatUtil.getEpcAsExpected(this.getId(), epcFormat, context)).userExtensions(EventConvertor.getCoreModelUserExtensionsWithFormat(this.getUserExtensions(), epcFormat, context)).innerUserExtensions((EventConvertor.getCoreModelUserExtensionsWithFormat(this.innerUserExtensions, epcFormat, context))).build();
  }

  public EpcES getId() {
    return this.id;
  }

  public List<Map<String, Object>> getUserExtensions() {
    return this.userExtensions;
  }

  public List<Map<String, Object>> getInnerUserExtensions() {
    return this.innerUserExtensions;
  }

  public void setId(EpcES id) {
    this.id = id;
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
    if (!(o instanceof ReadPointES)) return false;
    ReadPointES other = (ReadPointES) o;
    if (!other.canEqual((Object) this)) return false;
    Object this$id = this.getId();
    Object other$id = other.getId();
    if (this$id == null ? other$id != null : !this$id.equals(other$id)) return false;
    Object this$userExtensions = this.getUserExtensions();
    Object other$userExtensions = other.getUserExtensions();
    if (this$userExtensions == null ? other$userExtensions != null : !this$userExtensions.equals(other$userExtensions)) return false;
    Object this$innerUserExtensions = this.getInnerUserExtensions();
    Object other$innerUserExtensions = other.getInnerUserExtensions();
    if (this$innerUserExtensions == null ? other$innerUserExtensions != null : !this$innerUserExtensions.equals(other$innerUserExtensions)) return false;
    return true;
  }

  protected boolean canEqual(Object other) {
    return other instanceof ReadPointES;
  }

  @Override
  public int hashCode() {
    int PRIME = 59;
    int result = 1;
    Object $id = this.getId();
    result = result * PRIME + ($id == null ? 43 : $id.hashCode());
    Object $userExtensions = this.getUserExtensions();
    result = result * PRIME + ($userExtensions == null ? 43 : $userExtensions.hashCode());
    Object $innerUserExtensions = this.getInnerUserExtensions();
    result = result * PRIME + ($innerUserExtensions == null ? 43 : $innerUserExtensions.hashCode());
    return result;
  }

  @Override
  public String toString() {
    return "ReadPointES(id=" + this.getId() + ", userExtensions=" + this.getUserExtensions() + ", innerUserExtensions=" + this.getInnerUserExtensions() + ")";
  }

  public ReadPointES(EpcES id, List<Map<String, Object>> userExtensions, List<Map<String, Object>> innerUserExtensions) {
    this.id = id;
    this.userExtensions = userExtensions;
    this.innerUserExtensions = innerUserExtensions;
  }

  public ReadPointES() {
  }
}
