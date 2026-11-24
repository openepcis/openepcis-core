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
package io.openepcis.capture.spi;

import io.openepcis.validation.rule.Finding;
import io.openepcis.validation.rule.RuleContext;
import java.util.List;
import java.util.Map;

/**
 * Who may capture what. Runs on every capture path before the rule engine; unlike a rule it cannot
 * be disabled or softened by configuration. Its findings keep the event out.
 *
 * <p>The Community Edition lets every capture through ({@link OpenCaptureAuthorization}).
 */
public interface CaptureAuthorization {

  /** The claims of the capturing caller that rules may see, read from the capture metadata. */
  Map<String, Object> claims(Map<String, Object> metadata);

  /** @return blocking findings, empty when the capture may proceed */
  List<Finding> check(RuleContext context);
}
