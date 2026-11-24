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
package io.openepcis.validation.rule;

import io.smallrye.mutiny.Uni;
import java.util.List;
import java.util.Set;

/**
 * One validation rule for captured events. Implementations are CDI beans; the capture engine
 * discovers every bean of this type, so a rule ships by being on the classpath and is switched,
 * weighted and tuned by configuration alone:
 *
 * <pre>
 * openepcis.validation.rules.&lt;id&gt;.enabled=false
 * openepcis.validation.rules.&lt;id&gt;.severity=SOFT_ERROR
 * openepcis.validation.rules.&lt;id&gt;.on-failure=SOFT
 * openepcis.validation.rules.&lt;id&gt;.timeout=PT2S
 * openepcis.validation.rules.&lt;id&gt;.params.&lt;name&gt;=value
 * openepcis.validation.tenants.&lt;tenant&gt;.rules.&lt;id&gt;.severity=HARD_ERROR
 * </pre>
 *
 * <p>The engine runs the applicable rules of an event concurrently, each behind its own timeout,
 * retry and circuit breaker, off the I/O threads (a rule may block). Findings are reported in rule
 * order, so the outcome does not depend on which rule answered first.
 *
 * <p>A rule must not change the event, and should look things up by document id rather than by
 * search: a search does not see what was written a moment ago.
 */
public interface EventRule {

  /** Stable id, lower case with dashes; the configuration key. */
  String id();

  /**
   * Problem type URN of this rule's findings, one per rule, so a client can tell rules apart. The
   * failure of a rule (its backend unavailable) is reported under a type of its own.
   */
  default String type() {
    return "urn:openepcis:validation:" + id();
  }

  /** Short label of this rule's findings, constant per type; {@code null} uses the capture default. */
  default String title() {
    return null;
  }

  /** HTTP-style status of this rule's findings. */
  default int status() {
    return 400;
  }

  /**
   * Rules that must have run first. When one of them reported a HARD_ERROR this rule is skipped:
   * an expensive lookup is pointless when its input is already known to be wrong.
   */
  default Set<String> dependsOn() {
    return Set.of();
  }

  /** Whether the rule runs without configuration; an opt-in rule returns false. */
  default boolean enabledByDefault() {
    return true;
  }

  /** Position in the report; lower first. Built-in rules use 100 to 999. */
  default int order() {
    return 1000;
  }

  /** Severity of this rule's findings unless configuration says otherwise. */
  default Severity severity() {
    return Severity.HARD_ERROR;
  }

  /** What a failing rule (exception, timeout, open breaker) means, unless configured. */
  default OnFailure onFailure() {
    return OnFailure.HARD;
  }

  /** Whether the rule has anything to say about this event; cheap, no I/O. */
  default boolean applies(final RuleContext context) {
    return true;
  }

  /** The findings for one event; an empty list means nothing to report. */
  Uni<List<Finding>> check(RuleContext context);

  /** What happens when a rule cannot give an answer. */
  enum OnFailure {
    /** The validation itself fails, as an unexpected exception always did. */
    FAIL,
    /** Fail closed: a HARD_ERROR finding naming the rule; the event is refused. */
    HARD,
    /** Fail open: a SOFT_ERROR finding; the event is stored. */
    SOFT,
    /** Fail open without a trace in the report (the log still has it). */
    IGNORE
  }
}
