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
package io.openepcis.capture.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openepcis.model.dto.InvalidEPCISEventInfo;
import io.openepcis.model.epcis.EPCISEvent;
import io.openepcis.model.epcis.ErrorDeclaration;
import io.openepcis.repository.api.QueryRepository;
import io.smallrye.mutiny.Uni;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Which stored event an error declaration declares erroneous.
 *
 * <p>EPCIS 2.0 7.4.1.2: the declaration "SHALL be otherwise identical to a prior event", every
 * field equal except errorDeclaration and recordTime, the eventID included. The content hash
 * leaves exactly those out, so an equal hash is equal content. Before, an eventID that merely
 * existed was enough, and the lookup ran across every tenant.
 */
class ErrorDeclarationMatchTest {

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final String TENANT = "minecraft";
  private static final String HASH = "ni:///sha-256;aaa?ver=CBV2.0";
  private static final String ORIGINAL_ID = "urn:uuid:11111111-1111-1111-1111-111111111111";
  private static final String CORRECTION_ID = "urn:uuid:22222222-2222-2222-2222-222222222222";

  private static ObjectNode declaration(final String eventID, final String... corrective) {
    final ObjectNode event = JSON.createObjectNode();
    if (eventID != null) {
      event.put("eventID", eventID);
    }
    event.put("hash", HASH);
    final ObjectNode decl = event.putObject("errorDeclaration");
    decl.put("declarationTime", "2026-11-11T16:00:00Z");
    decl.put("reason", "incorrect_data");
    final var ids = decl.putArray("correctiveEventIDs");
    for (String id : corrective) {
      ids.add(id);
    }
    return event;
  }

  private static EPCISEvent stored(final String eventID, final boolean isDeclaration) {
    final EPCISEvent event = new EPCISEvent();
    event.setEventID(eventID);
    if (isDeclaration) {
      event.setErrorDeclaration(new ErrorDeclaration());
    }
    return event;
  }

  private static QueryRepository repositoryWith(
      final List<EPCISEvent> sameContent, final Set<String> knownIDs) {
    final QueryRepository repository = mock(QueryRepository.class);
    when(repository.getTenantEventsByHash(TENANT, HASH))
        .thenReturn(Uni.createFrom().item(sameContent));
    when(repository.getTenantEventIDs(eq(TENANT), anyList()))
        .thenReturn(Uni.createFrom().item(knownIDs));
    return repository;
  }

  private static List<InvalidEPCISEventInfo> match(
      final QueryRepository repository, final ObjectNode declaration) {
    return EPCISEventValidationService.matchErroneousEvent(
            repository, declaration, 0, new ArrayList<>(), TENANT)
        .await()
        .indefinitely();
  }

  @Test
  @DisplayName("same eventID and same content: accepted")
  void identicalCopyIsAccepted() {
    final var repo = repositoryWith(List.of(stored(ORIGINAL_ID, false)), Set.of());
    assertEquals(List.of(), match(repo, declaration(ORIGINAL_ID)));
  }

  @Test
  @DisplayName("the eventID exists but a field differs: refused, and the message says so")
  void changedContentIsRefused() {
    final var repo = repositoryWith(List.of(), Set.of(ORIGINAL_ID));
    final var found = match(repo, declaration(ORIGINAL_ID));
    assertEquals(1, found.size());
    assertTrue(found.getFirst().getDetail().contains("some field differs"), found.getFirst().getDetail());
  }

  @Test
  @DisplayName("no event with that eventID: refused as missing")
  void missingEventIsRefused() {
    final var repo = repositoryWith(List.of(), Set.of());
    final var found = match(repo, declaration(ORIGINAL_ID));
    assertEquals(1, found.size());
    assertTrue(found.getFirst().getDetail().contains("There is no event"), found.getFirst().getDetail());
  }

  @Test
  @DisplayName("same content under another eventID: refused, naming the right one")
  void wrongEventIdIsRefused() {
    final var repo = repositoryWith(List.of(stored(ORIGINAL_ID, false)), Set.of());
    final var found = match(repo, declaration("urn:uuid:99999999-9999-9999-9999-999999999999"));
    assertEquals(1, found.size());
    assertTrue(found.getFirst().getDetail().contains(ORIGINAL_ID), found.getFirst().getDetail());
  }

