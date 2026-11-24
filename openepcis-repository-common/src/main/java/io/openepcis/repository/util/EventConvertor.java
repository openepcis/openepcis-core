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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openepcis.constants.EPCIS;
import io.openepcis.core.exception.ValidationException;
import io.openepcis.model.epcis.*;
import io.openepcis.model.epcis.exception.CaptureValidationException;
import io.openepcis.model.epcis.exception.ExceptionMessages;
import io.openepcis.model.epcis.format.EPCFormat;
import io.openepcis.model.epcis.format.FormatPreference;
import io.openepcis.model.epcis.util.DataTypeUtil;
import io.openepcis.repository.Constants;
import io.openepcis.repository.model.*;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;

import java.util.*;
import java.util.stream.Collectors;

import static io.openepcis.constants.EPCIS.EPCIS_DEFAULT_NAMESPACES;

public class EventConvertor {

  private EventConvertor() {
    // There should be no need to create object of this class as this Utility class
    // contains a bunch of Utility methods.
  }

  public static EPCISEventES getESRepresentation(EPCISEvent event, Map<String, Object> metadata)
      throws ValidationException {
    return getESRepresentation(event, metadata, event.getContextInfo());
  }

  public static EPCISEventES getESRepresentation(
      EPCISEvent event, Map<String, Object> metadata, List<Object> context)
      throws ValidationException {
    if (event instanceof AggregationEvent)
      return new AggregationEventES((AggregationEvent) event, metadata, context);
    else if (event instanceof AssociationEvent)
      return new AssociationEventES((AssociationEvent) event, metadata, context);
    else if (event instanceof ObjectEvent)
      return new ObjectEventES((ObjectEvent) event, metadata, context);
    else if (event instanceof TransformationEvent)
      return new TransformationEventES((TransformationEvent) event, metadata, context);
    else if (event instanceof TransactionEvent)
      return new TransactionEventES((TransactionEvent) event, metadata, context);
    else return new EPCISEventES(event, metadata, context);
  }

  public static <Y extends ESModel<T>, T> T getCoreModel(Y object) {
    return object != null ? object.getCoreModel() : null;
  }

  public static <Y extends ESModel<T>, T> T getCoreModel(Y object, List<Object> context) {
    return object != null ? object.getCoreModel(context) : null;
  }

  public static <Y extends ESModel<T>, T> T getCoreModel(
      Y object, FormatPreference preference, List<Object> context) {
    return object != null ? object.getCoreModel(preference, context) : null;
  }

  public static <Y extends ESModel<T>, T> List<T> getCoreModel(List<Y> list, List<Object> context) {
    if (CollectionUtils.isNotEmpty(list)) {
      return list.stream().map(item -> item.getCoreModel(context)).collect(Collectors.toList());
    }
    return null;
  }

  public static <Y extends ESModel<T>, T> List<T> getCoreModel(List<Y> list) {
    if (CollectionUtils.isNotEmpty(list)) {
      return list.stream().map(ESModel::getCoreModel).collect(Collectors.toList());
    }
    return null;
  }

  public static <Y extends ESModel<T>, T> List<T> getCoreModel(
      List<Y> list, FormatPreference preference, List<Object> context) {
    if (CollectionUtils.isNotEmpty(list)) {
      return list.stream()
          .map(l -> l.getCoreModel(preference, context))
          .collect(Collectors.toList());
    }
    return null;
  }

  public static Map<String, Object> getCoreModelUserExtensions(
      final List<Map<String, Object>> userExtensions) {
    if (CollectionUtils.isNotEmpty(userExtensions)) {
      return userExtensions.stream()
          .filter(Objects::nonNull)
          .filter(m -> m.get(Constants.KEY) != null)
          .filter(m -> m.get(getValueKey(m)) != null)
          .collect(
              Collectors.toMap(
                  map -> map.get(Constants.KEY).toString(),
                  map -> getUserExtensionsInExpectedFormat(map.get(getValueKey(map))),
                  (a, b) -> a));
    }
    return null;
  }

  public static String getValueKey(final Map<String, Object> userExtension) {
    if (userExtension.containsKey(Constants.LONG_VALUE)) return Constants.LONG_VALUE;
    else if (userExtension.containsKey(Constants.BOOLEAN_VALUE)) return Constants.BOOLEAN_VALUE;
    else if (userExtension.containsKey(Constants.DOUBLE_VALUE)) return Constants.DOUBLE_VALUE;
    else if (userExtension.containsKey(Constants.DATE_VALUE)) return Constants.DATE_VALUE;
    else if (userExtension.containsKey(Constants.KEYWORD_VALUE)) return Constants.KEYWORD_VALUE;
    else return Constants.OBJECT_VALUE;
  }

