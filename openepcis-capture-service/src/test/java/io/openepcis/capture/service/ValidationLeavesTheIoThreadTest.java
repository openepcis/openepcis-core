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

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.openepcis.capture.rules.EventRuleEngine;
import io.openepcis.capture.rules.builtin.EventIdUniqueRule;
import io.openepcis.capture.rules.builtin.UserExtensionSchemaRule;
import io.openepcis.custom.extension.validators.UserExtensionValidator;
import io.openepcis.repository.api.QueryRepository;
import io.openepcis.repository.model.EPCISEventES;
import io.openepcis.validation.service.JsonSchemaValidator;
import io.smallrye.mutiny.Uni;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The repository's answers arrive on the OpenSearch HTTP client's I/O thread. The user-extension
 * validation that follows blocks on OpenSearch for the schema; run there, it waits for a response
 * only that thread could deliver. On dev this deadlock wedged the whole client (2026-10-06).
 */
class ValidationLeavesTheIoThreadTest {

  private static final String IO_THREAD = "fake-opensearch-io";

  @Test
  @DisplayName("the user-extension validation does not run on the thread that delivered the event IDs")
  void userExtensionValidationRunsOffTheIoThread() {
    final var io = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, IO_THREAD));
    try {
      final QueryRepository repository = mock(QueryRepository.class);
      when(repository.getEPCISEventESFromHash(any())).thenReturn(Uni.createFrom().item(new EPCISEventES()));
      // completed later on the I/O thread, as a network answer is, not before anyone waits
      when(repository.getEventIDs(anyList())).thenAnswer(inv -> {
        final CompletableFuture<Set<String>> answer = new CompletableFuture<>();
        io.schedule(() -> answer.complete(Set.of()), 200, TimeUnit.MILLISECONDS);
        return Uni.createFrom().completionStage(answer);
      });
      final UserExtensionValidator extensions = mock(UserExtensionValidator.class);
      final AtomicReference<String> validatedOn = new AtomicReference<>();
      when(extensions.validateUserExtensions(any(), any())).thenAnswer(inv -> {
        validatedOn.set(Thread.currentThread().getName());
        return Set.of();
      });
      final SimpleMeterRegistry registry = new SimpleMeterRegistry();
      final EPCISEventValidationService service = new EPCISEventValidationService(
          mock(JsonSchemaValidator.class), repository, extensions, registry,
          EventRuleEngine.of(java.util.List.of(
              new EventIdUniqueRule(repository),
              new UserExtensionSchemaRule(extensions, registry))));

      final ObjectNode event = new ObjectMapper().createObjectNode()
          .put("type", "ObjectEvent")
          .put("eventID", "ni:///sha-256;0000?ver=CBV2.0");
      service.validateEventAgainstCustomValidations(event, new ArrayList<>(), new String[] {""},
              "capture-1", new HashMap<>(), 0, new HashMap<>())
          .await().atMost(Duration.ofSeconds(10));

      assertNotNull(validatedOn.get(), "the user-extension validation ran");
      assertNotEquals(IO_THREAD, validatedOn.get(), "it must not block the I/O thread that answered");
    } finally {
      io.shutdownNow();
    }
  }
}
