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
package io.openepcis.query.aggregation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openepcis.query.aggregation.EventAggregation.Bucket;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EventAggregationTest {

  @Test
  @DisplayName("URN and as-captured buckets merge into one top list; what is cut off goes into otherCount")
  void termsMerge() {
    final EventAggregation result =
        EventAggregation.terms(
            EventGroupBy.bizStep,
            100,
            List.of(new Bucket("urn:epcglobal:cbv:bizstep:shipping", 40), new Bucket("urn:epcglobal:cbv:bizstep:receiving", 20)),
            5,
            List.of(new Bucket("https://example.com/bizstep/repair", 30), new Bucket("https://example.com/bizstep/x", 1)),
            2,
            2);
    assertEquals(
        List.of(new Bucket("urn:epcglobal:cbv:bizstep:shipping", 40), new Bucket("https://example.com/bizstep/repair", 30)),
        result.buckets());
    assertEquals(5 + 2 + 20 + 1, result.otherCount());
    assertEquals(100, result.total());
    assertEquals("bizStep", result.groupBy());
  }

  @Test
  @DisplayName("a key in both lists is one group")
  void sameKeyIsOneGroup() {
    final EventAggregation result =
        EventAggregation.terms(EventGroupBy.readPoint, 9, List.of(new Bucket("a", 4)), 0, List.of(new Bucket("a", 3), new Bucket("b", 2)), 0, 20);
    assertEquals(List.of(new Bucket("a", 7), new Bucket("b", 2)), result.buckets());
    assertEquals(0, result.otherCount());
  }

  @Test
  @DisplayName("serialised exactly as the contract says")
  void contract() throws Exception {
    final String json =
        new ObjectMapper()
            .writeValueAsString(
                EventAggregation.terms(EventGroupBy.bizStep, 12345, List.of(new Bucket("urn:epcglobal:cbv:bizstep:shipping", 512)), 17, List.of(), 0, 20));
    assertEquals(
        "{\"groupBy\":\"bizStep\",\"total\":12345,\"buckets\":[{\"key\":\"urn:epcglobal:cbv:bizstep:shipping\",\"count\":512}],\"otherCount\":17}",
        json);
  }

  @Test
  @DisplayName("groupBy names are exact; anything else is unknown")
  void parse() {
    assertEquals(Optional.of(EventGroupBy.day), EventGroupBy.parse("day"));
    assertTrue(EventGroupBy.parse("bizstep").isEmpty());
    assertTrue(EventGroupBy.parse("epc").isEmpty());
    assertTrue(EventGroupBy.parse(null).isEmpty());
  }
}
