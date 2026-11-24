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
import io.openepcis.model.epcis.QuantityList;
import io.openepcis.model.epcis.format.EPCFormat;
import io.openepcis.model.epcis.format.FormatPreference;
import io.openepcis.repository.util.EPCFormatUtil;
import java.util.List;

public class QuantityListES implements ESModel<QuantityList> {
  private EpcES epcClass;
  private Float quantity;
  private String uom;

  public QuantityListES(QuantityList quantityList, List<Object> context) throws ValidationException {
    this.setEpcClass(EPCFormatUtil.buildClassLevelEpc(quantityList.getEpcClass(), context));
    this.setQuantity(quantityList.getQuantity());
    this.setUom(quantityList.getUom());
  }

  @Override
  @JsonIgnore
  public QuantityList getCoreModel() {
    return QuantityList.builder().epcClass(this.getEpcClass().getAsCaptured()).quantity(this.getQuantity()).uom(this.getUom()).build();
  }

  @Override
  public QuantityList getCoreModel(List<Object> context) {
    return getCoreModel();
  }

  @Override
  public QuantityList getCoreModel(FormatPreference preference) {
    final String epcFormat = preference.getEpcFormat() != null ? preference.getEpcFormat().name() : EPCFormat.Always_GS1_Digital_Link.name();
    return QuantityList.builder().epcClass(EPCFormatUtil.getEpcInExpectedFormat(this.getEpcClass(), epcFormat)).quantity(this.getQuantity()).uom(this.getUom()).build();
  }

  @Override
  public QuantityList getCoreModel(FormatPreference preference, List<Object> context) {
    final String epcFormat = preference.getEpcFormat() != null ? preference.getEpcFormat().name() : EPCFormat.Always_GS1_Digital_Link.name();
    return QuantityList.builder().epcClass(EPCFormatUtil.getEpcInExpectedFormat(this.getEpcClass(), epcFormat, context)).quantity(this.getQuantity()).uom(this.getUom()).build();
  }

  public EpcES getEpcClass() {
    return this.epcClass;
  }

  public Float getQuantity() {
    return this.quantity;
  }

  public String getUom() {
    return this.uom;
  }

  public void setEpcClass(EpcES epcClass) {
    this.epcClass = epcClass;
  }

  public void setQuantity(Float quantity) {
    this.quantity = quantity;
  }

  public void setUom(String uom) {
    this.uom = uom;
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) return true;
    if (!(o instanceof QuantityListES)) return false;
    QuantityListES other = (QuantityListES) o;
    if (!other.canEqual((Object) this)) return false;
    Object this$quantity = this.getQuantity();
    Object other$quantity = other.getQuantity();
    if (this$quantity == null ? other$quantity != null : !this$quantity.equals(other$quantity)) return false;
    Object this$epcClass = this.getEpcClass();
    Object other$epcClass = other.getEpcClass();
    if (this$epcClass == null ? other$epcClass != null : !this$epcClass.equals(other$epcClass)) return false;
    Object this$uom = this.getUom();
    Object other$uom = other.getUom();
    if (this$uom == null ? other$uom != null : !this$uom.equals(other$uom)) return false;
    return true;
  }

  protected boolean canEqual(Object other) {
    return other instanceof QuantityListES;
  }

  @Override
  public int hashCode() {
    int PRIME = 59;
    int result = 1;
    Object $quantity = this.getQuantity();
    result = result * PRIME + ($quantity == null ? 43 : $quantity.hashCode());
    Object $epcClass = this.getEpcClass();
    result = result * PRIME + ($epcClass == null ? 43 : $epcClass.hashCode());
    Object $uom = this.getUom();
    result = result * PRIME + ($uom == null ? 43 : $uom.hashCode());
    return result;
  }

  @Override
  public String toString() {
    return "QuantityListES(epcClass=" + this.getEpcClass() + ", quantity=" + this.getQuantity() + ", uom=" + this.getUom() + ")";
  }

  public QuantityListES(EpcES epcClass, Float quantity, String uom) {
    this.epcClass = epcClass;
    this.quantity = quantity;
    this.uom = uom;
  }

  public QuantityListES() {
  }
}
