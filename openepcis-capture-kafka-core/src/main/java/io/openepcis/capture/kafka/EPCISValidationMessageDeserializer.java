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

import io.openepcis.capture.context.message.EPCISValidationMessage;
import io.quarkus.kafka.client.serialization.JsonbDeserializer;

/**
 * Kafka deserializer for {@link EPCISValidationMessage} used by the reactive-messaging
 * persistence stage that consumes the {@code epcis-event-validated-success} topic. Mirrors the
 * JSON-B serializer configured on {@code epcis-event-validated-success-out}.
 */
public class EPCISValidationMessageDeserializer extends JsonbDeserializer<EPCISValidationMessage> {
  public EPCISValidationMessageDeserializer() {
    super(EPCISValidationMessage.class);
  }
}
