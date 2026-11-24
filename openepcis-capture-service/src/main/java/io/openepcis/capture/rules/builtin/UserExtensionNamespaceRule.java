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
package io.openepcis.capture.rules.builtin;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.openepcis.constants.EPCIS;
import io.openepcis.model.epcis.exception.ExceptionMessages;
import io.openepcis.repository.util.JSONUtil;
import io.openepcis.service.util.Constants;
import io.openepcis.model.epcis.constants.CommonConstants;
import io.openepcis.validation.rule.EventRule;
import io.openepcis.validation.rule.Finding;
import io.openepcis.validation.rule.RuleContext;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;

/** Every prefix a user extension uses must be declared in the document's JSON-LD context. */
@ApplicationScoped
public class UserExtensionNamespaceRule implements EventRule {

  public static final String ID = "user-extension-namespaces";

  private final Counter invalidEventUserExtensionContextCounter;

  public UserExtensionNamespaceRule(final MeterRegistry meterRegistry) {
    this.invalidEventUserExtensionContextCounter =
        meterRegistry.counter("openepcis_validate_event_invalid_user_extension_context_count");
  }

  @Override
  public String id() {
    return ID;
  }

  /** The type every capture validation entry had before rules; clients may match on it. */
  @Override
  public String type() {
    return CommonConstants.CAPTURE_VALIDATION_EXCEPTION;
  }

  @Override
  public String title() {
    return CommonConstants.VALIDATION_FAILED;
  }

  @Override
  public int order() {
    return 300;
  }

  @Override
  public OnFailure onFailure() {
    return OnFailure.FAIL;
  }

  @Override
  public boolean applies(final RuleContext context) {
    return context.event().has(Constants.EVENT_ID);
  }

  @Override
  public Uni<List<Finding>> check(final RuleContext context) {
    return Uni.createFrom().item(() -> findings(context));
  }

  private List<Finding> findings(final RuleContext context) {
    // all prefixes of the event except the EPCIS default namespaces
    final Set<String> aliases =
        JSONUtil.getAllKeys(context.event()).stream()
            .filter(key -> key.contains(Constants.JSON_LD_USER_EXTENSION_FIELD_SEPARATOR))
            .map(key -> key.split(Constants.JSON_LD_USER_EXTENSION_FIELD_SEPARATOR)[0])
            .filter(prefix -> !EPCIS.EPCIS_DEFAULT_NAMESPACES.containsKey(prefix))
            .collect(Collectors.toSet());
    if (CollectionUtils.isEmpty(aliases)) {
      return List.of();
    }
    final Map<String, Object> jsonLdContext = context.jsonLdContext();
    final Set<String> missing =
        MapUtils.isEmpty(jsonLdContext)
            ? aliases
            : aliases.stream().filter(alias -> !jsonLdContext.containsKey(alias)).collect(Collectors.toSet());
    if (CollectionUtils.isEmpty(missing)) {
      return List.of();
    }
    invalidEventUserExtensionContextCounter.increment();
    // the message has always listed every alias, not only the missing ones
    return List.of(
        Finding.hard(ExceptionMessages.CONTEXT_INFORMATION_IS_MISSING_FOR_NAME_SPACE_ALIAS + aliases)
            .inSequence(List.of(context.eventIndex() + 1)));
  }
}
