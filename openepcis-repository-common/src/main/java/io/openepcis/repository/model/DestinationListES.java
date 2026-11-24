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
import io.openepcis.model.epcis.DestinationList;
import io.openepcis.model.epcis.constants.CBVUrnPrefix;
import io.openepcis.model.epcis.format.CBVFormat;
import io.openepcis.model.epcis.format.EPCFormat;
import io.openepcis.model.epcis.format.FormatPreference;
import io.openepcis.repository.util.CBVFormatUtil;
import io.openepcis.repository.util.EPCFormatUtil;
import java.util.List;

public class DestinationListES implements ESModel<DestinationList> {
  private CbvES type;
  private EpcES destination;

  public DestinationListES(final DestinationList destinationList, List<Object> context) throws ValidationException {
    this.setType(CBVFormatUtil.buildCbvESForStandardVocab(destinationList.getType(), CBVUrnPrefix.SOURCE_DEST_TYPE, context));
    this.setDestination(EPCFormatUtil.buildEpc(destinationList.getDestination(), context));
  }

  @Override
  @JsonIgnore
  public DestinationList getCoreModel() {
    return DestinationList.builder().destination(this.getDestination().getAsCaptured()).type(CBVFormatUtil.getCbvInExpectedFormat(this.getType(), CBVFormat.No_Preference.name())).build();
  }

  @Override
  public DestinationList getCoreModel(List<Object> context) {
    return DestinationList.builder().destination(this.getDestination().getAsCaptured()).type(CBVFormatUtil.getCbvInExpectedFormat(this.getType(), CBVFormat.No_Preference.name(), context)).build();
  }

  @Override
  public DestinationList getCoreModel(FormatPreference preference) {
    final String epcFormat = preference.getEpcFormat() != null ? preference.getEpcFormat().name() : EPCFormat.Always_GS1_Digital_Link.name();
    final String cbvFormat = preference.getCbvFormat() != null ? preference.getCbvFormat().name() : CBVFormat.No_Preference.name();
    return DestinationList.builder().type(CBVFormatUtil.getCbvInExpectedFormat(this.getType(), cbvFormat)).destination(EPCFormatUtil.getEpcInExpectedFormat(this.getDestination(), epcFormat)).build();
  }

  @Override
  public DestinationList getCoreModel(FormatPreference preference, List<Object> context) {
    final String epcFormat = preference.getEpcFormat() != null ? preference.getEpcFormat().name() : EPCFormat.Always_GS1_Digital_Link.name();
    final String cbvFormat = preference.getCbvFormat() != null ? preference.getCbvFormat().name() : CBVFormat.No_Preference.name();
    return DestinationList.builder().type(CBVFormatUtil.getCbvInExpectedFormat(this.getType(), cbvFormat, context)).destination(EPCFormatUtil.getEpcInExpectedFormat(this.getDestination(), epcFormat, context)).build();
  }

  public CbvES getType() {
    return this.type;
  }

  public EpcES getDestination() {
    return this.destination;
  }

  public void setType(CbvES type) {
    this.type = type;
  }

  public void setDestination(EpcES destination) {
    this.destination = destination;
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) return true;
    if (!(o instanceof DestinationListES)) return false;
    DestinationListES other = (DestinationListES) o;
    if (!other.canEqual((Object) this)) return false;
    Object this$type = this.getType();
    Object other$type = other.getType();
    if (this$type == null ? other$type != null : !this$type.equals(other$type)) return false;
    Object this$destination = this.getDestination();
    Object other$destination = other.getDestination();
    if (this$destination == null ? other$destination != null : !this$destination.equals(other$destination)) return false;
    return true;
  }

  protected boolean canEqual(Object other) {
    return other instanceof DestinationListES;
  }

  @Override
  public int hashCode() {
    int PRIME = 59;
    int result = 1;
    Object $type = this.getType();
    result = result * PRIME + ($type == null ? 43 : $type.hashCode());
    Object $destination = this.getDestination();
    result = result * PRIME + ($destination == null ? 43 : $destination.hashCode());
    return result;
  }

  @Override
  public String toString() {
    return "DestinationListES(type=" + this.getType() + ", destination=" + this.getDestination() + ")";
  }

  public DestinationListES(CbvES type, EpcES destination) {
    this.type = type;
    this.destination = destination;
  }

  public DestinationListES() {
  }
}
