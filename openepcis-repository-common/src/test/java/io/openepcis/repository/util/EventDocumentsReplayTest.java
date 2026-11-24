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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openepcis.repository.model.CapturedByES;
import io.openepcis.repository.model.CbvES;
import io.openepcis.repository.model.EPCISEventES;
import io.openepcis.repository.model.ErrorDeclarationES;
import io.openepcis.repository.model.MetadataES;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Which capture an event is news for, and when two declarations say the same. */
class EventDocumentsReplayTest {

  private static CapturedByES by(final String captureID, final String tenant) {
    return CapturedByES.builder().captureID(captureID).defaultGroup(tenant).userID(tenant + "-user").build();
  }

  private static EPCISEventES event(final CapturedByES... capturedBy) {
    final EPCISEventES event = new EPCISEventES();
    event.setMetadata(MetadataES.builder().capturedBy(new ArrayList<>(List.of(capturedBy))).build());
    return event;
  }

  @Test
  @DisplayName("news for the capture that first brought it to a tenant, not for that tenant's replays")
  void newInCapture() {
    final EPCISEventES event = event(by("c1", "acme"), by("c2", "globex"), by("c3", "ACME"));
    assertTrue(EventDocuments.newInCapture(event, "c1"));
    assertTrue(EventDocuments.newInCapture(event, "c2"), "another tenant: news for it");
    assertFalse(EventDocuments.newInCapture(event, "c3"), "the same tenant again: a replay");
    assertTrue(EventDocuments.capturedIn(event, "c3"));
    assertFalse(EventDocuments.capturedIn(event, "c4"));
    assertTrue(EventDocuments.capturedByTenant(event, "globex", null));
    assertFalse(EventDocuments.capturedByTenant(event, "initech", "initech-user"));
  }

  @Test
  @DisplayName("a capture also made for another group under the same captureID is news")
  void capturedForAnotherGroupIsNews() {
    final EPCISEventES event = event(by("c1", "carrier"), by("c1", "owner"));
    assertTrue(EventDocuments.newInCapture(event, "c1"));
  }

  private static ErrorDeclarationES declaration(final String time, final String reason, final String... corrective) {
    final CbvES cbv = new CbvES();
    cbv.setAsCaptured(reason);
    final ErrorDeclarationES declaration = new ErrorDeclarationES();
    declaration.setDeclarationTime(OffsetDateTime.parse(time));
    declaration.setReason(cbv);
    declaration.setCorrectiveEventIDs(corrective.length == 0 ? null : List.of(corrective));
    return declaration;
  }

  @Test
  @DisplayName("the same instant in another offset and corrections in another order say the same")
  void sameDeclaration() {
    assertTrue(
        EventDocuments.sameDeclaration(
            declaration("2026-11-11T16:00:00Z", "incorrect_data", "a", "b"),
            declaration("2026-11-11T17:00:00+01:00", "incorrect_data", "b", "a")));
    assertFalse(
        EventDocuments.sameDeclaration(
            declaration("2026-11-11T16:00:00Z", "incorrect_data"),
            declaration("2026-11-11T16:00:01Z", "incorrect_data")));
    assertFalse(
        EventDocuments.sameDeclaration(
            declaration("2026-11-11T16:00:00Z", "incorrect_data"),
            declaration("2026-11-11T16:00:00Z", "did_not_occur")));
    final ErrorDeclarationES urn = declaration("2026-11-11T16:00:00Z", "urn:epcglobal:cbv:er:incorrect_data");
    urn.getReason().setAsURN("urn:epcglobal:cbv:er:incorrect_data");
    final ErrorDeclarationES bare = declaration("2026-11-11T16:00:00Z", "incorrect_data");
    bare.getReason().setAsURN("urn:epcglobal:cbv:er:incorrect_data");
    assertTrue(EventDocuments.sameDeclaration(urn, bare), "the reason in another spelling");
    assertFalse(
        EventDocuments.sameDeclaration(
            declaration("2026-11-11T16:00:00Z", "incorrect_data", "a"),
            declaration("2026-11-11T16:00:00Z", "incorrect_data")));
  }
}
