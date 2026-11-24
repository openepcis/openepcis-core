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
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The precondition that has to hold before an event may be hashed: it has to
 * say when it happened. The canonical hash covers {@code eventTime}, and the
 * hash is the event's identity — so an event that cannot be dated would be
 * stored under an identity that does not describe it.
 */
class EventDatingTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static ObjectNode eventWith(final String eventTime) {
        final ObjectNode event = JSON.createObjectNode();
        event.put("type", "ObjectEvent");
        if (eventTime != null) {
            event.put("eventTime", eventTime);
        }
        return event;
    }

    @Test
    @DisplayName("an eventTime with an offset is what the hash can be built on")
    void anOffsetInstantPasses() {
        assertEquals(Optional.empty(),
                EPCISEventValidationService.whyItCannotBeDated(eventWith("2026-08-10T09:00:00.000Z")));
        assertEquals(Optional.empty(),
                EPCISEventValidationService.whyItCannotBeDated(eventWith("2026-08-10T11:00:00+02:00")));
    }

    @Test
    @DisplayName("no eventTime at all is an incomplete statement, not a hashable one")
    void aMissingEventTimeIsNamed() {
        // The generator does not object: it simply leaves the field out of the
        // pre-hash string and answers a hash anyway. That is the dangerous shape
        // — an incomplete statement, identified.
        assertTrue(EPCISEventValidationService.whyItCannotBeDated(eventWith(null))
                .orElseThrow().contains("no eventTime"));
        assertTrue(EPCISEventValidationService.whyItCannotBeDated(eventWith(""))
                .orElseThrow().contains("no eventTime"));
    }

    @Test
    @DisplayName("an eventTime without an offset is refused, not thrown at the caller")
    void anOffsetlessEventTimeIsNamed() {
        // Left to the generator this surfaces as a raw DateTimeParseException,
        // outside the wrapping that turns generator trouble into an EventHashException.
        final Optional<String> refusal =
                EPCISEventValidationService.whyItCannotBeDated(eventWith("2026-08-10T09:00:00"));
        assertTrue(refusal.isPresent());
        assertTrue(refusal.orElseThrow().contains("UTC offset"), refusal.orElseThrow());
    }
}
