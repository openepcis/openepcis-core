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

import io.openepcis.service.util.Constants;
import io.openepcis.service.util.EventPredicates;
import io.openepcis.model.epcis.constants.CommonConstants;
import io.openepcis.validation.rule.EventRule;
import io.openepcis.validation.rule.Finding;
import io.openepcis.validation.rule.RuleContext;
import io.openepcis.validation.service.SensorEventValidator;
import io.openepcis.validation.service.ValidationMessageFormatter;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Set;
import org.apache.commons.collections4.CollectionUtils;

/** Sensor reports follow the EPCIS rules for sensorElementList (types, units, value shapes). */
@ApplicationScoped
public class SensorReportRule implements EventRule {

  public static final String ID = "sensor-report";

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
    return 400;
  }

  @Override
  public OnFailure onFailure() {
    return OnFailure.FAIL;
  }

  @Override
  public boolean applies(final RuleContext context) {
    return context.event().has(Constants.EVENT_ID)
        && EventPredicates.IS_SENSOR_EVENT.test(context.event());
  }

  @Override
  public Uni<List<Finding>> check(final RuleContext context) {
    return Uni.createFrom()
        .item(
            () -> {
              final Set<String> messages = SensorEventValidator.validate(context.event());
              return CollectionUtils.isEmpty(messages)
                  ? List.<Finding>of()
                  : List.of(Finding.hard(ValidationMessageFormatter.format(messages)));
            });
  }
}
