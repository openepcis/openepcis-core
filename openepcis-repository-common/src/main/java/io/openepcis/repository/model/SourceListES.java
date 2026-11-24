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
import io.openepcis.model.epcis.SourceList;
import io.openepcis.model.epcis.constants.CBVUrnPrefix;
import io.openepcis.model.epcis.format.CBVFormat;
import io.openepcis.model.epcis.format.FormatPreference;
import io.openepcis.repository.util.CBVFormatUtil;
import io.openepcis.repository.util.EPCFormatUtil;
import java.util.List;

public class SourceListES implements ESModel<SourceList> {
  private CbvES type;
  private EpcES source;

  public SourceListES(final SourceList sourceList, List<Object> context) throws ValidationException {
    this.setType(CBVFormatUtil.buildCbvESForStandardVocab(sourceList.getType(), CBVUrnPrefix.SOURCE_DEST_TYPE, context));
    this.setSource(EPCFormatUtil.buildEpc(sourceList.getSource(), context));
  }

  @Override
  @JsonIgnore
  public SourceList getCoreModel() {
    return SourceList.builder().type(CBVFormatUtil.getCbvInExpectedFormat(this.getType(), CBVFormat.No_Preference.name())).source(this.getSource().getAsCaptured()).build();
  }

  @Override
  public SourceList getCoreModel(List<Object> context) {
    return SourceList.builder().type(CBVFormatUtil.getCbvInExpectedFormat(this.getType(), CBVFormat.No_Preference.name(), context)).source(this.getSource().getAsCaptured()).build();
  }

  @Override
  public SourceList getCoreModel(FormatPreference preference) {
    return SourceList.builder().type(CBVFormatUtil.getCbvInExpectedFormat(this.getType(), preference.getCbvFormat().name())).source(EPCFormatUtil.getEpcInExpectedFormat(this.getSource(), preference.getEpcFormat().name())).build();
  }

  @Override
  public SourceList getCoreModel(FormatPreference preference, List<Object> context) {
    return SourceList.builder().type(CBVFormatUtil.getCbvInExpectedFormat(this.getType(), preference.getCbvFormat().name(), context)).source(EPCFormatUtil.getEpcInExpectedFormat(this.getSource(), preference.getEpcFormat().name(), context)).build();
  }

  public CbvES getType() {
    return this.type;
  }

  public EpcES getSource() {
    return this.source;
  }

  public void setType(CbvES type) {
    this.type = type;
  }

  public void setSource(EpcES source) {
    this.source = source;
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) return true;
    if (!(o instanceof SourceListES)) return false;
    SourceListES other = (SourceListES) o;
    if (!other.canEqual((Object) this)) return false;
    Object this$type = this.getType();
    Object other$type = other.getType();
    if (this$type == null ? other$type != null : !this$type.equals(other$type)) return false;
    Object this$source = this.getSource();
    Object other$source = other.getSource();
    if (this$source == null ? other$source != null : !this$source.equals(other$source)) return false;
    return true;
  }

  protected boolean canEqual(Object other) {
    return other instanceof SourceListES;
  }

  @Override
  public int hashCode() {
    int PRIME = 59;
    int result = 1;
    Object $type = this.getType();
    result = result * PRIME + ($type == null ? 43 : $type.hashCode());
    Object $source = this.getSource();
    result = result * PRIME + ($source == null ? 43 : $source.hashCode());
    return result;
  }

  @Override
  public String toString() {
    return "SourceListES(type=" + this.getType() + ", source=" + this.getSource() + ")";
  }

  public SourceListES(CbvES type, EpcES source) {
    this.type = type;
    this.source = source;
  }

  public SourceListES() {
  }
}
