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
package io.openepcis.validation.service;

import com.networknt.schema.ValidationMessage;
import java.util.Collection;
import java.util.Set;

public class ValidationMessageFormatter {

  private ValidationMessageFormatter() {}

  public static String format(final Collection<String> messages) {
    final StringBuilder stringBuilder = new StringBuilder();
    int counter = 1;
    for (String errorMessage : messages) {
      stringBuilder.append(counter++).append(") ").append(errorMessage).append(" ");
    }
    return stringBuilder.toString();
  }

  public static String format(final Set<ValidationMessage> messages) {
    final StringBuilder stringBuilder = new StringBuilder();
    int counter = 1;
    for (ValidationMessage message : messages) {
      stringBuilder.append(counter++).append(") ").append(message.getMessage()).append("\n");
    }
    return stringBuilder.toString();
  }
}
