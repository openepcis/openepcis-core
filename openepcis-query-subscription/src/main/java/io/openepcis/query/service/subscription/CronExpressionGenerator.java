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
package io.openepcis.query.service.subscription;

import static io.openepcis.model.epcis.constants.QueryParameterConstants.*;

import io.openepcis.model.epcis.Schedule;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;

public class CronExpressionGenerator {

  private static final CronExpressionGenerator utility = new CronExpressionGenerator();

  private CronExpressionGenerator() {}

  public static CronExpressionGenerator getInstance() {
    return utility;
  }

  public String generateCronExpression(Map<String, Object> params) {
    final StringBuilder cronExpression = new StringBuilder();

    cronExpression.append(params.getOrDefault(SECOND, "*"));
    cronExpression.append(" ");
    cronExpression.append(params.getOrDefault(MINUTE, "*"));
    cronExpression.append(" ");
    cronExpression.append(params.getOrDefault(HOUR, "*"));
    cronExpression.append(" ");

    if (params.containsKey(DAY_OF_WEEK)
        && StringUtils.equals(params.get(DAY_OF_WEEK).toString(), "*"))
      cronExpression.append(params.getOrDefault(DAY_OF_MONTH, "*"));
    else cronExpression.append(params.getOrDefault(DAY_OF_MONTH, "*"));
    cronExpression.append(" ");
    cronExpression.append(params.getOrDefault(MONTH, "*"));
    cronExpression.append(" ");

    if (cronExpression.lastIndexOf("*") == -1)
      cronExpression.append(params.getOrDefault(DAY_OF_WEEK, "*"));
    else cronExpression.append(params.getOrDefault(DAY_OF_WEEK, "*"));

    return cronExpression.length() == 0 ? null : cronExpression.toString();
  }

  public String generateCronExpression(Schedule schedule) {
    final StringBuilder cronExpression = new StringBuilder();

    cronExpression.append(
        StringUtils.isNotBlank(schedule.getSecond()) ? schedule.getSecond() : "*");
    cronExpression.append(" ");
    cronExpression.append(
        StringUtils.isNotBlank(schedule.getMinute()) ? schedule.getMinute() : "*");
    cronExpression.append(" ");
    cronExpression.append(StringUtils.isNotBlank(schedule.getHour()) ? schedule.getHour() : "*");
    cronExpression.append(" ");

    if (StringUtils.isNotBlank(schedule.getDayOfWeek())
        && StringUtils.equals(schedule.getDayOfWeek(), "*")) {
      cronExpression.append(
          StringUtils.isNotBlank(schedule.getDayOfMonth()) ? schedule.getDayOfMonth() : "*");
    } else {
      cronExpression.append(
          StringUtils.isNotBlank(schedule.getDayOfMonth()) ? schedule.getDayOfMonth() : "*");
    }
    cronExpression.append(" ");

    cronExpression.append(StringUtils.isNotBlank(schedule.getMonth()) ? schedule.getMonth() : "*");
    cronExpression.append(" ");

    if (cronExpression.lastIndexOf("*") == -1) {
      cronExpression.append(
          StringUtils.isNotBlank(schedule.getDayOfMonth()) ? schedule.getDayOfWeek() : "*");
    } else {
      cronExpression.append(
          StringUtils.isNotBlank(schedule.getDayOfWeek()) ? schedule.getDayOfWeek() : "*");
    }
    return cronExpression.length() == 0 ? null : cronExpression.toString();
  }
}
