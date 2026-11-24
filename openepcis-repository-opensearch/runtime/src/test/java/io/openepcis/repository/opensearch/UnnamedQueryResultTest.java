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
package io.openepcis.repository.opensearch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openepcis.model.epcis.EpcisQueryResult;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** EPCIS 2.0 requires queryName in every QueryResults; GET /events had none. */
class UnnamedQueryResultTest {

  @Test
  @DisplayName("an unnamed event query answers as SimpleEventQuery")
  void unnamedQueryIsSimpleEventQuery() {
    final EpcisQueryResult result = QueryRepositoryImpl.unnamedQueryResult(List.of());
    assertEquals("SimpleEventQuery", result.getQueryName());
    assertTrue(result.getResultsBody().getEventList().isEmpty());
  }
}
