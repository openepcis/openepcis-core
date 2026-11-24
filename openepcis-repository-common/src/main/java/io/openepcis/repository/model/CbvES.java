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

import com.fasterxml.jackson.annotation.JsonInclude;
import java.io.Serializable;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class CbvES implements Serializable {
  private String asCaptured;
  private String asURN;
  private String asURI;
  private String asBareString;

  public String getAsCaptured() {
    return this.asCaptured;
  }

  public String getAsURN() {
    return this.asURN;
  }

  public String getAsURI() {
    return this.asURI;
  }

  public String getAsBareString() {
    return this.asBareString;
  }

  public void setAsCaptured(String asCaptured) {
    this.asCaptured = asCaptured;
  }

  public void setAsURN(String asURN) {
    this.asURN = asURN;
  }

  public void setAsURI(String asURI) {
    this.asURI = asURI;
  }

  public void setAsBareString(String asBareString) {
    this.asBareString = asBareString;
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) return true;
    if (!(o instanceof CbvES)) return false;
    CbvES other = (CbvES) o;
    if (!other.canEqual((Object) this)) return false;
    Object this$asCaptured = this.getAsCaptured();
    Object other$asCaptured = other.getAsCaptured();
    if (this$asCaptured == null ? other$asCaptured != null : !this$asCaptured.equals(other$asCaptured)) return false;
    Object this$asURN = this.getAsURN();
    Object other$asURN = other.getAsURN();
    if (this$asURN == null ? other$asURN != null : !this$asURN.equals(other$asURN)) return false;
    Object this$asURI = this.getAsURI();
    Object other$asURI = other.getAsURI();
    if (this$asURI == null ? other$asURI != null : !this$asURI.equals(other$asURI)) return false;
    Object this$asBareString = this.getAsBareString();
    Object other$asBareString = other.getAsBareString();
    if (this$asBareString == null ? other$asBareString != null : !this$asBareString.equals(other$asBareString)) return false;
    return true;
  }

  protected boolean canEqual(Object other) {
    return other instanceof CbvES;
  }

  @Override
  public int hashCode() {
    int PRIME = 59;
    int result = 1;
    Object $asCaptured = this.getAsCaptured();
    result = result * PRIME + ($asCaptured == null ? 43 : $asCaptured.hashCode());
    Object $asURN = this.getAsURN();
    result = result * PRIME + ($asURN == null ? 43 : $asURN.hashCode());
    Object $asURI = this.getAsURI();
    result = result * PRIME + ($asURI == null ? 43 : $asURI.hashCode());
    Object $asBareString = this.getAsBareString();
    result = result * PRIME + ($asBareString == null ? 43 : $asBareString.hashCode());
    return result;
  }

  @Override
  public String toString() {
    return "CbvES(asCaptured=" + this.getAsCaptured() + ", asURN=" + this.getAsURN() + ", asURI=" + this.getAsURI() + ", asBareString=" + this.getAsBareString() + ")";
  }

  public CbvES(String asCaptured, String asURN, String asURI, String asBareString) {
    this.asCaptured = asCaptured;
    this.asURN = asURN;
    this.asURI = asURI;
    this.asBareString = asBareString;
  }

  public CbvES() {
  }
}
