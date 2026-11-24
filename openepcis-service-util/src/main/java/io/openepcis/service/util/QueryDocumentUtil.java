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
package io.openepcis.service.util;

import io.openepcis.model.epcis.*;
import io.openepcis.service.common.ContextBuilder;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;

@ApplicationScoped
public class QueryDocumentUtil {
  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(QueryDocumentUtil.class);

  private static boolean containsDuplicateNamespace(Map<?, ?> context) {
    HashSet<Object> seenValues = new HashSet<>();
    return context.values().stream().anyMatch(value -> !seenValues.add(value));
  }

  private static void handleDuplicateNamespacesFromGS1Extensions(Map<String, Object> context, Map<String, Object> gs1Extension) {
    context.entrySet().removeIf(entry -> gs1Extension.containsValue(entry.getValue()));
    context.putAll(gs1Extension);
  }

  private static void removeDuplicateNamespaces(Map<String, Object> context) {
    Map<String, Object> result = MapUtils.invertMap(MapUtils.invertMap(context));
    context.clear();
    context.putAll(result);
  }

  private static Map<String, List<String>> createPrefixMapping(Map<String, Object> context, Map<String, Object> originalContext) {
    return context.entrySet().stream().filter(entry -> originalContext.containsValue(entry.getValue())).collect(Collectors.toMap(Map.Entry::getKey, entry -> originalContext.entrySet().stream().filter(innerEntry -> Objects.equals(innerEntry.getValue(), entry.getValue()) && !Objects.equals(innerEntry.getKey(), entry.getKey())).map(Map.Entry::getKey).toList()));
  }

  private static void updateUserExtensionsInEvents(EPCISQueryDocument epcisQueryDocument, Map<String, Object> context, Map<String, Object> contextFromQueryDoc) {
    Map<String, List<String>> prefixMapping = createPrefixMapping(context, contextFromQueryDoc);
    epcisQueryDocument.getEpcisBody().getQueryResults().getResultsBody().getEventList().forEach(epcisEvent -> updateExtensions(epcisEvent, prefixMapping));
  }

  private static void updateExtensions(EPCISEvent epcisEvent, Map<String, List<String>> prefixMapping) {
    if (epcisEvent instanceof ObjectEvent) {
      updateIlmdExtensions((ObjectEvent) epcisEvent, prefixMapping);
    }
    if (epcisEvent instanceof TransformationEvent) {
      updateIlmdExtensions((TransformationEvent) epcisEvent, prefixMapping);
    }
    if (MapUtils.isNotEmpty(epcisEvent.getUserExtensions())) {
      epcisEvent.setUserExtensions(updatePrefix(epcisEvent.getUserExtensions(), prefixMapping));
    }
    if (MapUtils.isNotEmpty(epcisEvent.getExtension())) {
      epcisEvent.setExtension(updatePrefix(epcisEvent.getExtension(), prefixMapping));
    }
    if (MapUtils.isNotEmpty(epcisEvent.getInnerUserExtensions())) {
      epcisEvent.setInnerUserExtensions(updatePrefix(epcisEvent.getInnerUserExtensions(), prefixMapping));
    }
    updateSensorElementExtensions(epcisEvent.getSensorElementList(), prefixMapping);
  }

  private static void updateIlmdExtensionsCommon(Ilmd ilmd, Map<String, List<String>> prefixMapping) {
    if (Objects.nonNull(ilmd)) {
      if (Objects.nonNull(ilmd.getUserExtensions())) {
        ilmd.setUserExtensions(updatePrefix(ilmd.getUserExtensions(), prefixMapping));
      }
      if (Objects.nonNull(ilmd.getInnerUserExtensions())) {
        ilmd.setInnerUserExtensions(updatePrefix(ilmd.getInnerUserExtensions(), prefixMapping));
      }
    }
  }