  @Test
  @DisplayName("an event that has a declaration already gets no second one")
  void secondDeclarationIsRefused() {
    final var repo =
        repositoryWith(List.of(stored(ORIGINAL_ID, false), stored(ORIGINAL_ID, true)), Set.of(ORIGINAL_ID));
    final var found = match(repo, declaration(ORIGINAL_ID));
    assertEquals(1, found.size());
    assertTrue(found.getFirst().getDetail().contains("already has an error declaration"), found.getFirst().getDetail());
  }

  @Test
  @DisplayName("without an eventID the declaration takes the original's, not its own hash")
  void declarationTakesTheOriginalsEventId() {
    final var repo = repositoryWith(List.of(stored(ORIGINAL_ID, false)), Set.of());
    final ObjectNode declaration = declaration(null);
    assertEquals(List.of(), match(repo, declaration));
    assertEquals(ORIGINAL_ID, declaration.get("eventID").asText());
  }

  @Test
  @DisplayName("without an eventID and without a matching event: refused")
  void noEventIdNoMatchIsRefused() {
    final var repo = repositoryWith(List.of(), Set.of());
    assertEquals(1, match(repo, declaration(null)).size());
  }

  @Test
  @DisplayName("a declaration cannot name its own event as the correction")
  void selfCorrectionIsRefused() {
    final QueryRepository repo = mock(QueryRepository.class);
    final List<InvalidEPCISEventInfo> found =
        EPCISEventValidationService.checkCorrectiveEvents(
                repo, declaration(ORIGINAL_ID, ORIGINAL_ID), List.of(), 0, new ArrayList<>(), TENANT)
            .await()
            .indefinitely();
    assertEquals(1, found.size());
    assertTrue(found.getFirst().getDetail().contains("correctiveEventIDs"), found.getFirst().getDetail());
    verify(repo, never()).getTenantEventIDs(any(), anyList());
  }

  @Test
  @DisplayName("a correction elsewhere is looked up in the tenant only")
  void correctionIsLookedUpInTheTenant() {
    final QueryRepository repo = mock(QueryRepository.class);
    when(repo.getTenantEventIDs(eq(TENANT), anyList()))
        .thenReturn(Uni.createFrom().item(Set.of(CORRECTION_ID)));
    final List<InvalidEPCISEventInfo> found =
        EPCISEventValidationService.checkCorrectiveEvents(
                repo, declaration(ORIGINAL_ID, CORRECTION_ID), List.of(), 0, new ArrayList<>(), TENANT)
            .await()
            .indefinitely();
    assertEquals(List.of(), found);
    verify(repo).getTenantEventIDs(TENANT, List.of(CORRECTION_ID));
  }
  private static EPCISEvent storedDeclaration(final String declarationTime, final String reason) {
    final EPCISEvent event = stored(ORIGINAL_ID, true);
    event.getErrorDeclaration().setDeclarationTime(java.time.OffsetDateTime.parse(declarationTime));
    event.getErrorDeclaration().setReason(reason);
    return event;
  }

  @Test
  @DisplayName("the same declaration again is a replay: accepted, the store settles it")
  void sameDeclarationAgainIsAccepted() {
    // the same instant in another offset, the reason in another spelling
    final var repo =
        repositoryWith(
            List.of(
                stored(ORIGINAL_ID, false),
                storedDeclaration("2026-11-11T17:00:00+01:00", "urn:epcglobal:cbv:er:incorrect_data")),
            Set.of(ORIGINAL_ID));
    assertEquals(List.of(), match(repo, declaration(ORIGINAL_ID)));
    final ObjectNode withoutID = declaration(null);
    assertEquals(List.of(), match(repo, withoutID));
    assertEquals(ORIGINAL_ID, withoutID.get("eventID").asText(), "takes the stored eventID");
  }

  @Test
  @DisplayName("a declaration that says something else than the stored one is refused")
  void differentDeclarationIsRefused() {
    final var repo =
        repositoryWith(
            List.of(stored(ORIGINAL_ID, false), storedDeclaration("2026-11-11T16:00:00Z", "did_not_occur")),
            Set.of(ORIGINAL_ID));
    final var found = match(repo, declaration(ORIGINAL_ID));
    assertEquals(1, found.size());
    assertTrue(found.getFirst().getDetail().contains("says something else"), found.getFirst().getDetail());
  }
}
