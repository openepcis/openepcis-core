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
import io.quarkus.kafka.client.serialization.ObjectMapperSerializer;

/**
 * Kafka serializer for {@link EPCISValidationMessage}. Required by the reactive-messaging
 * persistence stage (EventPersistenceProcessor): SmallRye derives the serializer for the
 * incoming channel's dead-letter-queue producer by the {@code <Type>Serializer} convention,
 * so this must exist alongside {@link EPCISValidationMessageDeserializer}. Jackson-based to
 * match the ObjectMapperSerde used by the Kafka Streams consumer of epcis-event-persisted.
 */
public class EPCISValidationMessageSerializer
    extends ObjectMapperSerializer<EPCISValidationMessage> {}
