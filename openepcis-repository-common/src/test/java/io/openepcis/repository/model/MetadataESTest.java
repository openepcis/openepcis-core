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

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.openepcis.repository.Constants;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MetadataESTest {

  private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-23T10:00:00Z");

  private static Map<String, Object> metadata() {
    final Map<String, Object> metadata = new HashMap<>();
    metadata.put(Constants.CAPTURED_BY, Map.of(Constants.USER_ID, "u1", Constants.DEFAULT_GROUP, "acme"));
    return metadata;
  }

  @Test
  void theCapturerAlone() {
    final MetadataES es = MetadataES.fromMetadata(metadata(), "c1", NOW);
    assertEquals(List.of("acme"), es.getCapturedBy().stream().map(CapturedByES::getDefaultGroup).toList());
  }

  @Test
  void furtherGroupsFollowTheCapturerOnceEach() {
    final Map<String, Object> metadata = metadata();
    metadata.put(
        Constants.ALSO_CAPTURED_FOR,
        List.of(
            Map.of(Constants.DEFAULT_GROUP, "globex", Constants.USER_ID, "u2"),
            Map.of(Constants.DEFAULT_GROUP, "ACME"),
            Map.of(Constants.DEFAULT_GROUP, " "),
            "not a map"));
    final MetadataES es = MetadataES.fromMetadata(metadata, "c1", NOW);
    assertEquals(List.of("acme", "globex"), es.getCapturedBy().stream().map(CapturedByES::getDefaultGroup).toList());
    assertEquals("u2", es.getCapturedBy().get(1).getUserID());
    assertEquals("c1", es.getCapturedBy().get(1).getCaptureID());
  }
}
