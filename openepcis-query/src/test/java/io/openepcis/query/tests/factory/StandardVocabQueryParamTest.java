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

import io.openepcis.query.data.QueryParam;
import io.openepcis.query.factory.QueryParamMapper;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A CBV value may be written three ways, and all three have to be askable.
 *
 * <p>EPCIS allows a standard vocabulary element as a URN, as a Web URI, or as a
 * bare string, and the repository stores all three forms beside each other
 * ({@code asURN}, {@code asURI}, {@code asCaptured}) for exactly that reason.
 * The converter only ever understood the Web-URI form.
 *
 * <p>Most filters never notice, because they carry a URN prefix and a bare
 * value is prefixed on the way in. {@code EQ_errorReason} carries none, so its
 * bare value reached the converter and came back to the caller as a <b>500</b>
 * — measured against a running repository on 2026-08-29:
 * {@code EQ_errorReason=did_not_occur} answered "a server-side error occurred",
 * while the same query spelled as a URN answered 200. These tests pin both
 * halves, so that the prefixed ones stay prefixed and the unprefixed one stops
 * failing.
 */
class StandardVocabQueryParamTest {

  @SuppressWarnings("unchecked")
  private static List<String> values(final String name, final String value) {
    final QueryParam param =
        new QueryParamMapper(true, null, Optional.empty()).apply(Map.entry(name, List.of(value)));
    return (List<String>) param.getValue();
  }

  @Test
  @DisplayName("a bare value on a prefixed filter is prefixed, as it always was")
  void prefixedFiltersStillPrefix() {
    assertEquals(List.of("urn:epcglobal:cbv:bizstep:shipping"), values("EQ_bizStep", "shipping"));
    assertEquals(List.of("urn:epcglobal:cbv:disp:in_transit"), values("EQ_disposition", "in_transit"));
  }

  @Test
  @DisplayName("a bare value on an unprefixed filter survives — asCaptured is where it lives")
  void unprefixedFiltersKeepTheBareValue() {
    // This is the one that answered 500. errorReason carries no URN prefix, so
    // nothing turned did_not_occur into something the converter recognises.
    assertEquals(List.of("did_not_occur"), values("EQ_errorReason", "did_not_occur"));
    assertEquals(List.of("incorrect_data"), values("EQ_errorReason", "incorrect_data"));
  }

  @Test
  @DisplayName("a Web URI is translated, because that is the form the index does not keep")
  void webUrisBecomeUrns() {
    assertEquals(
        List.of("urn:epcglobal:cbv:bizstep:shipping"),
        values("EQ_bizStep", "https://ref.gs1.org/cbv/BizStep-shipping"));
    assertEquals(
        List.of("urn:epcglobal:cbv:er:did_not_occur"),
        values("EQ_errorReason", "https://ref.gs1.org/cbv/ER-did_not_occur"));
  }

  @Test
  @DisplayName("a URN passes through untouched")
  void urnsPassThrough() {
    assertEquals(
        List.of("urn:epcglobal:cbv:bizstep:shipping"),
        values("EQ_bizStep", "urn:epcglobal:cbv:bizstep:shipping"));
  }

  @Test
  @DisplayName("a value nobody knows is a filter that matches nothing, not a server fault")
  void anUnknownValueIsNotAnError() {
    assertEquals(List.of("wrong_data"), values("EQ_errorReason", "wrong_data"));
  }
}
