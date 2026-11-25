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
package io.openepcis.capture.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.openepcis.constants.EPCISVersion;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class CaptureEventManagementServiceVersionTest {

  private static CaptureEventService service(
      final EPCISVersion min, final EPCISVersion standard, final EPCISVersion max) {
    final CaptureEventService service = mock(CaptureEventService.class);
    when(service.getEPCISVersionMin()).thenReturn(min);
    when(service.getEPCISVersion()).thenReturn(standard);
    when(service.getEPCISVersionMax()).thenReturn(max);
    return service;
  }

  @Test
  void rangeIsTheUnionOfTheServicesRanges() {
    // as in the repository: XML 1.2, XML 2.0 and JSON 2.0, which also accepts 2.0.1
    final CaptureEventManagementService management =
        () ->
            Stream.of(
                service(EPCISVersion.VERSION_1_2_0, EPCISVersion.VERSION_1_2_0, EPCISVersion.VERSION_1_2_0),
                service(EPCISVersion.VERSION_2_0_0, EPCISVersion.VERSION_2_0_0, EPCISVersion.VERSION_2_0_0),
                service(EPCISVersion.VERSION_2_0_0, EPCISVersion.VERSION_2_0_0, EPCISVersion.VERSION_2_0_1));
    assertEquals(EPCISVersion.VERSION_1_2_0, management.getEPCISVersionMin());
    assertEquals(EPCISVersion.VERSION_2_0_1, management.getEPCISVersionMax());
  }
}