  private static void updateIlmdExtensions(ObjectEvent objectEvent, Map<String, List<String>> prefixMapping) {
    updateIlmdExtensionsCommon(objectEvent.getIlmd(), prefixMapping);
    if (Objects.nonNull(objectEvent.getIlmdXml())) {
      objectEvent.setIlmdXml(updatePrefix(objectEvent.getIlmdXml(), prefixMapping));
    }
  }

  private static void updateIlmdExtensions(TransformationEvent transformationEvent, Map<String, List<String>> prefixMapping) {
    updateIlmdExtensionsCommon(transformationEvent.getIlmd(), prefixMapping);
    if (Objects.nonNull(transformationEvent.getIlmdXml())) {
      transformationEvent.setIlmdXml(updatePrefix(transformationEvent.getIlmdXml(), prefixMapping));
    }
  }

  private static void updateSensorElementExtensions(List<SensorElementList> sensorElements, Map<String, List<String>> prefixMapping) {
    if (!CollectionUtils.isEmpty(sensorElements)) {
      sensorElements.forEach(sensorElement -> {
        if (MapUtils.isNotEmpty(sensorElement.getExtension())) {
          sensorElement.setExtension(updatePrefix(sensorElement.getExtension(), prefixMapping));
        }
        if (MapUtils.isNotEmpty(sensorElement.getUserExtensions())) {
          sensorElement.setUserExtensions(updatePrefix(sensorElement.getUserExtensions(), prefixMapping));
        }
        if (MapUtils.isNotEmpty(sensorElement.getInnerUserExtensions())) {
          sensorElement.setInnerUserExtensions(updatePrefix(sensorElement.getInnerUserExtensions(), prefixMapping));
        }
      });
    }
  }

  private static Map<String, Object> updatePrefix(Map<String, Object> extensionMap, Map<String, List<String>> prefixMapping) {
    return extensionMap.entrySet().stream().collect(Collectors.toMap(entry -> {
      String field = entry.getKey();
      String matchingPrefix = prefixMapping.entrySet().stream().filter(entryset -> entryset.getValue().stream().anyMatch(field::startsWith)).map(Map.Entry::getKey).findFirst().orElse("");
      if (!matchingPrefix.isEmpty()) {
        return matchingPrefix + ":" + field.split(":")[1];
      } else {
        return field;
      }
    }, entry -> {
      if (entry.getValue() instanceof Map) {
        return updatePrefix((Map<String, Object>) entry.getValue(), prefixMapping);
      } else if (entry.getValue() instanceof List) {
        List list = new ArrayList();
        ((Collection<?>) entry.getValue()).forEach(item -> {
          if (item instanceof Map) {
            list.add(updatePrefix((Map<String, Object>) item, prefixMapping));
          } else {
            list.add(item);
          }
        });
        return list;
      } else {
        return entry.getValue();
      }
    }));
  }

  public static EPCISQueryDocument updateContextOfQueryDocument(EPCISQueryDocument epcisQueryDocument, Optional<String> gs1Extensions) {
    var context = epcisQueryDocument.getContext();
    if (context.size() >= 2 && context.get(1) instanceof Map) {
      final Map<String, Object> contextMap = (Map<String, Object>) context.remove(1);
      // create mutable copy
      final Map<String, Object> mutableContextMap = new HashMap<>(contextMap);
      final Map<String, Object> contextFromQueryDoc = new HashMap<>(contextMap);
      Optional<Map<String, Object>> gs1ExtensionsMap = ContextBuilder.getGs1ExtensionAsMap(gs1Extensions);
      gs1ExtensionsMap.ifPresent(stringObjectMap -> handleDuplicateNamespacesFromGS1Extensions(mutableContextMap, stringObjectMap));
      if (containsDuplicateNamespace(mutableContextMap)) {
        removeDuplicateNamespaces(mutableContextMap);
      }
      context.add(mutableContextMap);
      epcisQueryDocument.setContext(context);
      updateUserExtensionsInEvents(epcisQueryDocument, mutableContextMap, contextFromQueryDoc);
    }
    return epcisQueryDocument;
  }
}
