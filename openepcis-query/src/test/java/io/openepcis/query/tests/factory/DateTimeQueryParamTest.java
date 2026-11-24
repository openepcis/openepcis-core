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
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openepcis.model.epcis.exception.QueryParameterException;
import io.openepcis.query.data.QueryParam;
import io.openepcis.query.factory.QueryParamMapper;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A timestamp the caller cannot spell is the caller's mistake. It used to be ours:
 * {@code OffsetDateTime.parse} throws an unchecked {@code DateTimeParseException},
 * which travelled past every catch in the validation chain and came out of the
 * generic mapper as a 500. The value that provoked it in the field was a whole
 * query string — {@code GE_recordTime=2026-08-27T00:00:00.000Z&perPage=3} left
 * unquoted on the command line, so the {@code &} never separated anything and the
 * remainder arrived as one parameter value.
 *
 * <p>The unit converter is only consulted for PAIR-typed parameters, so date
 * parameters can be mapped without one.
 */
class DateTimeQueryParamTest {

  private static QueryParam map(final String name, final Object value) {
    return new QueryParamMapper(true, null, Optional.empty())
        .apply(Map.entry(name, value));
  }

  @Test
  @DisplayName("a timestamp with an offset is accepted and carried as OffsetDateTime")
  void wellFormedTimestamp() {
    final QueryParam param = map("GE_recordTime", List.of("2026-08-27T00:00:00.000Z"));

    assertEquals("GE_recordTime", param.getName());
    assertEquals(OffsetDateTime.parse("2026-08-27T00:00:00Z"), param.getValue());
  }

  @Test
  @DisplayName("an unquoted query string is a client error, and says which parameter")
  void unparseableTimestampNamesTheParameter() {
    final QueryParameterException thrown =
        assertThrows(
            QueryParameterException.class,
            () -> map("GE_recordTime", List.of("2026-08-27T00:00:00.000Z&perPage=3")));

    assertTrue(
        thrown.getMessage().contains("GE_recordTime"),
        () -> "the offending parameter has to be named, was: " + thrown.getMessage());
  }

  @Test
  @DisplayName("a timestamp without an offset is refused rather than guessed at")
  void timestampWithoutOffset() {
    assertThrows(
        QueryParameterException.class, () -> map("GE_eventTime", List.of("2026-08-27T00:00:00")));
  }

  @Test
  @DisplayName("a value that is not a timestamp at all is reported as such")
  void wrongTypeAltogether() {
    assertThrows(QueryParameterException.class, () -> map("LT_recordTime", List.of(42L)));
  }
}
