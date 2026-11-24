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

import io.openepcis.validation.rule.EventRule;
import io.openepcis.validation.rule.Severity;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * {@code openepcis.validation.*}: which rules run, how much their findings weigh and how they are
 * guarded. Everything is optional; without any entry every discovered rule runs with its own
 * defaults.
 *
 * <pre>
 * openepcis.validation.rules.sensor-report.enabled=false
 * openepcis.validation.rules.event-id-unique.severity=SOFT_ERROR
 * </pre>
 */
@ConfigMapping(prefix = "openepcis.validation")
public interface ValidationRulesConfig {

  /** Defaults for every rule's guard. */
  Guard defaults();

  /** Per rule id. */
  Map<String, Rule> rules();

  interface Guard {
    /** Longest a rule may take for one event. */
    @WithDefault("PT10S")
    Duration timeout();

    /** Retries after a failure (exception or timeout), with backoff. */
    @WithDefault("0")
    int retries();

    /** Consecutive failures that open a rule's circuit breaker. */
    @WithDefault("5")
    int breakerFailures();

    /** How long an open breaker short-circuits before the rule is tried again. */
    @WithDefault("PT30S")
    Duration breakerDelay();
  }

  interface Rule {
    Optional<Boolean> enabled();

    /** Replaces the severity of every finding of the rule. */
    Optional<Severity> severity();

    Optional<EventRule.OnFailure> onFailure();

    Optional<Duration> timeout();

    Optional<Integer> retries();

    Map<String, String> params();
  }
}
