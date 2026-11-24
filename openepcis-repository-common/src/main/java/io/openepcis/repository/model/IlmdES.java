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
import io.openepcis.model.epcis.Ilmd;
import io.openepcis.model.epcis.format.FormatPreference;
import io.openepcis.repository.util.EventConvertor;
import java.util.List;
import java.util.Map;
import org.apache.commons.collections4.MapUtils;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class IlmdES implements ESModel<Ilmd> {
  private List<Map<String, Object>> userExtensions;
  private List<Map<String, Object>> innerUserExtensions;

  public IlmdES(final Ilmd ilmd, List<Object> context) {
    this.setUserExtensions(MapUtils.isNotEmpty(ilmd.getUserExtensions()) ? EventConvertor.getUserExtensionsFromCoreModel(context, ilmd.getUserExtensions()) : null);
    this.setInnerUserExtensions(MapUtils.isNotEmpty(ilmd.getInnerUserExtensions()) ? EventConvertor.getUserExtensionsFromCoreModel(context, ilmd.getInnerUserExtensions()) : null);
  }

  @Override
  @JsonIgnore
  public Ilmd getCoreModel() {
    return Ilmd.builder().userExtensions(EventConvertor.getCoreModelUserExtensions(this.getUserExtensions())).innerUserExtensions((EventConvertor.getCoreModelUserExtensions(this.innerUserExtensions))).build();
  }

  @Override
  public Ilmd getCoreModel(List<Object> context) {
    return getCoreModel();
  }

  @Override
  public Ilmd getCoreModel(FormatPreference preference) {
    return getCoreModel();
  }

  @Override
  public Ilmd getCoreModel(FormatPreference preference, List<Object> context) {
    return getCoreModel();
  }

  public List<Map<String, Object>> getUserExtensions() {
    return this.userExtensions;
  }

  public List<Map<String, Object>> getInnerUserExtensions() {
    return this.innerUserExtensions;
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
    if (!(o instanceof IlmdES)) return false;
    IlmdES other = (IlmdES) o;
    if (!other.canEqual((Object) this)) return false;
    Object this$userExtensions = this.getUserExtensions();
    Object other$userExtensions = other.getUserExtensions();
    if (this$userExtensions == null ? other$userExtensions != null : !this$userExtensions.equals(other$userExtensions)) return false;
    Object this$innerUserExtensions = this.getInnerUserExtensions();
    Object other$innerUserExtensions = other.getInnerUserExtensions();
    if (this$innerUserExtensions == null ? other$innerUserExtensions != null : !this$innerUserExtensions.equals(other$innerUserExtensions)) return false;
    return true;
  }

  protected boolean canEqual(Object other) {
    return other instanceof IlmdES;
  }

  @Override
  public int hashCode() {
    int PRIME = 59;
    int result = 1;
    Object $userExtensions = this.getUserExtensions();
    result = result * PRIME + ($userExtensions == null ? 43 : $userExtensions.hashCode());
    Object $innerUserExtensions = this.getInnerUserExtensions();
    result = result * PRIME + ($innerUserExtensions == null ? 43 : $innerUserExtensions.hashCode());
    return result;
  }

  @Override
  public String toString() {
    return "IlmdES(userExtensions=" + this.getUserExtensions() + ", innerUserExtensions=" + this.getInnerUserExtensions() + ")";
  }

  public IlmdES(List<Map<String, Object>> userExtensions, List<Map<String, Object>> innerUserExtensions) {
    this.userExtensions = userExtensions;
    this.innerUserExtensions = innerUserExtensions;
  }

  public IlmdES() {
  }
}
