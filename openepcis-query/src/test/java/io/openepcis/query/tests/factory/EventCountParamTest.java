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
package io.openepcis.query.tests.factory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.openepcis.model.epcis.exception.QueryParameterException;
import io.openepcis.query.data.QueryParam;
import io.openepcis.query.data.SimpleEventQueryRequest;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * eventCountLimit and maxEventCount arrive as text from a query string. eventCountLimit used to be
 * cast to Integer, the cast failed, was logged, and the limit was silently dropped.
 */
class EventCountParamTest {

  private static SimpleEventQueryRequest request(final QueryParam... params) {
    return new SimpleEventQueryRequest(List.of(params));
  }

  @Test
  @DisplayName("eventCountLimit from a query string limits the result")
  void eventCountLimitFromText() {
    final SimpleEventQueryRequest request =
        request(new QueryParam("orderBy", "eventTime"), new QueryParam("eventCountLimit", "50"));
    assertEquals(50, request.getEventCountLimit());
  }

  @Test
  @DisplayName("a number or a list of one is read as well")
  void numberAndListOfOne() {
    assertEquals(7, request(new QueryParam("orderBy", "eventTime"), new QueryParam("eventCountLimit", 7)).getEventCountLimit());
    assertEquals(3, request(new QueryParam("orderBy", "eventTime"), new QueryParam("eventCountLimit", List.of("3"))).getEventCountLimit());
    assertEquals(9, request(new QueryParam("maxEventCount", "9")).getMaxEventCount());
  }

  @Test
  @DisplayName("without orderBy, and with something that is no number, the caller gets a parameter error")
  void callerErrors() {
    assertThrows(QueryParameterException.class, () -> request(new QueryParam("eventCountLimit", "5")));
    assertThrows(QueryParameterException.class,
        () -> request(new QueryParam("orderBy", "eventTime"), new QueryParam("eventCountLimit", "five")));
    assertThrows(QueryParameterException.class, () -> request(new QueryParam("maxEventCount", "1.5")));
  }
}
