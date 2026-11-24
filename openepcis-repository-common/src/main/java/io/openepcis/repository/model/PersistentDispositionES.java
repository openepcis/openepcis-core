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
import io.openepcis.model.epcis.PersistentDisposition;
import io.openepcis.model.epcis.constants.CBVUrnPrefix;
import io.openepcis.model.epcis.format.CBVFormat;
import io.openepcis.model.epcis.format.FormatPreference;
import io.openepcis.repository.util.CBVFormatUtil;
import java.util.List;

public class PersistentDispositionES implements ESModel<PersistentDisposition> {
  private List<CbvES> set;
  private List<CbvES> unset;

  public PersistentDispositionES(final PersistentDisposition persistentDisposition, List<Object> context) {
    this.setSet(CBVFormatUtil.buildCbvESListForStandardVocab(persistentDisposition.getSet(), CBVUrnPrefix.DISPOSITION, context));
    this.setUnset(CBVFormatUtil.buildCbvESListForStandardVocab(persistentDisposition.getUnset(), CBVUrnPrefix.DISPOSITION, context));
  }

  @Override
  @JsonIgnore
  public PersistentDisposition getCoreModel() {
    return PersistentDisposition.builder().set(CBVFormatUtil.getCbvAsExpected(this.getSet(), CBVFormat.No_Preference.name())).unset(CBVFormatUtil.getCbvAsExpected(this.getUnset(), CBVFormat.No_Preference.name())).build();
  }

  @Override
  public PersistentDisposition getCoreModel(List<Object> context) {
    return PersistentDisposition.builder().set(CBVFormatUtil.getCbvAsExpected(this.getSet(), CBVFormat.No_Preference.name(), context)).unset(CBVFormatUtil.getCbvAsExpected(this.getUnset(), CBVFormat.No_Preference.name(), context)).build();
  }

  @Override
  public PersistentDisposition getCoreModel(FormatPreference preference) {
    return PersistentDisposition.builder().set(CBVFormatUtil.getCbvAsExpected(this.getSet(), preference.getCbvFormat().name())).unset(CBVFormatUtil.getCbvAsExpected(this.getUnset(), preference.getCbvFormat().name())).build();
  }

  @Override
  public PersistentDisposition getCoreModel(FormatPreference preference, List<Object> context) {
    return PersistentDisposition.builder().set(CBVFormatUtil.getCbvAsExpected(this.getSet(), preference.getCbvFormat().name(), context)).unset(CBVFormatUtil.getCbvAsExpected(this.getUnset(), preference.getCbvFormat().name(), context)).build();
  }

  public List<CbvES> getSet() {
    return this.set;
  }

  public List<CbvES> getUnset() {
    return this.unset;
  }

  public void setSet(List<CbvES> set) {
    this.set = set;
  }

  public void setUnset(List<CbvES> unset) {
    this.unset = unset;
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) return true;
    if (!(o instanceof PersistentDispositionES)) return false;
    PersistentDispositionES other = (PersistentDispositionES) o;
    if (!other.canEqual((Object) this)) return false;
    Object this$set = this.getSet();
    Object other$set = other.getSet();
    if (this$set == null ? other$set != null : !this$set.equals(other$set)) return false;
    Object this$unset = this.getUnset();
    Object other$unset = other.getUnset();
    if (this$unset == null ? other$unset != null : !this$unset.equals(other$unset)) return false;
    return true;
  }

  protected boolean canEqual(Object other) {
    return other instanceof PersistentDispositionES;
  }

  @Override
  public int hashCode() {
    int PRIME = 59;
    int result = 1;
    Object $set = this.getSet();
    result = result * PRIME + ($set == null ? 43 : $set.hashCode());
    Object $unset = this.getUnset();
    result = result * PRIME + ($unset == null ? 43 : $unset.hashCode());
    return result;
  }

  @Override
  public String toString() {
    return "PersistentDispositionES(set=" + this.getSet() + ", unset=" + this.getUnset() + ")";
  }

  public PersistentDispositionES() {
  }

  public PersistentDispositionES(List<CbvES> set, List<CbvES> unset) {
    this.set = set;
    this.unset = unset;
  }
}
