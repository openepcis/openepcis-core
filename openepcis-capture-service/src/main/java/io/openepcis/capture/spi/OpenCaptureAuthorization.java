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
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Map;

/** Every capture may proceed, and rules see no claims. */
@DefaultBean
@ApplicationScoped
public class OpenCaptureAuthorization implements CaptureAuthorization {

  @Override
  public Map<String, Object> claims(final Map<String, Object> metadata) {
    return Map.of();
  }

  @Override
  public List<Finding> check(final RuleContext context) {
    return List.of();
  }
}
