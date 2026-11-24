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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FindingReportTest {

  @Test
  @DisplayName("JSONPath relative to the event becomes the EPCIS XML path, 1.2 extensions included")
  void xmlPaths() {
    assertEquals("/ObjectEvent/bizLocation", FindingReport.xmlPath("$.bizLocation", "ObjectEvent"));
    assertEquals("/ObjectEvent/extension/ilmd/cbvmda:lotNumber",
        FindingReport.xmlPath("$.ilmd.cbvmda:lotNumber", "ObjectEvent"));
    assertEquals("/ObjectEvent/extension/sourceList", FindingReport.xmlPath("$.sourceList[0]", "ObjectEvent"));
    assertEquals("/AggregationEvent/childEPCs", FindingReport.xmlPath("$.childEPCs[2]", "AggregationEvent"));
    assertNull(FindingReport.xmlPath(null, "ObjectEvent"));
  }

  @Test
  @DisplayName("identifiers are reported as URN and Digital Link, whichever form was captured")
  void identifiersInBothForms() {
    final var fromUrn = FindingReport.identifiers(java.util.List.of("urn:epc:id:sgtin:0614141.107346.2017")).get(0);
    assertEquals("https://id.gs1.org/01/10614141073464/21/2017", fromUrn.getDigitalLink());
    final var fromDl = FindingReport.identifiers(java.util.List.of("https://id.gs1.org/01/10614141073464/21/2017")).get(0);
    assertEquals("urn:epc:id:sgtin:0614141.107346.2017", fromDl.getUrn());
    final var lot = FindingReport.identifiers(java.util.List.of("urn:epc:class:lgtin:0614141.107346.LOT1")).get(0);
    assertEquals("https://id.gs1.org/01/10614141073464/10/LOT1", lot.getDigitalLink());
  }
}
