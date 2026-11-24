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

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.openepcis.capture.spi.CallerTags;
import io.micrometer.core.instrument.Timer;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Duration;

/**
 * The capture counters.
 *
 * <p>They are registered here at startup, so after a restart without a capture they exist with
 * zero: {@code rate()} answers and an alert on "no captures" can tell quiet from broken. Which
 * caller a series belongs to is up to {@link CallerTags}; the Community Edition has one caller.
 */
@Startup
@ApplicationScoped
public class CaptureMetrics {

  static final String DOCUMENTS = "openepcis_capture_document_count";
  static final String EVENTS = "openepcis_capture_event_count";
  static final String PERSISTED = "openepcis_persist_event_count";
  static final String INVALID = "openepcis_validate_event_invalid_count";
  static final String DUPLICATE = "openepcis_validate_event_duplicate_count";
  static final String COMPLETION = "openepcis_capture_completion";

  /**
   * Buckets of the completion time. Fixed instead of a percentile histogram: that would be some
   * seventy buckets per series. A small document completes in about seven seconds; the upper
   * buckets are for large imports and a stalled pipeline.
   */
  private static final Duration[] COMPLETION_BUCKETS = {
    Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(5), Duration.ofSeconds(10),
    Duration.ofSeconds(20), Duration.ofSeconds(30), Duration.ofMinutes(1), Duration.ofMinutes(2),
    Duration.ofMinutes(5), Duration.ofMinutes(15)
  };

  private final MeterRegistry registry;
  private final CallerTags callerTags;

  public CaptureMetrics(final MeterRegistry registry, final CallerTags callerTags) {
    this.registry = registry;
    this.callerTags = callerTags;
    for (final String name : new String[] {DOCUMENTS, EVENTS, PERSISTED}) {
      registry.counter(name, callerTags.of(null));
    }
    // Counted by EPCISEventValidationService, which runs before the caller is at hand; they are
    // only registered early here.
    registry.counter(INVALID);
    registry.counter(DUPLICATE);
    completionTimer(callerTags.of(null), true);
  }

  /** A document accepted by POST /capture. */
  public void documentCaptured(final String defaultGroup) {
    counter(DOCUMENTS, defaultGroup).increment();
  }

  /** A single event accepted by POST /events. */
  public void eventCaptured(final String defaultGroup) {
    counter(EVENTS, defaultGroup).increment();
  }

  /** An event written to the repository. */
  public void eventPersisted(final String defaultGroup) {
    counter(PERSISTED, defaultGroup).increment();
  }

  /**
   * A capture job finished: from the job's creation (the document was accepted) to the moment the
   * last of its events was processed and the job was marked done. {@code success} is false when
   * the job finished with errors.
   */
  public void captureCompleted(
      final String defaultGroup, final Duration took, final boolean success) {
    if (took == null || took.isNegative()) {
      return;
    }
    completionTimer(callerTags.of(defaultGroup), success).record(took);
  }

  private Timer completionTimer(final Tags caller, final boolean success) {
    return Timer.builder(COMPLETION)
        .description("Time from accepting a capture to its job being finished")
        .tags(caller)
        .tag("outcome", success ? "success" : "failed")
        .serviceLevelObjectives(COMPLETION_BUCKETS)
        .register(registry);
  }

  private Counter counter(final String name, final String defaultGroup) {
    return registry.counter(name, callerTags.of(defaultGroup));
  }
}
