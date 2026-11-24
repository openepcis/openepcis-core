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
package io.openepcis.query.tests.util;

import io.openepcis.config.ScriptEngineConfig;
import io.openepcis.query.util.UnitConverter;
import jakarta.inject.Inject;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;

public class UnitConverterTest {

  private static final org.slf4j.Logger log =
      org.slf4j.LoggerFactory.getLogger(UnitConverterTest.class);

  @Inject ScriptEngineConfig scriptEngineConfig;

  @Inject UnitConverter unitConverter;

  @Inject ManagedExecutor managedExecutor;

  @Disabled
  public void relatedUOM() throws Exception {

    int numberOfThreads = scriptEngineConfig.poolSize() * 5;
    final CountDownLatch latch = new CountDownLatch(numberOfThreads);

    for (int i = 0; i < numberOfThreads; i++) {
      final int index = i + 1;
      managedExecutor.execute(
          () -> {
            try {
              final List<String> result = unitConverter.relatedUOM("KEL");
              Assertions.assertTrue(
                  result.contains("KEL") && result.contains("CEL") && result.contains("FAH"));
              log.debug("test[{}]", index);
            } finally {
              latch.countDown();
            }
          });
    }
    latch.await(10, TimeUnit.SECONDS);
  }
}
