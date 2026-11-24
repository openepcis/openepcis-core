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
package io.openepcis.capture.kafka;

import io.quarkus.runtime.annotations.StaticInitSafe;
import io.smallrye.config.ConfigMapping;
import java.util.Map;

@StaticInitSafe
@ConfigMapping(prefix = "kafka")
public interface KafkaConfigurationService {
  Topics topics();

  Stores stores();

  Bootstrap bootstrap();

  /**
   * {@code kafka.sasl.*}, passed to the Kafka clients and not read here. Mapped only so that the
   * mapping accepts it: without quarkus-oidc on the class path the Kafka extension sets {@code
   * kafka.sasl.oauthbearer.jwt.validator.class} itself, and an unmapped key under this prefix fails
   * the startup.
   */
  Map<String, String> sasl();

  interface Bootstrap {
    String servers();
  }

  interface Topics {
    String epcisEventCaptured();

    String captureDocsEvent();

    String captureDocEventCount();

    String captureDocs();

    String eventValidated();

    String eventPersisted();

    String captureDocsAgg();

    String streamingSubscription();
  }

  interface Stores {
    String captureDocsStore();

    String globalCaptureJobMessageStore();
  }
}
