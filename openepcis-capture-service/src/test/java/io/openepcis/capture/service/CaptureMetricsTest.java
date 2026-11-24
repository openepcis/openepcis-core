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
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.openepcis.capture.spi.NoCallerTags;
import org.junit.jupiter.api.Test;

class CaptureMetricsTest {

  @Test
  void countersExistBeforeTheFirstCapture() {
    final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    new CaptureMetrics(registry, new NoCallerTags());
    for (final String name :
        new String[] {CaptureMetrics.DOCUMENTS, CaptureMetrics.EVENTS, CaptureMetrics.PERSISTED}) {
      assertNotNull(registry.find(name).counter(), name);
    }
    assertNotNull(registry.find(CaptureMetrics.INVALID).counter());
    assertNotNull(registry.find(CaptureMetrics.DUPLICATE).counter());
    assertNotNull(registry.find(CaptureMetrics.COMPLETION).timer());
  }

  @Test
  void everyCallerCountsInOneSeries() {
    final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    final CaptureMetrics metrics = new CaptureMetrics(registry, new NoCallerTags());
    metrics.documentCaptured("a");
    metrics.documentCaptured(null);

    assertEquals(2.0, registry.get(CaptureMetrics.DOCUMENTS).counter().count());
  }

  @Test
  void completionIsTimedPerOutcome() {
    final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    final CaptureMetrics metrics = new CaptureMetrics(registry, new NoCallerTags());
    metrics.captureCompleted("a", java.time.Duration.ofSeconds(7), true);
    metrics.captureCompleted("a", java.time.Duration.ofSeconds(3), false);
    metrics.captureCompleted("a", java.time.Duration.ofSeconds(-1), true);

    assertEquals(
        1, registry.get(CaptureMetrics.COMPLETION).tag("outcome", "success").timer().count());
    assertEquals(
        1, registry.get(CaptureMetrics.COMPLETION).tag("outcome", "failed").timer().count());
  }
}
