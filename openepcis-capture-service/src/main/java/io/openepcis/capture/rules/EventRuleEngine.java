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

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.openepcis.capture.spi.CallerTags;
import io.openepcis.capture.spi.NoCallerTags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.openepcis.opentelemetry.logging.OpenEPCISLogger;
import io.openepcis.validation.rule.EventRule;
import io.openepcis.validation.rule.Finding;
import io.openepcis.validation.rule.RuleContext;
import io.openepcis.validation.rule.Severity;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runs the {@link EventRule}s on one event. Every enabled, applicable rule runs at once, unless it
 * waits for the rules it depends on; each sits behind its own guard (timeout, retries with
 * backoff, circuit breaker, then its on-failure policy) on the worker pool. The findings come back
 * in rule order, completed with the rule's catalogue entry and the effective severity, so the
 * report does not depend on which rule answered first.
 */
@ApplicationScoped
public class EventRuleEngine {

  private static final OpenEPCISLogger log = OpenEPCISLogger.getLogger(EventRuleEngine.class);

  /** Problem type prefix of a rule that could not give an answer. */
  public static final String UNAVAILABLE_TYPE = "urn:openepcis:validation:unavailable:";

  private final List<EventRule> rules;
  private final RuleSettings.Resolver settings;
  private final MeterRegistry meterRegistry;
  private final int breakerFailures;
  private final Duration breakerDelay;
  private final Clock clock;
  private final CallerTags callerTags;
  private final Map<String, RuleBreaker> breakers = new ConcurrentHashMap<>();

  @Inject
  public EventRuleEngine(
      @Any final Instance<EventRule> rules,
      final ValidationRulesConfig config,
      final RuleSettings.Resolver settings,
      final CallerTags callerTags,
      final MeterRegistry meterRegistry) {
    this(
        rules.stream().toList(),
        settings,
        meterRegistry,
        config.defaults().breakerFailures(),
        config.defaults().breakerDelay(),
        Clock.systemUTC(),
        callerTags);
    warnAboutUnknownRules(settings);
  }

  public EventRuleEngine(
      final List<EventRule> rules,
      final RuleSettings.Resolver settings,
      final MeterRegistry meterRegistry,
      final int breakerFailures,
      final Duration breakerDelay,
      final Clock clock) {
    this(rules, settings, meterRegistry, breakerFailures, breakerDelay, clock, new NoCallerTags());
  }

  public EventRuleEngine(
      final List<EventRule> rules,
      final RuleSettings.Resolver settings,
      final MeterRegistry meterRegistry,
      final int breakerFailures,
      final Duration breakerDelay,
      final Clock clock,
      final CallerTags callerTags) {
    this.callerTags = callerTags;
    this.rules =
        rules.stream()
            .sorted(Comparator.comparingInt(EventRule::order).thenComparing(EventRule::id))
            .toList();
    this.settings = settings;
    this.meterRegistry = meterRegistry;
    this.breakerFailures = breakerFailures;
    this.breakerDelay = breakerDelay;
    this.clock = clock;
    final List<String> ids = this.rules.stream().map(EventRule::id).toList();
    if (ids.stream().distinct().count() != ids.size()) {
      throw new IllegalStateException("two event rules share an id: " + ids);
    }
    rejectCycles();
    log.info("Event validation rules: {}", ids);
  }

  /** An engine with these rules and no configuration, for tests and tools. */
  public static EventRuleEngine of(final List<EventRule> rules) {
    return new EventRuleEngine(
        rules, RuleSettings.Resolver.defaults(), new SimpleMeterRegistry(), 5, Duration.ofSeconds(30),
        Clock.systemUTC());
  }

  public List<EventRule> rules() {
    return rules;
  }

