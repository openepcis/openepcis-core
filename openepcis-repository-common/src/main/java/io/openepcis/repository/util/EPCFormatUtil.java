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
package io.openepcis.repository.util;

import io.openepcis.constants.EPCIS;
import io.openepcis.core.exception.UnsupportedGS1IdentifierException;
import io.openepcis.core.exception.UrnDLTransformationException;
import io.openepcis.core.exception.ValidationException;
import io.openepcis.identifiers.converter.StandardVocabConvertorUtil;
import io.openepcis.identifiers.converter.util.ConverterUtil;
import io.openepcis.model.epcis.format.EPCFormat;
import io.openepcis.repository.model.EpcES;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.stream.Collectors;
import static io.openepcis.repository.util.EventConvertor.convertContextToMap;

public class EPCFormatUtil {
  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(EPCFormatUtil.class);

  private EPCFormatUtil() {
    throw new UnsupportedOperationException("Invalid invocation of constructor");
  }

  public static List<EpcES> buildListOfEpcES(List<String> epcList, List<Object> context) throws ValidationException {
    if (CollectionUtils.isNotEmpty(epcList)) {
      List<EpcES> listOfEpcES = new ArrayList();
      for (String epc : epcList) listOfEpcES.add(EPCFormatUtil.buildEpc(epc, context));
      return listOfEpcES;
    } else {
      return null;
    }
  }

  public static EpcES buildEpc(String epcAsCaptured) throws ValidationException {
    if (StringUtils.isNotEmpty(epcAsCaptured)) {
      final EpcES epc = new EpcES();
      epc.setAsCaptured(epcAsCaptured);
      if (epcAsCaptured.startsWith("urn:")) {
        epc.setAsURN(epcAsCaptured);
        try {
          // GS1 URN -> canonical Digital Link
          epc.setCanonicalDL(ConverterUtil.toURI(epcAsCaptured));
        } catch (ValidationException e) {
        }
      } else 
      // non-GS1 URN (e.g. urn:epc:id:bic) has no Digital Link; keep the URN, leave canonicalDL null
      {
        // TODO: improve unsupported gs1 extension
        try {
          final Map<String, String> map = ConverterUtil.toURN(epcAsCaptured);
          epc.setAsURN(map.get("asURN"));
          epc.setCanonicalDL(map.get("canonicalDL"));
        } catch (ValidationException e) {
          epc.setAsCaptured(epcAsCaptured);
          if (epcAsCaptured.startsWith("http://") || epcAsCaptured.startsWith("https://")) {
            epc.setCanonicalDL(epcAsCaptured);
          }
        }
      }
      return epc;
    }
    return null;
  }

  public static EpcES buildEpc(String epcAsCaptured, List<Object> context) {
    if (StringUtils.isNotEmpty(epcAsCaptured)) {
      if (isExtensionValue(epcAsCaptured, context)) {
        final EpcES epc = new EpcES();
        epc.setAsCaptured(epcAsCaptured);
        return epc;
      } else {
        return buildEpc(epcAsCaptured);
      }
    }
    return null;
  }

  public static EpcES buildClassLevelEpc(String epcAsCaptured) throws ValidationException {
    if (StringUtils.isNotEmpty(epcAsCaptured)) {
      final EpcES epc = new EpcES();
      epc.setAsCaptured(epcAsCaptured);
      if (epcAsCaptured.startsWith("urn:")) {
        epc.setAsURN(epcAsCaptured);
        try {
          epc.setCanonicalDL(ConverterUtil.toURIForClassLevelIdentifier(epcAsCaptured));
        } catch (ValidationException e) {
        }
      } else 
      // non-GS1 URN (e.g. urn:epc:id:bic) has no Digital Link; keep the URN, leave canonicalDL null
      {
        final Map<String, String> map = ConverterUtil.toURNForClassLevelIdentifier(epcAsCaptured);
        epc.setAsURN(map.get("asURN"));
        epc.setCanonicalDL(map.get("canonicalDL"));
      }
      return epc;
    }
    return null;
  }

  public static EpcES buildClassLevelEpc(String epcAsCaptured, List<Object> context) throws ValidationException {
    if (StringUtils.isNotEmpty(epcAsCaptured)) {
      final EpcES epc = new EpcES();
      if (isExtensionValue(epcAsCaptured, context)) {
        epc.setAsCaptured(epcAsCaptured);
        return epc;
      } else {
        return buildClassLevelEpc(epcAsCaptured);
      }
    }
    return null;
  }

  public static List<EpcES> buildEpcESListForStandardVocab(List<String> epcList) throws ValidationException {
    if (CollectionUtils.isNotEmpty(epcList)) {
      List<EpcES> listOfEpcES = new ArrayList();
      for (String epc : epcList) listOfEpcES.add(EPCFormatUtil.buildEpcESForStandardVocab(epc));
      return listOfEpcES;
    } else {
      return null;
    }
  }

