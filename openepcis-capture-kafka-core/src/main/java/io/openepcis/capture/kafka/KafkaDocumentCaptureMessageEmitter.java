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

import io.openepcis.capture.context.delegates.DocumentCaptureMessageEmitter;
import io.openepcis.capture.context.message.DocumentCaptureMessage;
import io.smallrye.reactive.messaging.kafka.Record;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.concurrent.CompletionStage;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

@ApplicationScoped
public class KafkaDocumentCaptureMessageEmitter implements DocumentCaptureMessageEmitter {
  @Inject
  @Channel("event-captured-out")
  Emitter<Record<String, DocumentCaptureMessage>> eventEmitter;

  @Override
  public CompletionStage<Void> send(String captureID, DocumentCaptureMessage message) {
    return eventEmitter.send(Record.of(captureID, message));
  }

  public KafkaDocumentCaptureMessageEmitter() {
  }
}