  // Method to process and store the userExtensions, if userExtensions contain GS1 AI then convert
  // them to EpcEs
  public static List<Map<String, Object>> getUserExtensionsFromCoreModel(
      final List<Object> context, final Map<String, Object> userExtensions) {
    if (MapUtils.isNotEmpty(userExtensions)) {
      return userExtensions.entrySet().stream()
          .map(
              entry -> {
                // Compute the value to be stored as string/boolean/object for each extension
                final String path = getExpandedPathForUserExtension(context, entry.getKey());
                final String key = entry.getKey().substring(entry.getKey().lastIndexOf(".") + 1);
                final String expandedKey =
                    getExpandedKeyForUserExtension(
                        context,
                        entry.getKey().contains(":")
                            ? entry
                                .getKey()
                                .substring(entry.getKey().lastIndexOf(".") + 1)
                                .split(":")
                            : new String[] {entry.getKey()});
                final Object computedExtensionValue =
                    userExtensionsBuild(entry.getKey(), entry.getValue());
                return Map.of(
                    Constants.PATH,
                    path,
                    Constants.KEY,
                    key,
                    Constants.EXPANDED_KEY,
                    expandedKey,
                    DataTypeUtil.getValueKeyFromType(computedExtensionValue)
                        .concat(Constants.VALUE),
                    computedExtensionValue);
              })
          .collect(Collectors.toList());
    }
    return null;
  }

  public static String getExpandedKeyForUserExtension(
      final List<Object> context, final String[] field) {
    // Check if the Context contains empty namespaces map
    if (Objects.nonNull(context)) {
      boolean containsEmptyMap =
          context.stream()
              .filter(obj -> obj instanceof Map)
              .map(obj -> (Map<?, ?>) obj)
              .anyMatch(Map::isEmpty);
      if ((!containsEmptyMap || EPCIS_DEFAULT_NAMESPACES.containsKey(field[0])) && isNotExtensionField(field)) {
        final JSONObject jsonContext = JSONUtil.asJSONObject(convertContextToMap(context));
        final JSONObject defaultContext = JSONUtil.asJSONObject(EPCIS_DEFAULT_NAMESPACES);

        // Get the value corresponding to prefix from either user-defined context or EPCIS default
        // context
        final String namespace =
            jsonContext.optString(field[0], defaultContext.optString(field[0]));
        if (!StringUtils.isBlank(namespace)) {
          return namespace + (namespace.endsWith("/") ? "" : "/") + field[1];
        } else {
          throw new CaptureValidationException(
              ExceptionMessages.EXPANDED_JSONLD_DOCUMENT_DOESN_T_CONTAIN_VALID_NAMESPACE);
        }
      }
    }
    return field[0];
  }

  public static String getExpandedKeyForUserExtension(
      final Map<String, Object> context, final String[] field) {
    final String namespace =
        context.containsKey(field[0])
            ? context.get(field[0]).toString()
            : EPCIS_DEFAULT_NAMESPACES.getOrDefault(field[0], "").toString();

    // Get the namespace from either user-defined namespaces or default EPCIS namespaces
    if (!StringUtils.isBlank(namespace)) {
      return namespace.endsWith("/")
          ? namespace.concat(field[1])
          : namespace.concat("/").concat(field[1]);
    } else {
      throw new CaptureValidationException(
          ExceptionMessages.EXPANDED_JSONLD_DOCUMENT_DOESN_T_CONTAIN_VALID_NAMESPACE);
    }
  }

  public static String getExpandedPathForUserExtension(final List<Object> context, String path) {
    final JSONObject jsonContext = JSONUtil.asJSONObject(convertContextToMap(context));
    final JSONObject defaultContext = JSONUtil.asJSONObject(EPCIS_DEFAULT_NAMESPACES);
    final String[] arr = StringUtils.substringsBetween(path, ".", ":");

    if (arr != null) {
      for (String a : arr) {
        if (a.contains(".")) {
          a = a.substring(a.indexOf(".") + 1);
        }

        // Get the namespace from user-defined or default context based on it form the path
        final String namespace = jsonContext.optString(a, defaultContext.optString(a));
        if (!StringUtils.isBlank(namespace)) {
          path = path.replace(a + ":", namespace.endsWith("/") ? namespace : namespace + "/");
        } else {
          throw new CaptureValidationException(
              ExceptionMessages.EXPANDED_JSONLD_DOCUMENT_DOESN_T_CONTAIN_VALID_NAMESPACE);
        }
      }
    }
    return path;
  }

  public static String getExpandedPathForUserExtension(
      final Map<String, Object> context, String path) {
    final String[] arr = StringUtils.substringsBetween(path, ".", ":");
    for (String a : arr) {
      if (context.containsKey(a)) {
        if (context.get(a).toString().endsWith("/")) {
          path = path.replace(a + ":", context.get(a).toString());
        } else {
          path = path.replace(a + ":", context.get(a).toString().concat("/"));
        }

      } else {
        throw new CaptureValidationException(
            ExceptionMessages.EXPANDED_JSONLD_DOCUMENT_DOESN_T_CONTAIN_VALID_NAMESPACE);
      }
    }
    return path;
  }