  public static EpcES buildEpcESForStandardVocab(String epcAsCaptured) throws ValidationException {
    if (StringUtils.isNotEmpty(epcAsCaptured)) {
      final EpcES epc = new EpcES();
      epc.setAsCaptured(epcAsCaptured);
      if (epcAsCaptured.startsWith("urn:") || epcAsCaptured.startsWith("gs1:")) {
        epc.setAsURN(epcAsCaptured);
        epc.setCanonicalDL(StandardVocabConvertorUtil.toURI(epcAsCaptured));
      } else {
        epc.setAsURN(StandardVocabConvertorUtil.toURN(epcAsCaptured));
        epc.setCanonicalDL(epcAsCaptured);
      }
      return epc;
    }
    return null;
  }

  public static List<String> getEpcAsCaptured(List<EpcES> epcList) {
    return CollectionUtils.isNotEmpty(epcList) ? epcList.stream().map(EpcES::getAsCaptured).collect(Collectors.toList()) : null;
  }

  public static List<String> getEpcAsExpected(List<EpcES> epcList, String epcFormat, List<Object> context) {
    return CollectionUtils.isNotEmpty(epcList) ? epcList.stream().map(e -> EPCFormatUtil.getEpcInExpectedFormat(e, epcFormat, context)).collect(Collectors.toList()) : null;
  }

  public static URI getEpcAsCaptured(EpcES epc) {
    try {
      return Objects.nonNull(epc) ? new URI(epc.getAsCaptured()) : null;
    } catch (URISyntaxException e) {
      log.error("Error while converting epc: {} to URI", epc.getAsCaptured(), e);
      throw new UrnDLTransformationException(String.format("Error while converting DL{%s} to URN", epc.getAsCaptured()), e);
    }
  }

  public static URI getEpcAsExpected(EpcES epc, String epcFormat) {
    try {
      final EPCFormat format = EPCFormat.fromString(epcFormat).orElse(EPCFormat.No_Preference);
      if (Objects.nonNull(epc)) {
        return switch (format) {
          // if value that could not be translated then retain the captured value in every case
          case Always_GS1_Digital_Link -> new URI(applyDigitalLinkHostOverride(StringUtils.isNotBlank(epc.getCanonicalDL()) ? epc.getCanonicalDL() : epc.getAsCaptured()));
          case Always_EPC_URN -> new URI(StringUtils.isNotBlank(epc.getAsURN()) ? epc.getAsURN() : epc.getAsCaptured());
          case Never_Translates -> new URI(applyDigitalLinkHostOverride(epc.getAsCaptured()));
          default -> new URI(applyDigitalLinkHostOverride(StringUtils.isNotBlank(epc.getCanonicalDL()) ? epc.getCanonicalDL() : epc.getAsCaptured()));
        };
      } else {
        return null;
      }
    } catch (URISyntaxException e) {
      log.error("Error while converting epc: {} to URI", epc.getAsCaptured(), e);
      throw new UrnDLTransformationException(String.format("Error while converting captured{%s} to URI", epc.getAsCaptured()), e);
    } catch (RuntimeException e) {
      log.error("Error while converting epc: {} to requested {} format", epc.getAsCaptured(), epcFormat, e);
      throw new UrnDLTransformationException(String.format("Error while converting epc: captured{%s} to requested %s format", epc.getAsCaptured(), epcFormat), e);
    }
  }

  public static URI getEpcAsExpected(EpcES epc, String epcFormat, List<Object> context) {
    try {
      if (Objects.nonNull(epc)) {
        if (isExtensionValue(epc.getAsCaptured(), context)) {
          return new URI(epc.getAsCaptured());
        } else {
          return getEpcAsExpected(epc, epcFormat);
        }
      } else {
        return null;
      }
    } catch (URISyntaxException e) {
      log.error("Error while converting epc: {} to URI", epc.getAsCaptured(), e);
      throw new UrnDLTransformationException(String.format("Error while converting DL{%s} to URN", epc.getAsCaptured()), e);
    }
  }

  public static String getEpcInExpectedFormat(EpcES epc, String epcFormat) {
    if (Objects.nonNull(epc)) {
      // if value that could not be translated then retain the captured value in every case
      if (epcFormat.equalsIgnoreCase("Always_GS1_Digital_Link")) {
        return applyDigitalLinkHostOverride(StringUtils.isNotBlank(epc.getCanonicalDL()) ? epc.getCanonicalDL() : epc.getAsCaptured());
      } else if (epcFormat.equalsIgnoreCase("Always_EPC_URN")) {
        return StringUtils.isNotBlank(epc.getAsURN()) ? epc.getAsURN() : epc.getAsCaptured();
      } else if (epcFormat.equalsIgnoreCase("Never_Translates")) {
        return applyDigitalLinkHostOverride(epc.getAsCaptured());
      }
      return applyDigitalLinkHostOverride(StringUtils.isNotBlank(epc.getCanonicalDL()) ? epc.getCanonicalDL() : epc.getAsCaptured());
    } else {
      return null;
    }
  }

