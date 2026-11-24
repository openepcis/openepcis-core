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
public class EpcES implements Serializable {
  private String asCaptured;
  private String asURN;
  private String canonicalDL;

  public String getAsCaptured() {
    return this.asCaptured;
  }

  public String getAsURN() {
    return this.asURN;
  }

  public String getCanonicalDL() {
    return this.canonicalDL;
  }

  public void setAsCaptured(String asCaptured) {
    this.asCaptured = asCaptured;
  }

  public void setAsURN(String asURN) {
    this.asURN = asURN;
  }

  public void setCanonicalDL(String canonicalDL) {
    this.canonicalDL = canonicalDL;
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) return true;
    if (!(o instanceof EpcES)) return false;
    EpcES other = (EpcES) o;
    if (!other.canEqual((Object) this)) return false;
    Object this$asCaptured = this.getAsCaptured();
    Object other$asCaptured = other.getAsCaptured();
    if (this$asCaptured == null ? other$asCaptured != null : !this$asCaptured.equals(other$asCaptured)) return false;
    Object this$asURN = this.getAsURN();
    Object other$asURN = other.getAsURN();
    if (this$asURN == null ? other$asURN != null : !this$asURN.equals(other$asURN)) return false;
    Object this$canonicalDL = this.getCanonicalDL();
    Object other$canonicalDL = other.getCanonicalDL();
    if (this$canonicalDL == null ? other$canonicalDL != null : !this$canonicalDL.equals(other$canonicalDL)) return false;
    return true;
  }

  protected boolean canEqual(Object other) {
    return other instanceof EpcES;
  }

  @Override
  public int hashCode() {
    int PRIME = 59;
    int result = 1;
    Object $asCaptured = this.getAsCaptured();
    result = result * PRIME + ($asCaptured == null ? 43 : $asCaptured.hashCode());
    Object $asURN = this.getAsURN();
    result = result * PRIME + ($asURN == null ? 43 : $asURN.hashCode());
    Object $canonicalDL = this.getCanonicalDL();
    result = result * PRIME + ($canonicalDL == null ? 43 : $canonicalDL.hashCode());
    return result;
  }

  @Override
  public String toString() {
    return "EpcES(asCaptured=" + this.getAsCaptured() + ", asURN=" + this.getAsURN() + ", canonicalDL=" + this.getCanonicalDL() + ")";
  }

  public EpcES(String asCaptured, String asURN, String canonicalDL) {
    this.asCaptured = asCaptured;
    this.asURN = asURN;
    this.canonicalDL = canonicalDL;
  }

  public EpcES() {
  }
}
