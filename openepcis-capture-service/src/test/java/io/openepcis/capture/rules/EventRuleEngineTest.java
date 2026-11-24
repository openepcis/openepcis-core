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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.openepcis.validation.rule.EventRule;
import io.openepcis.validation.rule.Finding;
import io.openepcis.validation.rule.RuleContext;
import io.openepcis.validation.rule.Severity;
import io.smallrye.config.PropertiesConfigSource;
import io.smallrye.config.SmallRyeConfigBuilder;
import io.smallrye.mutiny.Uni;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EventRuleEngineTest {

  private static final Duration WAIT = Duration.ofSeconds(10);

  @Test
  @DisplayName("a rule is skipped when a rule it depends on reported a HARD_ERROR")
  void dependentRuleSkippedAfterHardError() {
    final AtomicInteger expensiveCalls = new AtomicInteger();
    final EventRule format =
        new TestRule("format", 1, EventRule.OnFailure.HARD, c -> Uni.createFrom().item(
            c.flag("broken", false) ? List.of(Finding.hard("bad format")) : List.<Finding>of()));
    final EventRule lookup =
        new EventRule() {
          @Override
          public String id() {
            return "lookup";
          }

          @Override
          public int order() {
            return 2;
          }

          @Override
          public java.util.Set<String> dependsOn() {
            return java.util.Set.of("format");
          }

          @Override
          public Uni<List<Finding>> check(final RuleContext context) {
            expensiveCalls.incrementAndGet();
            return Uni.createFrom().item(List.of(Finding.soft("looked up")));
          }
        };
    final List<Finding> broken =
        engine(Map.of("openepcis.validation.rules.format.params.broken", "true"), format, lookup)
            .run(context(null)).await().atMost(WAIT);
    assertEquals(List.of("format"), broken.stream().map(Finding::ruleId).toList());
    assertEquals(0, expensiveCalls.get());

    final List<Finding> fine = engine(Map.of(), format, lookup).run(context(null)).await().atMost(WAIT);
    assertEquals(List.of("lookup"), fine.stream().map(Finding::ruleId).toList());
    assertEquals(1, expensiveCalls.get());
  }

  @Test
  @DisplayName("findings carry the rule's catalogue entry: type URN, status")
  void catalogueEntry() {
    final List<Finding> findings =
        EventRuleEngine.of(List.of(rule("gln-known", 1, c -> Uni.createFrom().item(List.of(Finding.hard("unknown GLN"))))))
            .run(context(null)).await().atMost(WAIT);
    assertEquals("urn:openepcis:validation:gln-known", findings.get(0).type());
    assertEquals(400, findings.get(0).status());
  }

  @Test
  @DisplayName("a dependency cycle is refused at startup")
  void cycleRefused() {
    final EventRule a = new EventRule() {
      public String id() { return "a"; }
      public java.util.Set<String> dependsOn() { return java.util.Set.of("b"); }
      public Uni<List<Finding>> check(final RuleContext c) { return Uni.createFrom().item(List.of()); }
    };
    final EventRule b = new EventRule() {
      public String id() { return "b"; }
      public java.util.Set<String> dependsOn() { return java.util.Set.of("a"); }
      public Uni<List<Finding>> check(final RuleContext c) { return Uni.createFrom().item(List.of()); }
    };
    assertThrows(IllegalStateException.class, () -> EventRuleEngine.of(List.of(a, b)));
  }

  /** A rule assembled from its parts. */
  private record TestRule(
      String id, int order, OnFailure onFailure, Function<RuleContext, Uni<List<Finding>>> body)
      implements EventRule {
    @Override
    public Uni<List<Finding>> check(final RuleContext context) {
      return body.apply(context);
    }
  }

  private static TestRule rule(final String id, final int order, final Function<RuleContext, Uni<List<Finding>>> body) {
    return new TestRule(id, order, EventRule.OnFailure.HARD, body);
  }

  private static RuleContext context(final String tenant) {
    return new RuleContext(
        new ObjectMapper().createObjectNode().put("type", "ObjectEvent"), Map.of(), 3, List.of(), tenant,
        "capture-1", Map.of());
  }

  private static ValidationRulesConfig config(final Map<String, String> properties) {
    return new SmallRyeConfigBuilder()
        .withMapping(ValidationRulesConfig.class)
        .withSources(new PropertiesConfigSource(properties, "test", 500))
        .build()
        .getConfigMapping(ValidationRulesConfig.class);
  }

  private static EventRuleEngine engine(final Map<String, String> properties, final EventRule... rules) {
    final ValidationRulesConfig config = config(properties);
    return new EventRuleEngine(
        List.of(rules), RuleSettings.Resolver.from(config), new SimpleMeterRegistry(),
        config.defaults().breakerFailures(), config.defaults().breakerDelay(), Clock.systemUTC());
  }

  @Test
  @DisplayName("findings come in rule order, not in the order the rules answered")
  void ruleOrderNotArrivalOrder() {
    final EventRuleEngine engine =
        engine(
            Map.of(),
            rule("slow-first", 10, c -> Uni.createFrom().item(List.of(Finding.hard("slow")))
                .onItem().delayIt().by(Duration.ofMillis(300))),
            rule("fast-second", 20, c -> Uni.createFrom().item(List.of(Finding.hard("fast")))));
    final List<Finding> findings = engine.run(context(null)).await().atMost(WAIT);
    assertEquals(List.of("slow", "fast"), findings.stream().map(Finding::detail).toList());
    assertEquals(List.of("slow-first", "fast-second"), findings.stream().map(Finding::ruleId).toList());
  }

  @Test
  @DisplayName("rules run concurrently: two slow rules cost one wait, not two")
  void rulesRunConcurrently() {
    final Function<RuleContext, Uni<List<Finding>>> slow =
        c -> Uni.createFrom().item(List.<Finding>of()).onItem().delayIt().by(Duration.ofMillis(600));
    final EventRuleEngine engine = engine(Map.of(), rule("a", 1, slow), rule("b", 2, slow));
    final long start = System.nanoTime();
    engine.run(context(null)).await().atMost(WAIT);
    assertTrue(Duration.ofNanos(System.nanoTime() - start).toMillis() < 1100);
  }

  @Test
  @DisplayName("configuration switches a rule off, re-weights it and passes parameters")
  void configuration() {
    final Map<String, String> properties =
        Map.of(
            "openepcis.validation.rules.weighed.severity", "SOFT_ERROR",
            "openepcis.validation.rules.silenced.enabled", "false",
            "openepcis.validation.rules.weighed.params.limit", "7");
    final EventRuleEngine engine =
        engine(
            properties,
            rule("weighed", 1, c -> Uni.createFrom().item(
                List.of(Finding.hard("limit " + c.parameter("limit").orElse("-"))))),
            rule("silenced", 2, c -> Uni.createFrom().item(List.of(Finding.hard("silenced")))));

    final List<Finding> findings = engine.run(context(null)).await().atMost(WAIT);
    assertEquals(1, findings.size());
    assertEquals(Severity.SOFT_ERROR, findings.get(0).severity());
    assertEquals("limit 7", findings.get(0).detail());
  }

  @Test
  @DisplayName("a rule that times out fails open or closed as configured")
  void timeoutFollowsOnFailurePolicy() {
    final Function<RuleContext, Uni<List<Finding>>> hangs = c -> Uni.createFrom().nothing();
    final Map<String, String> properties =
        Map.of(
            "openepcis.validation.defaults.timeout", "PT0.2S",
            "openepcis.validation.rules.closed.on-failure", "HARD",
            "openepcis.validation.rules.open.on-failure", "SOFT",
            "openepcis.validation.rules.quiet.on-failure", "IGNORE");
    final List<Finding> findings =
        engine(properties, rule("closed", 1, hangs), rule("open", 2, hangs), rule("quiet", 3, hangs))
            .run(context(null))
            .await()
            .atMost(WAIT);
    assertEquals(List.of("closed", "open"), findings.stream().map(Finding::ruleId).toList());
    assertEquals(List.of(Severity.HARD_ERROR, Severity.SOFT_ERROR), findings.stream().map(Finding::severity).toList());
    assertTrue(findings.get(0).detail().startsWith("Validation rule closed could not check the event"));
    assertEquals(EventRuleEngine.UNAVAILABLE_TYPE + "closed", findings.get(0).type(),
        "an unavailable rule is not confused with a finding about the data");
    assertEquals(503, findings.get(0).status());

    final Map<String, String> failing = Map.of("openepcis.validation.defaults.timeout", "PT0.2S",
        "openepcis.validation.rules.strict.on-failure", "FAIL");
    assertThrows(
        Exception.class,
        () -> engine(failing, rule("strict", 1, hangs)).run(context(null)).await().atMost(WAIT));
  }

  @Test
  @DisplayName("retries absorb a passing failure")
  void retriesAbsorbAPassingFailure() {
    final AtomicInteger calls = new AtomicInteger();
    final EventRule flaky =
        rule("flaky", 1, c -> calls.incrementAndGet() == 1
            ? Uni.createFrom().failure(new IllegalStateException("blip"))
            : Uni.createFrom().item(List.<Finding>of()));
    final List<Finding> findings =
        engine(Map.of("openepcis.validation.rules.flaky.retries", "1"), flaky)
            .run(context(null))
            .await()
            .atMost(WAIT);
    assertEquals(List.of(), findings);
    assertEquals(2, calls.get());
  }

  @Test
  @DisplayName("after repeated failures the breaker stops calling the rule, and tries again later")
  void breakerOpensAndRecovers() {
    final AtomicInteger calls = new AtomicInteger();
    final MutableClock clock = new MutableClock();
    final EventRule broken =
        rule("broken", 1, c -> {
          calls.incrementAndGet();
          return Uni.createFrom().failure(new IllegalStateException("backend down"));
        });
    final EventRuleEngine engine =
        new EventRuleEngine(List.of(broken), RuleSettings.Resolver.defaults(), new SimpleMeterRegistry(),
            3, Duration.ofSeconds(30), clock);
    for (int i = 0; i < 6; i++) {
      final List<Finding> findings = engine.run(context(null)).await().atMost(WAIT);
      assertEquals(1, findings.size(), "fail closed on every event, breaker or not");
    }
    assertEquals(3, calls.get(), "the breaker opened after three failures");
    clock.advance(Duration.ofSeconds(31));
    engine.run(context(null)).await().atMost(WAIT);
    assertEquals(4, calls.get(), "after the delay the rule is asked again");
  }

  @Test
  @DisplayName("an exception in applies() is a rule failure, not a crash of the validation")
  void appliesThrowing() {
    final EventRule throwing =
        new EventRule() {
          @Override
          public String id() {
            return "throwing";
          }

          @Override
          public boolean applies(final RuleContext context) {
            throw new IllegalArgumentException("bad input");
          }

          @Override
          public OnFailure onFailure() {
            return OnFailure.SOFT;
          }

          @Override
          public Uni<List<Finding>> check(final RuleContext context) {
            return Uni.createFrom().item(List.of());
          }
        };
    final List<Finding> findings = EventRuleEngine.of(List.of(throwing)).run(context(null)).await().atMost(WAIT);
    assertEquals(1, findings.size());
    assertEquals(Severity.SOFT_ERROR, findings.get(0).severity());
  }

  private static final class MutableClock extends Clock {
    private Instant now = Instant.parse("2026-10-08T10:00:00Z");

    void advance(final Duration duration) {
      now = now.plus(duration);
    }

    @Override
    public java.time.ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(final java.time.ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }
}