  public static void mapUserExtensionToDataType(
      Map<String, Object> context,
      Map<String, Object> userExtensions,
      Set<UserExtensionField> newFields) {
    final Date currentDate = new Date();
    userExtensions.forEach(
        (k, v) -> {
          String expandedKey =
              getExpandedKeyForUserExtension(
                  context, k.substring(k.lastIndexOf(".") + 1).split(":"));
          newFields.add(
              new UserExtensionField(
                  expandedKey, DataTypeUtil.getValueKeyFromType(v), currentDate));
        });
  }

  private static final ObjectMapper SHARED_MAPPER = new ObjectMapper();

  public static HashMap<String, Object> convertContextToMap(List<Object> context) {
    if (context == null || context.isEmpty()) {
      return new HashMap<>();
    }

    final HashMap<String, Object> result = new HashMap<>();
    for (Object m : context) {
      if (m instanceof Map<?, ?> map) {
        map.forEach((k, v) -> result.put(String.valueOf(k), v));
      } else {
        result.putAll(SHARED_MAPPER.convertValue(m, HashMap.class));
      }
    }
    return result;
  }

  private static boolean isNotExtensionField(String[] field) {
    return Objects.nonNull(field) && field.length > 1 && !EPCIS.EXTENSION.equals(field[0]);
  }

  // For GS1 AI within userExtensions to build EpcES format with asCaptured, DL and URN format while
  // storing to OpenSearch
  private static Object userExtensionsBuild(final String key, final Object value) {
    if (value instanceof Map) {
      return ((Map<?, ?>) value)
          .entrySet().stream()
              .collect(
                  Collectors.toMap(
                      entry -> entry.getKey().toString(),
                      entry ->
                          userExtensionsBuild(
                              entry.getKey().toString(), entry.getValue()) // Recurse for value
                      ));
    } else if (value instanceof List) {
      // Recursively process lists
      return ((List<?>) value)
          .stream().map(item -> userExtensionsBuild(key, item)).collect(Collectors.toList());
    } else if (value instanceof String stringValue) {
      return EPCFormatUtil.buildUserExtensionEpc(key, stringValue);
    } else {
      return value;
    }
  }

  // Method called from coreModel (EPCISEventEs, ObjectEventEs, ReadPointEs, etc.) to get the URN/DL
  // formatted identifiers based on format preference passed or default values
  public static Map<String, Object> getCoreModelUserExtensionsWithFormat(
      final List<Map<String, Object>> userExtensions,
      final String epcFormat,
      final List<Object> context) {
    if (CollectionUtils.isNotEmpty(userExtensions)) {
      return userExtensions.stream()
          .filter(Objects::nonNull)
          .filter(m -> m.get(Constants.KEY) != null)
          .filter(m -> m.get(getValueKey(m)) != null)
          .collect(
              Collectors.toMap(
                  map -> map.get(Constants.KEY).toString(),
                  map ->
                      getUserExtensionsInExpectedFormat(
                          map.get(getValueKey(map)), epcFormat, context),
                  (a, b) -> a));
    }
    return null;
  }

  // Method to get the extension value in respective format using the recursive approach during the
  // call from coreModel
  private static Object getUserExtensionsInExpectedFormat(final Object value) {
    return getUserExtensionsInExpectedFormat(value, EPCFormat.No_Preference.getEPCFormat(), null);
  }

  // Method to get the extension value using the recursive approach such as asCaptured or based on
  // epcFormat or the default value
  private static Object getUserExtensionsInExpectedFormat(
      final Object value, final String epcFormat, final List<Object> context) {
    if (value instanceof EpcES epcEs) {
      return EPCFormatUtil.getEpcInExpectedFormat(epcEs, epcFormat);
    } else if (value instanceof Map<?, ?> mapValue) {
      return mapValue.entrySet().stream()
          .filter(ele -> ele.getKey() != null && ele.getValue() != null)
          .collect(
              Collectors.toMap(
                  Map.Entry::getKey,
                  element -> {
                    if (element.getValue() instanceof Map<?, ?> elementMap
                        && elementMap.containsKey(Constants.AS_CAPTURED)) {
                      return elementMap.get(Constants.AS_CAPTURED);
                    }
                    return getUserExtensionsInExpectedFormat(
                        element.getValue(), epcFormat, context);
                  }));
    } else if (value instanceof List<?> listValue) {
      return listValue.stream()
          .map(element -> getUserExtensionsInExpectedFormat(element, epcFormat, context))
          .collect(Collectors.toList());
    } else {
      return value;
    }
  }
}
