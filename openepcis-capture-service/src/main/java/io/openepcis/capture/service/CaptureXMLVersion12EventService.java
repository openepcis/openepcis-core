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
package io.openepcis.capture.service;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openepcis.capture.context.CaptureContext;
import io.openepcis.constants.EPCISFormat;
import io.openepcis.constants.EPCISVersion;
import io.openepcis.converter.Conversion;
import io.openepcis.converter.VersionTransformer;
import io.openepcis.model.dto.InvalidEPCISEventInfo;
import io.openepcis.model.epcis.EPCISEvent;
import io.openepcis.model.epcis.constants.CommonConstants;
import io.openepcis.reactive.publisher.ObjectNodePublisher;
import io.openepcis.service.util.Constants;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.io.IOUtils;

@ApplicationScoped
public class CaptureXMLVersion12EventService extends CaptureJSONEventService {
  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(CaptureXMLVersion12EventService.class);
  @Inject
  VersionTransformer versionTransformer;

  @Override
  public EPCISVersion getEPCISVersion() {
    return EPCISVersion.VERSION_1_2_0;
  }

  @PostConstruct
  public void init() {
    super.init();
  }

  @Override
  public Uni<CaptureContext> captureDocument(final InputStream in, final CaptureContext context) {
    try {
      final InputStream[] inputStreams = inputStreamMultiplier.multiply(in, 2);
      return storageService.documentUpload(context, inputStreams[1]).chain(x -> Uni.createFrom().item(() -> {
        return getEPCISDocumentASJson(inputStreams[0]);
      }).chain(jsonStream -> {
        try {
          final ObjectNodePublisher<ObjectNode> publisher = new ObjectNodePublisher<>(jsonStream, () -> storageService.get(context.getStorageKey()));
          context.getObjectNodePublisher().set(publisher);
          storageService.publishToContext(publisher, context, managedExecutor);
          captureMetrics.documentCaptured(context.getDefaultGroup());
          return Uni.createFrom().item(context);
        } catch (IOException e) {
          return Uni.createFrom().failure(e);
        }
      }));
    } catch (Exception e) {
      return Uni.createFrom().failure(e);
    }
  }

  @Override
  public Uni<EPCISEvent> captureSingleEvent(final String captureString, final CaptureContext ctx) {
    try {
      final byte[] documentBytes = captureString.getBytes(StandardCharsets.UTF_8);
      final String storagePath = storageService.getStoragePath(ctx, cloudStorageConfig);
      final Map<String, String> tags = storageService.createCaptureTags(ctx);
      tags.put(CommonConstants.DOCUMENT_TYPE, Constants.EPCIS_DOCUMENT);
      storageService.documentUpload(storagePath, ctx.getCaptureID(), ctx.getContentType(), ctx.getContentLength(), tags, new ByteArrayInputStream(documentBytes));
      return super.captureSingleEventFromJSON(ctx.getGs1Extensions().get(), ctx.getMetadata().get(), ctx.getCaptureID(), storagePath, IOUtils.toString(getEPCISDocumentASJson(new ByteArrayInputStream(documentBytes)), StandardCharsets.UTF_8), ctx.getDefaultGroup());
    } catch (IOException e) {
      return Uni.createFrom().failure(e);
    }
  }

  @Override
  public Uni<List<InvalidEPCISEventInfo>> validateCaptureDocument(InputStream epcisDocument) throws IOException {
    return super.validateCaptureDocument(getEPCISDocumentASJson(epcisDocument));
  }

  private InputStream getEPCISDocumentASJson(InputStream inputStream) {
    try {
      return versionTransformer.convert(inputStream, Conversion.builder().fromMediaType(EPCISFormat.XML).fromVersion(EPCISVersion.VERSION_1_2_0).toMediaType(EPCISFormat.JSON_LD).toVersion(EPCISVersion.VERSION_2_0_0).build());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public Collection<EPCISFormat> getSupportedEPCISFormats() {
    return Set.of(EPCISFormat.XML);
  }

  public CaptureXMLVersion12EventService() {
  }
}
