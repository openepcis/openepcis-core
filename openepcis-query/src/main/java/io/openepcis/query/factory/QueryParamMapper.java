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
package io.openepcis.query.factory;

import io.openepcis.core.exception.UrnDLTransformationException;
import io.openepcis.core.exception.ValidationException;
import io.openepcis.constants.EPCIS;
import io.openepcis.identifiers.converter.StandardVocabConvertorUtil;
import io.openepcis.identifiers.converter.util.ConverterUtil;
import io.openepcis.model.epcis.Action;
import io.openepcis.model.epcis.exception.ExceptionMessages;
import io.openepcis.model.epcis.exception.QueryParameterException;
import io.openepcis.model.epcis.exception.QueryValidationException;
import io.openepcis.query.data.*;
import io.openepcis.query.util.DateUtility;
import io.openepcis.query.util.UnitConverter;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import java.text.ParseException;
import java.time.DateTimeException;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class QueryParamMapper implements Function<Map.Entry<String, Object>, QueryParam> {
  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(QueryParamMapper.class);
  private static final String PIPE = "|";
  private static final String PIPE_REGEX = "\\|";
  private final UnitConverter unitConverter;
  private final boolean shouldAllowSingleValues;
  private Object context;
  private final Optional<Map<String, Object>> gs1Extensions;
  private final Set<QueryParamName> epcOrientedQueryParams = Set.of(QueryParamName.EPC, QueryParamName.EQ_readPoint, QueryParamName.EQ_bizLocation, QueryParamName.MATCH_epc, QueryParamName.MATCH_inputEPC, QueryParamName.MATCH_outputEPC, QueryParamName.MATCH_anyEPC, QueryParamName.EQ_deviceID, QueryParamName.MATCH_parentID);
  private final Set<QueryParamName> classLevelEpcOrientedQueryParams = Set.of(QueryParamName.EPC, QueryParamName.MATCH_epcClass, QueryParamName.MATCH_inputEPCClass, QueryParamName.MATCH_outputEPCClass, QueryParamName.MATCH_anyEPCClass);
  private final Set<QueryParamName> epcOrientedStandardVocabQueryParams = Set.of(QueryParamName.EQ_bizStep, QueryParamName.EQ_errorReason, QueryParamName.EQ_disposition, QueryParamName.EQ_persistentDisposition_set, QueryParamName.EQ_persistentDisposition_unset);
  private final Set<QueryParamName> dateTimeQueryParams = Set.of(QueryParamName.values()).stream().filter(n -> ValueType.DATE_TIME.equals(n.getValueType())).collect(Collectors.toSet());
  private final Set<QueryParamName> typeQueryParam = Set.of(QueryParamName.EQ_bizTransaction_type, QueryParamName.EQ_destination_type, QueryParamName.EQ_source_type);

  public QueryParamMapper(boolean shouldAllowSingleValues, UnitConverter unitConverter, Optional<Map<String, Object>> gs1Extensions) {
    this.shouldAllowSingleValues = shouldAllowSingleValues;
    this.unitConverter = unitConverter;
    this.gs1Extensions = gs1Extensions;
  }

  public QueryParamMapper(boolean shouldAllowSingleValues, UnitConverter unitConverter, Object context, Optional<Map<String, Object>> gs1Extesnions) {
    this.shouldAllowSingleValues = shouldAllowSingleValues;
    this.unitConverter = unitConverter;
    this.context = context;
    this.gs1Extensions = gs1Extesnions;
  }

  /**
   * The number a sensor-value filter compares against. A JSON body names it as a number
   * ({@code "GT_value_CEL": 25}), a URL or a list as text; both mean the same. The single value
   * used to go on as text, and the unit converter's cast to Number failed with a 500 when a named
   * query was saved.
   */
  static Double measuredValue(final String paramName, final Object value) {
    if (value instanceof Number number) {
      return number.doubleValue();
    }
    try {
      return Double.parseDouble(String.valueOf(value).trim());
    } catch (NumberFormatException e) {
      throw new QueryParameterException(paramName + " needs a number, got: " + value);
    }
  }

  @Override
  public QueryParam apply(Map.Entry<String, Object> entry) {
    Object value = entry.getValue();
    final QueryParamName paramName = QueryParamName.parse(entry.getKey());
    if (shouldAllowSingleValues && (paramName.getValueType() == ValueType.STRING_LIST || paramName.getValueType() == ValueType.PAIR) && !(value instanceof Collection) && paramName.getOperator() == OperatorType.EQ) {
      if (value instanceof Boolean) {
        value = List.of((Boolean) value);
      } else {
        if (value.toString().contains(PIPE)) {
          value = List.of(value.toString().split(PIPE_REGEX));
        } else {
          value = List.of(value.toString());
        }
      }
    }
    if (paramName.getValueType() == ValueType.PAIR && !paramName.isUserExtension()) {
      final Map<String, Number> uomValueMap = new HashMap<>();
      if (value instanceof Collection) {
        for (Object v : (List<Object>) value) {
          Pair<String, Object> uomValuePair = new ImmutablePair<>(entry.getKey().substring(entry.getKey().lastIndexOf("_") + 1), measuredValue(entry.getKey(), v));
          uomValueMap.putAll(unitConverter.multiConvertValueUOM(uomValuePair));
        }
      } else {
        Pair<String, Object> uomValuePair = new ImmutablePair<>(entry.getKey().substring(entry.getKey().lastIndexOf("_") + 1), measuredValue(entry.getKey(), value));
        uomValueMap.putAll(unitConverter.multiConvertValueUOM(uomValuePair));
      }
      value = uomValueMap;
    }
    if (paramName.isUserExtension()) {
      if (paramName.getQueryType().equals(EpcisQueryType.SIMPLE_EVENT_QUERY)) {
        final Object v = value;
        value = getFullyQualifiedPathOfCUIRE(paramName.name(), entry.getKey()).stream().collect(Collectors.toMap(s -> s, s -> v));
      } else {
        value = Map.of(entry.getKey().substring(entry.getKey().lastIndexOf("_") + 1), value);
      }
    }
    if (paramName.isAppendURIPrefix()) {
      List<String> uriValue = new ArrayList<>();
      try {
        if (value instanceof Collection) {
          for (String val : (List<String>) value) {
            uriValue.add(appendURIPrefixToValue(val, paramName.getUriPrefix()));
          }
        }
      } catch (ClassCastException exception) {
        log.error(String.format(ExceptionMessages.VALUE_FOR_PARAM_NOT_AS_EXPECTED, paramName));
        throw new QueryParameterException(String.format(ExceptionMessages.VALUE_FOR_PARAM_NOT_AS_EXPECTED, paramName));
      }
      value = uriValue;
    }
    validate(paramName, value);
    if (epcOrientedQueryParams.contains(paramName)) value = gs1Extensions.isPresent() ? convertAsPerGs1Extension((List<String>) value) : convertToURN((List<String>) value);
    if (classLevelEpcOrientedQueryParams.contains(paramName)) value = gs1Extensions.isPresent() ? convertClassLevelEPCAsPerGs1Extension((List<String>) value) : convertClassLevelEPCToURN((List<String>) value);
    if (epcOrientedStandardVocabQueryParams.contains(paramName)) value = convertToURNForStandardVocab((List<String>) value);
    if (dateTimeQueryParams.contains(paramName)) {
      if (value instanceof Collection<?>) {
        final Object o = ((Collection<?>) value).stream().toList().get(0);
        if (o instanceof String) {
          value = DateUtility.getInstance().parse(o.toString());
        } else if (o instanceof Date) {
          value = OffsetDateTime.from(((Date) o).toInstant());
        } else if (o instanceof OffsetDateTime) {
          value = o;
        }
      }
    }
    if (typeQueryParam.contains(paramName)) {
      return new TypeQueryParam(entry.getKey(), value, paramName.getUriPrefix(), entry.getKey());
    }
    return new QueryParam(entry.getKey(), value);
  }

  private List<String> convertAsPerGs1Extension(List<String> value) {
    try {
      return value.stream().map(epc -> gs1Extensions.get().containsKey(epc.split(":")[0]) || epc.startsWith("urn:") ? epc : convertEpcToURN(epc)).toList();
    } catch (UrnDLTransformationException exception) {
      throw new QueryValidationException("Custom value could not be resolved. GS1-Extensions contains invalid prefix", exception);
    }
  }

  private String convertEpcToURN(String epc) {
    final Map<String, String> map;
    try {
      map = ConverterUtil.toURN(epc);
    } catch (ValidationException e) {
      // Per EPCIS 2.0 §8.2.7.1.1, MATCH_anyEPC accepts any URI — URN, GS1
      // Digital Link, or wildcard pattern. If the DL→URN translator can't
      // canonicalise the value (unknown AI, wildcard, invalid check digit,
      // non-GS1 URI), fall back to matching the raw value literally against
      // the indexed form. Returning empty is acceptable; 400 is not.
      log.warn(String.format("epc %s could not be canonicalised to URN (%s) — matching literally", epc, e.getClass().getSimpleName()));
      return epc;
    }
    return map.get("asURN");
  }

  private List<String> convertClassLevelEPCAsPerGs1Extension(List<String> value) {
    try {
      return value.stream().map(epc -> (gs1Extensions.get().containsKey(epc.split(":")[0]) || epc.startsWith("urn:") || epc.startsWith("gs1:")) ? epc : convertToURNForClassLevelEPC(epc)).toList();
    } catch (UrnDLTransformationException exception) {
      throw new QueryValidationException("Custom value could not be resolved. GS1-Extensions contains invalid prefix", exception);
    }
  }

  private List<String> convertClassLevelEPCToURN(List<String> value) {
    return value.stream().map(epc -> {
      if (epc.startsWith("urn:") || epc.startsWith("gs1:")) {
        return epc;
      } else if (epc.startsWith("http")) {
        return convertToURNForClassLevelEPC(epc);
      } else {
        throw new QueryValidationException("Custom value could not be resolved. GS1-Extensions is missing");
      }
    }).toList();
  }

  private String convertToURNForClassLevelEPC(String epc) {
    final Map<String, String> map;
    try {
      map = ConverterUtil.toURNForClassLevelIdentifier(epc);
    } catch (ValidationException e) {
      // Same rationale as convertEpcToURN — fall back to literal match when
      // the value can't be canonicalised (wildcard, unknown AI, ...).
      log.warn(String.format("epc %s could not be canonicalised to URN class-level (%s) — matching literally", epc, e.getClass().getSimpleName()));
      return epc;
    }
    return map.get("asURN");
  }

  /**
   * A CBV value for a filter, in whichever of the three spellings the caller used.
   *
   * <p>EPCIS allows a standard vocabulary element as a URN, as a Web URI, or as
   * a bare string — {@code shipping} is as legal as
   * {@code https://ref.gs1.org/cbv/BizStep-shipping} — and the repository stores
   * all three forms next to each other ({@code asURN}, {@code asURI},
   * {@code asCaptured}) precisely so that any of them can be asked for.
   *
   * <p>The converter, however, only understands the Web-URI form. Most of these
   * filters never notice, because their {@link QueryParamName} carries a URN
   * prefix and a bare value is prefixed before it gets here —
   * {@code EQ_bizStep=shipping} becomes
   * {@code urn:epcglobal:cbv:bizstep:shipping} on the way. {@code EQ_errorReason}
   * carries no prefix, so a bare value reached the converter, failed, and came
   * back to the caller as a <b>500</b>. Measured against a running repository on
   * 2026-08-29: {@code EQ_errorReason=did_not_occur} answered "a server-side
   * error occurred", while the same query in the URN spelling answered 200.
   *
   * <p>So a value the converter cannot place is now left exactly as it came. It
   * is not an error: it matches the {@code asCaptured} key, which is where a
   * bare value is stored. Only the Web-URI form is translated, because that is
   * the one form the index does not keep verbatim.
   */
  private List<String> convertToURNForStandardVocab(List<String> value) {
    return value.stream().map(epc -> (epc.startsWith("urn:") || epc.startsWith("gs1:")) ? epc : convertStandardVocabToURN(epc)).toList();
  }

  private String convertStandardVocabToURN(String epc) {
    try {
      return StandardVocabConvertorUtil.toURN(epc);
    } catch (ValidationException e) {
      // Not a Web URI of a standard vocabulary element — a bare CBV value, or
      // something this build does not know. Either way the caller's own
      // spelling is the better guess than a failed request: an unknown value
      // simply matches nothing, which is an answer.
      log.debug("Standard vocabulary value '{}' left as it came: {}", epc, e.getMessage());
      return epc;
    }
  }

  private List<String> convertToURN(List<String> value) {
    return value.stream().map(epc -> {
      if (epc.startsWith("urn:")) {
        return epc;
      } else if (epc.startsWith("http")) {
        return convertEpcToURN(epc);
      } else {
        throw new QueryValidationException("Custom value could not be resolved. GS1-Extensions is missing");
      }
    }).toList();
  }

  /**
   * algorithm to extract path
   *
   * <p>assume paramKey = EQ_example:myField namespaceAlias = example fieldName = myField
   *
   * @param paramKey
   * @return
   */
  private List<String> getFullyQualifiedPathOfCUIRE(String paramName, String paramKey) {
    final String namespaceAlias = paramKey.substring(paramName.lastIndexOf("_") + 1, paramKey.indexOf(":"));
    if (!EPCIS.EPCIS_DEFAULT_NAMESPACES.keySet().contains(namespaceAlias) && gs1Extensions.isEmpty() && ((null == context) || List.class.isAssignableFrom(context.getClass()) && !((List<Map<?, ?>>) context).stream().anyMatch(m -> m.containsKey(namespaceAlias)) || Map.class.isAssignableFrom(context.getClass()) && !((Map) context).containsKey(namespaceAlias))) {
      log.info(ExceptionMessages.NAMESPACE_MISSING_FOR_USER_EXTENSIONS);
      throw new QueryParameterException(ExceptionMessages.NAMESPACE_MISSING_FOR_USER_EXTENSIONS);
    }
    if (gs1Extensions.isPresent() && !gs1Extensions.get().containsKey(namespaceAlias)) {
      gs1Extensions.get().put(namespaceAlias, EPCIS.EPCIS_DEFAULT_NAMESPACES.get(namespaceAlias));
    }
    final Map<String, Object> contextMap = context == null ? gs1Extensions.orElse(EPCIS.EPCIS_DEFAULT_NAMESPACES) : Map.class.isAssignableFrom(context.getClass()) ? (Map) context : List.class.isAssignableFrom(context.getClass()) ? ((List<Map<String, String>>) context).stream().flatMap(s -> s.entrySet().stream()).collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue())) : gs1Extensions.orElse(EPCIS.EPCIS_DEFAULT_NAMESPACES);
    if (!paramKey.contains("_INNER_")) {
      final String[] fields = paramKey.substring(paramName.lastIndexOf("_") + 1).split("\\.");
      final List<String> pathField = new ArrayList<String>();
      for (String field : fields) {
        final String fieldName = field.substring(field.indexOf(":") + 1);
        if (contextMap.get(namespaceAlias).toString().endsWith("/")) {
          pathField.add(contextMap.get(namespaceAlias).toString().concat(fieldName));
        } else {
          pathField.add(contextMap.get(namespaceAlias).toString().concat("/").concat(fieldName));
        }
      }
      if (pathField.size() >= 2) {
        return List.of(pathField.get(pathField.size() - 1), String.join(".", pathField));
      }
      return List.of(String.join(".", pathField));
    }
    final String fieldName = paramKey.substring(paramKey.indexOf(":") + 1);
    if (contextMap.get(namespaceAlias).toString().endsWith("/")) {
      return List.of(contextMap.get(namespaceAlias).toString().concat(fieldName));
    } else {
      return List.of(contextMap.get(namespaceAlias).toString().concat("/").concat(fieldName));
    }
  }

  static boolean isVoidExists(QueryParamName paramName) {
    return paramName.getOperator() == OperatorType.EXISTS && paramName.getValueType() != ValueType.PAIR;
  }

  public void validate(QueryParamName paramName, Object value) throws QueryParameterException {
    validateQueryParamName(paramName, value);
    validateActionValue(paramName, value);
    validateOrderByField(paramName, value);
  }

  private void validateOrderByField(QueryParamName paramName, Object value) {
    if (QueryParamName.orderBy.equals(paramName)) {
      try {
        EpcisEventValidSortableFields.valueOf(String.valueOf(value));
      } catch (ClassCastException e) {
        throw new QueryParameterException(String.format(ExceptionMessages.INVALID_VALUE, QueryParamName.orderBy, value));
      } catch (IllegalArgumentException e) {
        if (!String.valueOf(value).contains(":")) {
          throw new QueryParameterException(String.format(ExceptionMessages.INVALID_VALUE, QueryParamName.orderBy, value));
        }
      }
    }
  }

  private void validateQueryParamName(QueryParamName paramName, Object value) {
    if (isVoidExists(paramName)) {
      // EPCIS 2.0 8.2.7.1 types EXISTS_errorDeclaration and its kind as Void: the value is
      // ignored. The GS1 OpenAPI sends true or false. Both used to fail here as "not a number".
      return;
    }
    QueryParameterException queryexp = new QueryParameterException(String.format(ExceptionMessages.INVALID_VALUE_TYPE, paramName.toString()));
    try {
      if (!paramName.validate(value)) {
        throw queryexp;
      }
    } catch (ClassCastException e) {
      throw queryexp;
    } catch (ParseException e) {
      throw new QueryParameterException(String.format(ExceptionMessages.INVALID_VALUE, paramName, value));
    } catch (DateTimeException e) {
      // A timestamp the client cannot spell is the client's mistake, not ours.
      // OffsetDateTime.parse throws DateTimeParseException, which is unchecked and
      // therefore used to travel past every catch here into the generic mapper,
      // where it became a 500. It also has to name the parameter: the parse error
      // alone quotes the text and leaves the caller guessing which one it was.
      throw new QueryParameterException(String.format(ExceptionMessages.INVALID_VALUE, paramName, value));
    }
  }

  @SuppressWarnings("unchecked")
  private void validateActionValue(QueryParamName paramName, Object value) {
    if (QueryParamName.EQ_action.equals(paramName)) {
      try {
        if (value instanceof Collection) {
          ((Collection<String>) value).forEach(Action::valueOf);
        } else {
          Action.valueOf(value.toString());
        }
      } catch (ClassCastException | IllegalArgumentException e) {
        throw new QueryParameterException(ExceptionMessages.VALID_ACTION_VALUES);
      }
    }
  }

  private String appendURIPrefixToValue(String value, String uriPrefix) {
    String prefix = value.substring(0, value.lastIndexOf(":") + 1);
    String payload = value.substring(value.lastIndexOf(":") + 1);
    if (prefix.isBlank()) value = uriPrefix.concat(payload);
    return value;
  }
}
