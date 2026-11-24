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
package io.openepcis.capture.rules;

import com.fasterxml.jackson.databind.JsonNode;
import io.openepcis.model.dto.EPCIdentifier;
import io.openepcis.model.dto.EventLocationInEPCISDocument;
import io.openepcis.model.dto.InvalidEPCISEventInfo;
import io.openepcis.model.epcis.constants.CommonConstants;
import io.openepcis.capture.rules.CanonicalEpc;
import io.openepcis.validation.rule.Finding;
import io.openepcis.validation.rule.Severity;
import java.util.List;
import java.util.Set;

/**
 * Turns a {@link Finding} into the capture report entry, in the shape of the SmartHub Connect API
 * {@code CaptureErrorItem}: type, title, status, detail, severity, and where it points
 * ({@code eventLocationsInEPCISDocument}: index, eventId, eventHashId, jsonPath, xmlPath and the
 * identifiers in URN and Digital Link form). {@code sequenceInEPCISDoc} stays for older clients.
 */
public final class FindingReport {

  /** Fields that EPCIS 1.2 XML carries under {@code <extension>}, unlike JSON-LD. */
  private static final Set<String> XML_EXTENSION_FIELDS =
      Set.of("ilmd", "sourceList", "destinationList", "sensorElementList", "persistentDisposition");

  private FindingReport() {}

  public static InvalidEPCISEventInfo toInvalidEventInfo(
      final Finding finding, final JsonNode event, final int eventIndex) {
    final EventLocationInEPCISDocument location =
        new EventLocationInEPCISDocument()
            .index(eventIndex)
            .eventId(text(event, "eventID"))
            .eventHashId(text(event, "hash"))
            .jsonPath(finding.jsonPath())
            .xmlPath(xmlPath(finding.jsonPath(), text(event, "type")))
            .identifiers(identifiers(finding.identifiers()));
    return new InvalidEPCISEventInfo(
            finding.type() != null ? finding.type() : CommonConstants.CAPTURE_VALIDATION_EXCEPTION,
            finding.title() != null ? finding.title() : CommonConstants.VALIDATION_FAILED,
            finding.status() != null ? finding.status() : 400,
            finding.detail(),
            finding.sequence() != null ? finding.sequence() : List.of(eventIndex))
        .severity(
            (finding.severity() == null || finding.severity() == Severity.HARD_ERROR)
                ? InvalidEPCISEventInfo.Severity.HARD_ERROR
                : InvalidEPCISEventInfo.Severity.SOFT_ERROR)
        .eventLocationsInEPCISDocument(List.of(location));
  }

  /**
   * The XPath of a field in EPCIS XML for a JSONPath relative to the event: {@code $.bizLocation}
   * of an ObjectEvent is {@code /ObjectEvent/bizLocation}, {@code $.ilmd.x} is
   * {@code /ObjectEvent/extension/ilmd/x}. Array positions are dropped.
   */
  static String xmlPath(final String jsonPath, final String eventType) {
    if (jsonPath == null || !jsonPath.startsWith("$.") || jsonPath.length() < 3) {
      return null;
    }
    final String fields = jsonPath.substring(2).replaceAll("\\[[^]]*]", "");
    final String first = fields.contains(".") ? fields.substring(0, fields.indexOf('.')) : fields;
    final String path =
        (XML_EXTENSION_FIELDS.contains(first) ? "extension/" : "") + fields.replace('.', '/');
    return eventType == null || eventType.isBlank() ? path : "/" + eventType + "/" + path;
  }

  /** Each EPC as URN and Digital Link; an EPC that cannot be converted keeps the captured form. */
  static List<EPCIdentifier> identifiers(final List<String> epcs) {
    if (epcs == null || epcs.isEmpty()) {
      return null;
    }
    return epcs.stream()
        .map(CanonicalEpc::of)
        .map(epc -> new EPCIdentifier(epc.urn(), epc.canonicalDL()))
        .toList();
  }

  private static String text(final JsonNode event, final String field) {
    final JsonNode value = event == null ? null : event.get(field);
    return value == null || value.isNull() || value.asText().isBlank() ? null : value.asText();
  }
}