  /** All findings of all applicable rules for one event, in rule order. */
  public Uni<List<Finding>> run(final RuleContext context) {
    final Map<String, Uni<List<Finding>>> scheduled = new LinkedHashMap<>();
    for (final EventRule rule : rules) {
      schedule(rule, context, scheduled);
    }
    final List<Uni<List<Finding>>> checks =
        scheduled.values().stream().filter(uni -> uni != SKIPPED).toList();
    if (checks.isEmpty()) {
      return Uni.createFrom().item(List.of());
    }
    return Uni.join()
        .all(checks)
        .andFailFast()
        .map(perRule -> perRule.stream().flatMap(List::stream).toList())
        // a rule's answer may arrive on an I/O thread; whatever the caller does next may block
        .emitOn(Infrastructure.getDefaultWorkerPool());
  }

  /** Marker for a rule that does not run for this event (disabled or not applicable). */
  private static final Uni<List<Finding>> SKIPPED = Uni.createFrom().item(List.of());

  private Uni<List<Finding>> schedule(
      final EventRule rule, final RuleContext context, final Map<String, Uni<List<Finding>>> scheduled) {
    final Uni<List<Finding>> known = scheduled.get(rule.id());
    if (known != null) {
      return known;
    }
    final RuleSettings effective = settings.resolve(rule, context.tenant());
    final RuleContext ruleContext = context.withParameters(effective.parameters());
    Uni<List<Finding>> check;
    if (!effective.enabled()) {
      check = SKIPPED;
    } else {
      boolean applies;
      try {
        applies = rule.applies(ruleContext);
      } catch (RuntimeException e) {
        scheduled.put(rule.id(), failed(rule, effective, ruleContext, e).memoize().indefinitely());
        return scheduled.get(rule.id());
      }
      check = applies ? guarded(rule, effective, ruleContext) : SKIPPED;
    }
    if (check != SKIPPED && !rule.dependsOn().isEmpty()) {
      final List<Uni<List<Finding>>> prerequisites = new ArrayList<>();
      for (final String dependency : rule.dependsOn()) {
        rules.stream()
            .filter(candidate -> candidate.id().equals(dependency))
            .findFirst()
            .map(candidate -> schedule(candidate, context, scheduled))
            .filter(uni -> uni != SKIPPED)
            .ifPresent(prerequisites::add);
      }
      if (!prerequisites.isEmpty()) {
        final Uni<List<Finding>> own = check;
        check =
            Uni.join()
                .all(prerequisites)
                .andCollectFailures()
                .onFailure()
                .recoverWithItem(List.of(List.of(Finding.hard("prerequisite failed"))))
                .chain(
                    results -> {
                      final boolean hardBefore =
                          results.stream()
                              .flatMap(List::stream)
                              .anyMatch(f -> f.severity() == null || f.severity().blocks());
                      if (hardBefore) {
                        log.debug("Rule {} skipped: a prerequisite reported a HARD_ERROR", rule.id());
                        return Uni.createFrom().item(List.<Finding>of());
                      }
                      return own;
                    });
      }
    }
    final Uni<List<Finding>> shared = check == SKIPPED ? SKIPPED : check.memoize().indefinitely();
    scheduled.put(rule.id(), shared);
    return shared;
  }

  private Uni<List<Finding>> guarded(
      final EventRule rule, final RuleSettings effective, final RuleContext context) {
    final RuleBreaker breaker =
        breakers.computeIfAbsent(rule.id(), id -> new RuleBreaker(breakerFailures, breakerDelay, clock));
    return Uni.createFrom()
        .deferred(
            () -> {
              if (!breaker.allows()) {
                return failed(
                    rule, effective, context,
                    new IllegalStateException("circuit breaker open after repeated failures"));
              }
              Uni<List<Finding>> check =
                  Uni.createFrom()
                      .deferred(() -> rule.check(context))
                      .runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
                      .ifNoItem()
                      .after(effective.timeout())
                      .fail();
              if (effective.retries() > 0) {
                check =
                    check
                        .onFailure()
                        .retry()
                        .withBackOff(Duration.ofMillis(100), Duration.ofSeconds(2))
                        .atMost(effective.retries());
              }
              return check
                  .onItem()
                  .invoke(breaker::success)
                  .map(
                      findings ->
                          finish(rule, effective, context, findings == null ? List.of() : findings))
                  .onFailure()
                  .invoke(breaker::failure)
                  .onFailure()
                  .recoverWithUni(failure -> failed(rule, effective, context, failure));
            });
  }

