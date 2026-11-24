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
package io.openepcis.repository.util;

import io.openepcis.model.dto.EventLocationInEPCISDocument;
import io.openepcis.model.dto.InvalidEPCISEventInfo;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Condenses a capture's validation entries: entries that say the same thing (type, title, status,
 * detail, severity) become one, carrying every event they were reported for, in the order of
 * first appearance. Positions and event locations are merged, de-duplicated and sorted by index.
 */
public class InvalidEventInfoUtil {
  public static List<InvalidEPCISEventInfo> condenseInvalidInfos(
      List<InvalidEPCISEventInfo> invalidEPCISEventInfos) {
    if (invalidEPCISEventInfos == null || invalidEPCISEventInfos.isEmpty()) {
      return Collections.emptyList();
    }
    final Map<List<Object>, InvalidEPCISEventInfo> condensed = new LinkedHashMap<>();
    final Map<List<Object>, Set<Integer>> sequences = new LinkedHashMap<>();
    final Map<List<Object>, Set<EventLocationInEPCISDocument>> locations = new LinkedHashMap<>();
    for (final InvalidEPCISEventInfo info : invalidEPCISEventInfos) {
      final List<Object> key =
          Arrays.asList(
              info.getType(), info.getTitle(), info.getStatus(), info.getDetail(), info.getSeverity());
      condensed.computeIfAbsent(
          key,
          k ->
              new InvalidEPCISEventInfo(info.getType(), info.getTitle(), info.getStatus(), info.getDetail())
                  .instance(info.getInstance())
                  .severity(info.getSeverity()));
      if (info.getSequenceInEPCISDoc() != null) {
        sequences.computeIfAbsent(key, k -> new LinkedHashSet<>()).addAll(info.getSequenceInEPCISDoc());
      }
      if (info.getEventLocationsInEPCISDocument() != null) {
        locations
            .computeIfAbsent(key, k -> new LinkedHashSet<>())
            .addAll(info.getEventLocationsInEPCISDocument());
      }
    }
    condensed.forEach(
        (key, info) -> {
          if (sequences.containsKey(key)) {
            info.setSequenceInEPCISDoc(List.copyOf(sequences.get(key)));
          }
          if (locations.containsKey(key)) {
            info.setEventLocationsInEPCISDocument(
                locations.get(key).stream()
                    .sorted(
                        Comparator.comparing(
                            EventLocationInEPCISDocument::getIndex,
                            Comparator.nullsLast(Comparator.naturalOrder())))
                    .toList());
          }
        });
    return List.copyOf(condensed.values());
  }
}
