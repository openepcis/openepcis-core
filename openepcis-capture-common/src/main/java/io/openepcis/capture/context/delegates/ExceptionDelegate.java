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

import io.openepcis.capture.context.CaptureContext;
import io.openepcis.model.dto.CaptureJobStatusMessage;
import io.openepcis.model.dto.InvalidEPCISEventInfo;
import io.openepcis.repository.api.EventRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.function.Consumer;
import org.apache.commons.collections4.CollectionUtils;

@ApplicationScoped
public class ExceptionDelegate implements Consumer<CaptureContext> {
  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ExceptionDelegate.class);
  private final EventRepository reactiveRepository;

  @Override
  public void accept(CaptureContext context) {
    final CaptureJobStatusMessage captureJobStatusMessage = context.toCaptureJobStatusMessage();
    final List<InvalidEPCISEventInfo> invalidEPCISEventInfos = context.getThrowableRef().get().stream().map(throwable -> new InvalidEPCISEventInfo("InvalidEPCException", "Translation failed", 400, throwable.getLocalizedMessage())).toList();
    if (CollectionUtils.isNotEmpty(invalidEPCISEventInfos)) {
      captureJobStatusMessage.getErrors().addAll(invalidEPCISEventInfos);
    }
    reactiveRepository.saveCaptureJob(captureJobStatusMessage, context.getCaptureID(), context.getCreatedAt(), context.getDefaultGroup(), context.getMetadata().get()).subscribe().with(s -> log.info("Updated captureJob with captureID={} with creationDate={}", context.getCaptureID(), context.getCreatedAt()), throwable -> log.error("Error while updating captureJob with captureID={} with creationDate={}", context.getCaptureID(), context.getCreatedAt(), throwable));
  }

  public ExceptionDelegate(EventRepository reactiveRepository) {
    this.reactiveRepository = reactiveRepository;
  }
}
