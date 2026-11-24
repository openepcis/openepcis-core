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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openepcis.model.dto.InvalidEPCISEventInfo;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The CBV rule for an error declaration's reason, which nothing checked.
 *
 * <p>CBV defines exactly two reasons and forbids one combination:
 * {@code did_not_occur} withdraws the event outright, so it must not also point
 * at events that put it right — there is nothing left to correct. Neither the
 * closed list nor the combination was validated anywhere, so a typo and a
 * contradiction were both stored as stated.
 */
class ErrorReasonTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static ObjectNode eventWith(final String reason, final String... correctiveEventIDs) {
        final ObjectNode event = JSON.createObjectNode();
        final ObjectNode declaration = event.putObject("errorDeclaration");
        if (reason != null) {
            declaration.put("reason", reason);
        }
        if (correctiveEventIDs.length > 0) {
            final var ids = declaration.putArray("correctiveEventIDs");
            for (String id : correctiveEventIDs) {
                ids.add(id);
            }
        }
        return event;
    }

    private static List<InvalidEPCISEventInfo> complaintsAbout(
            final String reason, final String... correctiveEventIDs) {
        final List<InvalidEPCISEventInfo> found = new ArrayList<>();
        EPCISEventValidationService.checkReason(eventWith(reason, correctiveEventIDs), 1, found);
        return found;
    }

    @Test
    @DisplayName("both reasons pass, in all three spellings CBV allows")
    void theTwoReasonsPass() {
        for (String reason : List.of(
                "did_not_occur",
                "urn:epcglobal:cbv:er:did_not_occur",
                "https://ref.gs1.org/cbv/ER-did_not_occur",
                "incorrect_data",
                "urn:epcglobal:cbv:er:incorrect_data",
                "https://ref.gs1.org/cbv/ER-incorrect_data")) {
            assertEquals(List.of(), complaintsAbout(reason), reason);
        }
    }

    @Test
    @DisplayName("anything else is refused rather than stored as stated")
    void anInventedReasonIsRefused() {
        final List<InvalidEPCISEventInfo> complaints = complaintsAbout("wrong_data");
        assertEquals(1, complaints.size());
        assertTrue(complaints.getFirst().getDetail().contains("is not an error reason"),
                complaints.getFirst().getDetail());
    }

    @Test
    @DisplayName("did_not_occur must not name corrective events")
    void aWithdrawnEventHasNothingToCorrect() {
        final List<InvalidEPCISEventInfo> complaints =
                complaintsAbout("did_not_occur", "ni:///sha-256;abc?ver=CBV2.0");
        assertEquals(1, complaints.size());
        assertTrue(complaints.getFirst().getDetail().contains("did_not_occur"),
                complaints.getFirst().getDetail());
    }

    @Test
    @DisplayName("incorrect_data may, and that is the whole point of the pair")
    void aCorrectedEventMay() {
        assertEquals(List.of(), complaintsAbout("incorrect_data", "ni:///sha-256;abc?ver=CBV2.0"));
    }

    @Test
    @DisplayName("no reason at all is not this check's business")
    void anAbsentReasonIsLeftAlone() {
        assertEquals(List.of(), complaintsAbout(null));
    }
}
