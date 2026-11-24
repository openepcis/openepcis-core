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

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openepcis.query.data.QueryParamName;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * GET /epcs/{epc}/events asks with the parameter EPC for every event that names the EPC, at
 * instance or class level. It has to look wherever MATCH_anyEPC and MATCH_anyEPCClass look;
 * a lot named only in a TransformationEvent's input or output quantities was invisible there.
 */
class EpcParamCoverageTest {

  @Test
  void epcCoversEveryFieldOfAnyEpcAndAnyEpcClass() {
    Set<String> wanted = new HashSet<>(QueryParamName.MATCH_anyEPC.getKeys().keySet());
    wanted.addAll(QueryParamName.MATCH_anyEPCClass.getKeys().keySet());
    Set<String> missing = new HashSet<>(wanted);
    missing.removeAll(QueryParamName.EPC.getKeys().keySet());
    assertTrue(missing.isEmpty(), () -> "EPC does not look at " + missing);
  }

  @Test
  void classFieldsKeepTheirNestedPath() {
    QueryParamName.MATCH_anyEPCClass.getKeys().forEach((field, path) ->
        assertTrue(path.equals(QueryParamName.EPC.getKeys().get(field)),
            () -> field + " needs the nested path " + path + " under EPC as under MATCH_anyEPCClass"));
  }
}
