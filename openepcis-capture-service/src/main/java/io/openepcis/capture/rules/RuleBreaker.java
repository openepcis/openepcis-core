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
package io.openepcis.capture.rules;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A circuit breaker for one rule: after {@code threshold} failures in a row the rule is not asked
 * for {@code delay}; then calls go through again, the first success closes it and the first failure
 * re-opens it.
 * A rule whose backend is down then costs nothing per event instead of a timeout per event.
 */
final class RuleBreaker {

  private final int threshold;
  private final Duration delay;
  private final Clock clock;
  private final AtomicInteger failures = new AtomicInteger();
  private final AtomicReference<Instant> openUntil = new AtomicReference<>(Instant.MIN);

  RuleBreaker(final int threshold, final Duration delay, final Clock clock) {
    this.threshold = Math.max(1, threshold);
    this.delay = delay;
    this.clock = clock;
  }

  /** Whether a call may go through now. */
  boolean allows() {
    final Instant until = openUntil.get();
    if (until.equals(Instant.MIN) || clock.instant().isAfter(until)) {
      return true;
    }
    return false;
  }

  void success() {
    failures.set(0);
    openUntil.set(Instant.MIN);
  }

  void failure() {
    if (failures.incrementAndGet() >= threshold) {
      openUntil.set(clock.instant().plus(delay));
    }
  }

  boolean isOpen() {
    return !allows();
  }
}
