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
package io.openepcis.capture.service.test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.rholder.retry.Retryer;
import io.openepcis.capture.context.CaptureContext;
import io.openepcis.capture.context.CaptureContextFactory;
import io.openepcis.capture.context.StorageService;
import io.openepcis.capture.service.*;
import io.openepcis.capture.service.test.commons.FileUtils;
import io.openepcis.capture.service.test.config.ServiceTestConfig;
import io.openepcis.model.epcis.exception.CaptureValidationException;
import io.openepcis.repository.api.EventRepository;
import io.openepcis.service.config.CloudStorageConfig;
import io.smallrye.mutiny.Uni;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

public class CaptureEventServiceTestDisabled {
  private final EventRepository eventReactiveRepository = Mockito.mock(EventRepository.class);
  private final ObjectMapper objectMapper = ServiceTestConfig.objectMapper();
  private final CaptureConfigurationService captureConfigService =
      Mockito.mock(CaptureConfigurationService.class);
  private final CaptureContextFactory captureContextFactory =
      Mockito.mock(CaptureContextFactory.class);
  private final CloudStorageConfig cloudStorageConfig = Mockito.mock(CloudStorageConfig.class);
  private final StorageService storageService = Mockito.mock(StorageService.class);
  private final EPCISEventValidationService eventValidationService =
      Mockito.mock(EPCISEventValidationService.class);
  private final EPCISEventPersistenceService epcisEventPersistenceService =
      Mockito.mock(EPCISEventPersistenceService.class);
  private final Retryer<Boolean> retryer;
  private CaptureJSONEventService captureEventService;

  public CaptureEventServiceTestDisabled(Retryer<Boolean> retryer) {
    this.retryer = retryer;
  }

  @Test
  public void testCaptureDocumentNullInput() throws JsonProcessingException {
    final CaptureContext ctx =
        captureContextFactory.createContext(
            "rollback", Optional.empty(), StringUtils.EMPTY, "test");
    Assertions.assertThrows(CaptureValidationException.class,() -> captureEventService.captureDocument(null, ctx));
  }

  @BeforeEach
  public void setUp() throws Exception {
    Mockito.when(cloudStorageConfig.bucket()).thenReturn("openepcis");
    Mockito.when(cloudStorageConfig.jsonSchemaKeyPrefix()).thenReturn("json-schema/");
    Mockito.when(cloudStorageConfig.documentKeyPrefixDateFormat())
        .thenReturn("'capture-document/'yyyy/MM/dd/");
    captureEventService = Mockito.spy(new CaptureJSONEventService());
    captureEventService.init();
  }

  @Test
  public void testCaptureDocument() throws Exception {

    Mockito.doReturn("{\"example\":\"http://ns.example.com/epcis/\"}")
        .when(captureEventService)
        .getContextInfo(ArgumentMatchers.any(), ArgumentMatchers.anyMap());

    Mockito.when(captureConfigService.limit()).thenReturn(20);
    Mockito.when(captureConfigService.fileSizeLimit()).thenReturn(Double.valueOf(5000d));

    var epcisDocument =
        FileUtils.loadFileContentAsString(
            "/JSONSchemaConstraintValidations/ObjectEvent-action-field-missing.jsonld");
    final CaptureContext ctx =
        captureContextFactory.createContext(
            "rollback", Optional.empty(), StringUtils.EMPTY, ArgumentMatchers.anyString());
    Uni<CaptureContext> captureId =
        captureEventService.captureDocument(
            new ByteArrayInputStream(epcisDocument.getBytes(StandardCharsets.UTF_8)), ctx);

    Assertions.assertNotNull(captureId.await().indefinitely());

    Mockito.verify(eventReactiveRepository, Mockito.times(1))
        .saveCaptureJob(
            ArgumentMatchers.any(),
            ArgumentMatchers.anyString(),
            ArgumentMatchers.any(OffsetDateTime.class),
            ArgumentMatchers.anyString(),
            ArgumentMatchers.any());
  }
}
