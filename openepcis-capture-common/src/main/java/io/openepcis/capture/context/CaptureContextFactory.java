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
package io.openepcis.capture.context;

import io.openepcis.capture.context.delegates.CaptureContextDelegation;
import io.openepcis.capture.context.delegates.EventCountEmitter;
import io.openepcis.capture.context.delegates.ExceptionDelegate;
import io.openepcis.constants.CBVVersion;
import io.openepcis.constants.EPCISFormat;
import io.openepcis.constants.EPCISVersion;
import io.openepcis.service.config.CloudStorageConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

@ApplicationScoped
public class CaptureContextFactory {
  private final CaptureContextDelegation captureContextDelegation;
  private final ExceptionDelegate exceptionDelegate;
  @Inject
  EventCountEmitter eventCountEmitter;
  @Inject
  CloudStorageConfig cloudStorageConfig;
  @Inject
  StorageService storageService;

  public CaptureContext createContext(final String captureErrorBehaviour, final Optional<Long> contentLength, final String contentType, final String defaultGroup) {
    return new DefaultCaptureContext(contentType, EPCISVersion.VERSION_2_0_0, this.captureContextDelegation, this.eventCountEmitter, captureErrorBehaviour, contentLength, new AtomicReference<>(), this.exceptionDelegate, defaultGroup, cloudStorageConfig, storageService);
  }

  public <T> CaptureContext createContext(EPCISVersion epcisVersion, CBVVersion cbvVersion, EPCISFormat epcisFormat, String captureErrorBehaviour, Optional<Long> contentLength, String contentType, Optional<String> gs1Extensions, Map<String, Object> metadata, String defaultGroup) {
    final DefaultCaptureContext ctx = new DefaultCaptureContext(contentType, epcisVersion, this.captureContextDelegation, this.eventCountEmitter, captureErrorBehaviour, contentLength, new AtomicReference<>(), this.exceptionDelegate, defaultGroup, cloudStorageConfig, storageService);
    ctx.setCbvVersion(cbvVersion);
    ctx.getMetadata().set(new ConcurrentHashMap<>(metadata));
    if (gs1Extensions.isPresent()) {
      ctx.getGs1Extensions().set(gs1Extensions);
    }
    return ctx;
  }

  public CaptureContextFactory(CaptureContextDelegation captureContextDelegation, ExceptionDelegate exceptionDelegate) {
    this.captureContextDelegation = captureContextDelegation;
    this.exceptionDelegate = exceptionDelegate;
  }
}
