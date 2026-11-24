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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openepcis.repository.config.RepositoryConfiguration;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.core.SearchRequest;

/**
 * GET /events/{eventID} reads what the event query reads. The capturer's group was a filter of its
 * own here, so an event another group captured and the caller may read (shared with it, held by
 * it) was found by the query and missing by its ID.
 */
class EventByIdQueryTest {

  @Test
  void anEventIsFoundByItsIdWhoeverCapturedIt() {
    final RepositoryConfiguration config = mock(RepositoryConfiguration.class, RETURNS_DEEP_STUBS);
    when(config.epcis().event().index()).thenReturn("epcis-event");
    final QueryRepositoryImpl repository = new QueryRepositoryImpl(new ObjectMapper(), null, config, null, null, null, null);
    final SearchRequest request = repository.getSearchRequestForFetchEvents("ni:///sha-256;abc?ver=CBV2.0", "platform");

    final StringWriter json = new StringWriter();
    final JacksonJsonpMapper mapper = new JacksonJsonpMapper();
    try (var generator = mapper.jsonProvider().createGenerator(json)) {
      request.query().serialize(generator, mapper);
    }
    assertFalse(json.toString().contains("capturedBy"), json.toString());
    assertTrue(json.toString().contains("ni:///sha-256;abc?ver=CBV2.0"), json.toString());
    assertTrue(json.toString().contains("metadata.visible"), json.toString());
    assertTrue(request.index().contains("epcis-event*"), request.index().toString());
  }
}
