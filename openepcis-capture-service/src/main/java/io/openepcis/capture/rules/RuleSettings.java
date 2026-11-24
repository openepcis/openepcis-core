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
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The effective settings of one rule: the rule's own defaults, then the configured layers (in the
 * Community Edition {@code openepcis.validation.rules.<id>}); the later wins field by field,
 * parameters merge.
 */
public record RuleSettings(
    boolean enabled,
    Optional<Severity> severity,
    EventRule.OnFailure onFailure,
    Duration timeout,
    int retries,
    Map<String, String> parameters) {

  /** Resolves the settings of a rule for the capturing caller's default group. */
  @FunctionalInterface
  public interface Resolver {
    RuleSettings resolve(EventRule rule, String tenant);

    /** The configured rule ids, keyed by how a startup warning names them. */
    default Map<String, String> configuredRules() {
      return Map.of();
    }

    /** No configuration: every rule on, with its own defaults. */
    static Resolver defaults() {
      return (rule, tenant) ->
          new RuleSettings(
              rule.enabledByDefault(), Optional.empty(), rule.onFailure(), Duration.ofSeconds(10), 0, Map.of());
    }

    /** {@code openepcis.validation.rules.<id>} on top of the rule's own defaults. */
    static Resolver from(final ValidationRulesConfig config) {
      return new Resolver() {
        @Override
        public RuleSettings resolve(final EventRule rule, final String tenant) {
          return layered(rule, config.defaults(), config.rules().get(rule.id()));
        }

        @Override
        public Map<String, String> configuredRules() {
          final Map<String, String> ids = new HashMap<>();
          config.rules().keySet().forEach(id -> ids.put(id, id));
          return ids;
        }
      };
    }
  }

  /** The rule's defaults with the given layers applied in order; null layers are skipped. */
  public static RuleSettings layered(
      final EventRule rule,
      final ValidationRulesConfig.Guard defaults,
      final ValidationRulesConfig.Rule... layers) {
    boolean enabled = rule.enabledByDefault();
    Optional<Severity> severity = Optional.empty();
    EventRule.OnFailure onFailure = rule.onFailure();
    Duration timeout = defaults.timeout();
    int retries = defaults.retries();
    final Map<String, String> parameters = new HashMap<>();
    for (final ValidationRulesConfig.Rule layer : layers) {
      if (layer == null) {
        continue;
      }
      enabled = layer.enabled().orElse(enabled);
      severity = layer.severity().isPresent() ? layer.severity() : severity;
      onFailure = layer.onFailure().orElse(onFailure);
      timeout = layer.timeout().orElse(timeout);
      retries = layer.retries().orElse(retries);
      parameters.putAll(layer.params());
    }
    return new RuleSettings(enabled, severity, onFailure, timeout, retries, Map.copyOf(parameters));
  }
}