  /**
   * Rewrites only the authority (host[:port]) of a GS1 Digital Link URI to the
   * value carried in the {@code GS1-Digital-Link-Host} request header, or to the
   * deployment's {@value DigitalLinkHostContext#DEFAULT_HOST_PROPERTY}. The
   * URI's scheme is preserved — http stays http, https stays https. AI paths
   * (e.g. {@code /01/.../21/…}) are preserved. Non-HTTP URIs (URNs, etc.) and
   * {@code null} pass through unchanged. Returns the original value when no
   * override is active for the current request and no default is configured.
   *
   * <p>The header value is interpreted as a bare authority (e.g.
   * {@code repo.example.com} or {@code repo.example.com:8443}). If a caller
   * mistakenly includes a scheme or path (e.g. {@code https://repo.example/…}),
   * the scheme and path are stripped and only the authority is honoured.
   */
  static String applyDigitalLinkHostOverride(final String url) {
    if (url == null || url.isEmpty()) {
      return url;
    }
    final String raw = DigitalLinkHostContext.currentHost();
    if (raw == null) {
      return url;
    }
    if (!url.startsWith("http://") && !url.startsWith("https://")) {
      return url;
    }
    String authority = raw.trim();
    final int schemeSep = authority.indexOf("://");
    if (schemeSep >= 0) {
      authority = authority.substring(schemeSep + 3);
    }
    final int pathStart = authority.indexOf('/');
    if (pathStart >= 0) {
      authority = authority.substring(0, pathStart);
    }
    if (authority.isEmpty()) {
      return url;
    }
    return url.replaceFirst("^(https?://)[^/]+", "$1" + java.util.regex.Matcher.quoteReplacement(authority));
  }

  public static String getEpcInExpectedFormat(EpcES epc, String epcFormat, List<Object> context) {
    if (Objects.nonNull(epc)) {
      if (isExtensionValue(epc.getAsCaptured(), context)) {
        return epc.getAsCaptured();
      } else {
        return getEpcInExpectedFormat(epc, epcFormat);
      }
    } else {
      return null;
    }
  }


  /**
   * Immutable snapshot of derived context lookups — a single volatile reference
   * guarantees atomic reads with zero torn-read risk under concurrency.
   */
  private record ContextSnapshot(int identity, Set<String> prefixes, Set<String> namespaces) {
  }

  private static volatile ContextSnapshot contextSnapshot = new ContextSnapshot(0, Set.of(), Set.of());

  private static boolean isExtensionValue(String epcAsCaptured, List<Object> context) {
    ContextSnapshot snap = contextSnapshot;
    final int identity = System.identityHashCode(context);
    if (snap.identity() != identity) {
      final Map<String, Object> contextMap = convertContextToMap(context);
      final Set<String> prefixes = new HashSet<>(contextMap.keySet());
      final Set<String> namespaces = new HashSet<>();
      for (Object v : contextMap.values()) {
        namespaces.add(v.toString());
      }
      snap = new ContextSnapshot(identity, prefixes, namespaces);
      contextSnapshot = snap;
    }
    final int colonIdx = epcAsCaptured.indexOf(':');
    final String prefix = colonIdx > 0 ? epcAsCaptured.substring(0, colonIdx) : epcAsCaptured;
    if (snap.prefixes().contains(prefix)) {
      return true;
    } else if (epcAsCaptured.startsWith("http://") || epcAsCaptured.startsWith("https://")) {
      final String namespace = epcAsCaptured.substring(0, epcAsCaptured.lastIndexOf('/'));
      return snap.namespaces().contains(namespace);
    }
    return false;
  }

  // If UserExtension contain EPCs then this method generates the EPC in various format
  // URN/DL/AsCaptured format and keep
  public static Object buildUserExtensionEpc(final String extensionKey, final String extensionValue) throws ValidationException {
    // If key is "@id" or extensionValue is not GS1 application identifier then return the value
    // directly without any conversion.
    if (EPCIS.AT_ID.equalsIgnoreCase(extensionKey) || StringUtils.isEmpty(extensionValue) || !isGS1Identifier(extensionValue)) {
      return extensionValue;
    }
    // For all other keys and values with GS1 Application Identifier convert to EpcES
    final EpcES epc = new EpcES();
    epc.setAsCaptured(extensionValue);
    try {
      if (extensionValue.startsWith(EPCIS.URN)) {
        epc.setAsURN(extensionValue);
        epc.setCanonicalDL(ConverterUtil.toURI(extensionValue));
      } else {
        final Map<String, String> urnMap = ConverterUtil.toURN(extensionValue);
        epc.setAsURN(urnMap.getOrDefault("asURN", extensionValue));
        epc.setCanonicalDL(urnMap.getOrDefault("canonicalDL", extensionValue));
      }
    } catch (Exception e) {
      return extensionValue;
    }
    return epc;
  }

  // Utility method to check if the string is a GS1 identifier
  private static boolean isGS1Identifier(final String value) {
    return value.startsWith(EPCIS.URN) || value.startsWith("https://") || value.startsWith("http://");
  }
}
