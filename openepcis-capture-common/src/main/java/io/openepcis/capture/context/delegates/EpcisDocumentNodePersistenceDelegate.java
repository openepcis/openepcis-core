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
package io.openepcis.capture.context.delegates;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openepcis.capture.context.CaptureContext;
import io.openepcis.constants.EPCIS;
import io.openepcis.repository.api.EventRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.function.BiConsumer;

/**
 * Saves EPCIS document in database
 */
@ApplicationScoped
public class EpcisDocumentNodePersistenceDelegate implements BiConsumer<CaptureContext, ObjectNode> {
  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(EpcisDocumentNodePersistenceDelegate.class);
  private final EventRepository reactiveRepository;

  @Override
  public void accept(CaptureContext context, ObjectNode objectNode) {
    if (context.getEpcisDocumentRef().get() != null) {
      return;
    }
    reactiveRepository.saveCaptureJob(context.toCaptureJobStatusMessage(), context.getCaptureID(), context.getCreatedAt(), context.getDefaultGroup(), context.getMetadata().get()).subscribe().with(s -> {
      if (log.isDebugEnabled()) log.debug("saved captureJob with captureID={} with creationDate={}", context.getCaptureID(), context.getCreatedAt());
    }, throwable -> log.error("Error while saving captureJob with captureID={} with creationDate={}", context.getCaptureID(), context.getCreatedAt(), throwable));
    context.getEpcisDocumentRef().set(objectNode);
    // Set the documentType in CaptureContext, it can be added as tags/metadata or for other
    // purposes in later stages
    context.setDocumentType(objectNode.get(EPCIS.TYPE).asText());
  }

  public EpcisDocumentNodePersistenceDelegate(EventRepository reactiveRepository) {
    this.reactiveRepository = reactiveRepository;
  }
}
