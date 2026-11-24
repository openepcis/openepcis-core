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
package io.openepcis.service.common;

import jakarta.enterprise.context.ApplicationScoped;
import java.time.Duration;
import java.time.OffsetDateTime;

@ApplicationScoped
public class TimeUtil {
  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(TimeUtil.class);

  public static void waitForIndexing(OffsetDateTime timeOfCapture) {
    OffsetDateTime currentTime = OffsetDateTime.now();
    Duration timeDifference = Duration.between(timeOfCapture, currentTime);
    long sleepTimeMillis = 1500L - timeDifference.toMillis();
    if (sleepTimeMillis > 0 && sleepTimeMillis <= 1500) {
      try {
        Thread.sleep(sleepTimeMillis);
      } catch (InterruptedException e) {
        log.error("Error while waiting to index ", e);
      }
    }
  }

  public TimeUtil() {
  }
}
