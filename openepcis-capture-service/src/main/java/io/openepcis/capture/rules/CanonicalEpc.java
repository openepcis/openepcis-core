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
package io.openepcis.capture.rules;

import io.openepcis.repository.model.EpcES;
import io.openepcis.repository.util.EPCFormatUtil;

/**
 * An EPC in its URN and canonical Digital Link form, whichever form was captured. Instance EPCs
 * convert with the instance rules; what does not convert as an instance (LGTIN, GTIN) is tried as
 * a class.
 *
 * @param asCaptured the value in the event
 * @param urn URN form, or {@code null}
 * @param canonicalDL canonical Digital Link, or {@code null} when it is not a GS1 key
 * @param classLevel whether it identifies a class (lot, product) rather than one instance
 */
public record CanonicalEpc(String asCaptured, String urn, String canonicalDL, boolean classLevel) {

  public static CanonicalEpc of(final String epc) {
    EpcES instance = null;
    try {
      instance = EPCFormatUtil.buildEpc(epc);
    } catch (Exception ignored) {
      // tried as a class below
    }
    if (instance != null && instance.getCanonicalDL() != null && instance.getAsURN() != null) {
      return new CanonicalEpc(epc, instance.getAsURN(), instance.getCanonicalDL(), false);
    }
    try {
      final EpcES asClass = EPCFormatUtil.buildClassLevelEpc(epc);
      if (asClass != null && asClass.getCanonicalDL() != null && asClass.getAsURN() != null) {
        return new CanonicalEpc(epc, asClass.getAsURN(), asClass.getCanonicalDL(), true);
      }
    } catch (Exception ignored) {
      // not a GS1 class either
    }
    final boolean urnForm = epc.startsWith("urn:");
    return new CanonicalEpc(
        epc,
        instance != null && instance.getAsURN() != null ? instance.getAsURN() : (urnForm ? epc : null),
        instance != null && instance.getCanonicalDL() != null ? instance.getCanonicalDL() : (urnForm ? null : epc),
        false);
  }

  /** Whether it is a GS1 key the register can hold. */
  public boolean registrable() {
    return canonicalDL != null && urn != null;
  }
}
