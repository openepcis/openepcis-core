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

import io.openepcis.model.dto.EventLocationInEPCISDocument;
import io.openepcis.model.dto.InvalidEPCISEventInfo;
import io.openepcis.repository.util.InvalidEventInfoUtil;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CondenseReportTest {

  private static InvalidEPCISEventInfo entry(final String detail, final InvalidEPCISEventInfo.Severity severity, final int index) {
    return new InvalidEPCISEventInfo("urn:openepcis:validation:gln-known", "Unknown GLN", 400, detail, List.of(index))
        .severity(severity)
        .eventLocationsInEPCISDocument(List.of(new EventLocationInEPCISDocument().index(index).jsonPath("$.bizLocation")));
  }

  @Test
  @DisplayName("the same entry for several events becomes one, with every location, sorted")
  void sameEntryMerges() {
    final List<InvalidEPCISEventInfo> condensed = InvalidEventInfoUtil.condenseInvalidInfos(List.of(
        entry("GLN 952 unknown", InvalidEPCISEventInfo.Severity.HARD_ERROR, 7),
        entry("other", InvalidEPCISEventInfo.Severity.HARD_ERROR, 1),
        entry("GLN 952 unknown", InvalidEPCISEventInfo.Severity.HARD_ERROR, 2),
        entry("GLN 952 unknown", InvalidEPCISEventInfo.Severity.HARD_ERROR, 7)));
    assertEquals(2, condensed.size());
    assertEquals("GLN 952 unknown", condensed.get(0).getDetail(), "order of first appearance");
    assertEquals(List.of(7, 2), condensed.get(0).getSequenceInEPCISDoc());
    assertEquals(List.of(2, 7),
        condensed.get(0).getEventLocationsInEPCISDocument().stream().map(EventLocationInEPCISDocument::getIndex).toList());
  }

  @Test
  @DisplayName("a soft and a hard entry with the same text stay apart")
  void severityKeepsApart() {
    assertEquals(2, InvalidEventInfoUtil.condenseInvalidInfos(List.of(
        entry("same", InvalidEPCISEventInfo.Severity.HARD_ERROR, 1),
        entry("same", InvalidEPCISEventInfo.Severity.SOFT_ERROR, 2))).size());
  }
}
