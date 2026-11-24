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
package io.openepcis.query.util;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

public class DateUtility {

  private static DateUtility utility = new DateUtility();

  private DateUtility() {}

  public static DateUtility getInstance() {
    return utility;
  }

  public OffsetDateTime parse(String value) {
    return OffsetDateTime.parse(value, DateTimeFormatter.ISO_DATE_TIME);
  }
}