  private Uni<List<Finding>> failed(
      final EventRule rule,
      final RuleSettings effective,
      final RuleContext context,
      final Throwable failure) {
    meterRegistry
        .counter("openepcis_validation_rule_failures", callerTags(context).and("rule", rule.id()))
        .increment();
    log.warn(
        "Validation rule {} could not check event {} of capture {}: {}",
        rule.id(),
        context.eventIndex(),
        context.captureId(),
        failure.toString());
    final String detail =
        "Validation rule " + rule.id() + " could not check the event: " + failure.getMessage();
    final Finding unavailable =
        new Finding(
            rule.id(), UNAVAILABLE_TYPE + rule.id(), "Validation rule unavailable", 503, null, detail,
            null, null, null);
    return switch (effective.onFailure()) {
      case FAIL -> Uni.createFrom().failure(failure);
      case HARD -> Uni.createFrom().item(List.of(count(unavailable.withSeverity(Severity.HARD_ERROR), context)));
      case SOFT -> Uni.createFrom().item(List.of(count(unavailable.withSeverity(Severity.SOFT_ERROR), context)));
      case IGNORE -> Uni.createFrom().item(List.of());
    };
  }

  /** Completes a rule's findings with its catalogue entry and the configured severity. */
  private List<Finding> finish(
      final EventRule rule,
      final RuleSettings effective,
      final RuleContext context,
      final List<Finding> findings) {
    return findings.stream()
        .map(
            finding -> {
              Finding done = finding.withRuleId(rule.id());
              if (done.type() == null) {
                done = done.typed(rule.type());
              }
              if (done.title() == null && rule.title() != null) {
                done = done.titled(rule.title());
              }
              if (done.status() == null) {
                done = done.withStatus(rule.status());
              }
              if (effective.severity().isPresent()) {
                done = done.withSeverity(effective.severity().get());
              } else if (done.severity() == null) {
                done = done.withSeverity(rule.severity());
              }
              return count(done, context);
            })
        .toList();
  }

  private Finding count(final Finding finding, final RuleContext context) {
    meterRegistry
        .counter(
            "openepcis_validation_rule_findings",
            callerTags(context)
                .and(
                    "rule",
                    finding.ruleId(),
                    "severity",
                    (finding.severity() == null ? Severity.HARD_ERROR : finding.severity()).name()))
        .increment();
    return finding;
  }

  private Tags callerTags(final RuleContext context) {
    return callerTags.of(context.tenant());
  }

  private void rejectCycles() {
    final Map<String, Set<String>> edges = new HashMap<>();
    rules.forEach(rule -> edges.put(rule.id(), rule.dependsOn()));
    final Set<String> done = new HashSet<>();
    for (final String id : edges.keySet()) {
      visit(id, edges, new HashSet<>(), done);
    }
  }

  private static void visit(
      final String id, final Map<String, Set<String>> edges, final Set<String> path, final Set<String> done) {
    if (done.contains(id) || !edges.containsKey(id)) {
      return;
    }
    if (!path.add(id)) {
      throw new IllegalStateException("event rules depend on each other in a cycle: " + path);
    }
    edges.get(id).forEach(next -> visit(next, edges, path, done));
    path.remove(id);
    done.add(id);
  }

  /** A configured rule id without a rule is almost always a typo; say so at startup. */
  private void warnAboutUnknownRules(final RuleSettings.Resolver settings) {
    final Set<String> known = new HashSet<>(rules.stream().map(EventRule::id).toList());
    final Set<String> unknown = new TreeSet<>();
    settings.configuredRules().forEach(
        (label, id) -> {
          if (!known.contains(id)) {
            unknown.add(label);
          }
        });
    if (!unknown.isEmpty()) {
      log.warn("openepcis.validation configures rules that do not exist: {} (known: {})", unknown, known);
    }
  }
}
