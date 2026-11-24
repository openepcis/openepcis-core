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
import io.openepcis.query.factory.QueryParamMapper;
import io.openepcis.query.util.UnitConverter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A sensor-value filter compares against a number, however the caller wrote it.
 *
 * <p>Saving the named query {@code {"GT_value_CEL": 25}} on epc.is on 2026-11-17 answered "class
 * java.lang.String cannot be cast to class java.lang.Number": the single value went to the unit
 * converter as text, only a list was parsed.
 */
class ValueUomQueryParamTest {

  private static final UnitConverter UNITS = new UnitConverter(() -> 1);

  @SuppressWarnings("unchecked")
  private static Map<String, Number> values(final String name, final Object value) {
    final QueryParam param =
        new QueryParamMapper(false, UNITS, Optional.empty()).apply(Map.entry(name, value));
    return (Map<String, Number>) param.getValue();
  }

  @Test
  @DisplayName("a single number, as a JSON body sends it, is compared as that number")
  void singleNumber() {
    assertEquals(25.0, values("GT_value_CEL", 25).get("CEL").doubleValue());
  }

  @Test
  @DisplayName("a single number written as text means the same")
  void singleText() {
    assertEquals(25.0, values("GT_value_CEL", "25").get("CEL").doubleValue());
  }

  @Test
  @DisplayName("a list still works as before")
  void list() {
    assertEquals(25.0, values("GT_value_CEL", List.of("25")).get("CEL").doubleValue());
  }

  @Test
  @DisplayName("the other units are derived from the given one")
  void convertsToRelatedUnits() {
    assertEquals(77.0, values("GT_value_CEL", 25).get("FAH").doubleValue(), 0.001);
  }

  @Test
  @DisplayName("something that is no number is the caller's mistake, not a server error")
  void notANumber() {
    assertThrows(QueryParameterException.class, () -> values("GT_value_CEL", "warm"));
  }
}
