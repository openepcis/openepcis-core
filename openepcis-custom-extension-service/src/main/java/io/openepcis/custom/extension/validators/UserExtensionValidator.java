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
package io.openepcis.custom.extension.validators;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.ValidationMessage;
import io.openepcis.capture.context.StorageService;
import io.openepcis.constants.EPCIS;
import io.openepcis.core.exception.MarshallingException;
import io.openepcis.custom.extension.cache.UserExtensionSchemaCache;
import io.openepcis.model.epcis.UserExtensionSchema;
import io.openepcis.model.epcis.constants.EpcisEventFieldConstants;
import io.openepcis.model.epcis.exception.ExceptionMessages;
import io.openepcis.repository.api.UserExtensionRepository;
import io.openepcis.service.util.Constants;
import io.openepcis.service.util.EventPredicates;
import io.openepcis.service.util.JsonSchemaLoader;
import io.openepcis.service.util.UserExtensionExtractor;
import jakarta.enterprise.context.ApplicationScoped;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;

@ApplicationScoped
public class UserExtensionValidator {
  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(UserExtensionValidator.class);
  private final UserExtensionRepository userExtensionRepository;
  private final StorageService storageService;
  private final UserExtensionSchemaCache schemaCache;
  private final ObjectMapper objectMapper;

  /**
   * Algorithm:
   *
   * <p>1. Check existence of user extensions in each event 2. If user extension is present 2a. get
   * the namespace URL of all top-level extensions present in an event 2b. get the UserSchema ES
   * entry by namespace URL 2c. If Schema doesn't exist then reject event 2d. If Schema exists then
   * pull our JSON schema from AWS or local cache 3. Perform validation for each top-level extension
   * element 4. Reject event if validation fails else move on
   */
  public Set<String> validateUserExtensions(ObjectNode individualEvent, Map<String, Object> context) {
    final Set<String> uxValidationErrors = new HashSet<>();
    validateUserExtensionsAtParentLevel(individualEvent, context, uxValidationErrors);
    validateUserExtensionsWithinReadPoint(individualEvent, context, uxValidationErrors);
    validateUserExtensionsWithinBizLocation(individualEvent, context, uxValidationErrors);
    validateUserExtensionsWithinErrorDeclaration(individualEvent, context, uxValidationErrors);
    validateUserExtensionsWithinSensorElement(individualEvent, context, uxValidationErrors);
    validateUserExtensionsWithinIlmd(individualEvent, context, uxValidationErrors);
    validateUserExtensionsWithinBizStep(individualEvent, context, uxValidationErrors);
    validateUserExtensionsWithinDisposition(individualEvent, context, uxValidationErrors);
    validateUserExtensionsInBizTransactionType(individualEvent, context, uxValidationErrors);
    validateUserExtensionsInSourceListType(individualEvent, context, uxValidationErrors);
    validateUserExtensionsInDestinationListType(individualEvent, context, uxValidationErrors);
    return uxValidationErrors;
  }

  private void validateUserExtensionsAtParentLevel(ObjectNode individualEvent, Map<String, Object> context, Set<String> uxValidationErrors) {
    validateAgainstSchema(individualEvent, context, uxValidationErrors);
  }

  private void validateUserExtensionsWithinReadPoint(ObjectNode individualEvent, Map<String, Object> context, Set<String> uxValidationErrors) {
    if (EventPredicates.HAS_READ_POINT.test(individualEvent)) {
      final ObjectNode readPoint = (ObjectNode) individualEvent.get(Constants.READ_POINT);
      validateAgainstSchema(readPoint, context, uxValidationErrors);
    }
  }

  private void validateUserExtensionsWithinBizLocation(ObjectNode individualEvent, Map<String, Object> context, Set<String> uxValidationErrors) {
    if (EventPredicates.HAS_BIZ_LOCATION.test(individualEvent)) {
      final ObjectNode bizLocation = (ObjectNode) individualEvent.get(Constants.BIZ_LOCATION);
      validateAgainstSchema(bizLocation, context, uxValidationErrors);
    }
  }

  private void validateUserExtensionsWithinBizStep(ObjectNode individualEvent, Map<String, Object> context, Set<String> uxValidationErrors) {
    if (EventPredicates.HAS_BIZ_STEP.test(individualEvent)) {
      final JsonNode cbvValue = individualEvent.get(Constants.BIZ_STEP);
      final String prefix = cbvValue.textValue().split(":")[0];
      if (context.containsKey(prefix)) {
        final ObjectNode obj = objectMapper.createObjectNode();
        obj.set(Constants.BIZ_STEP, cbvValue);
        validateCbvValueAgainstUserExtensionSchema(obj, prefix, context, uxValidationErrors);
      }
    }
  }

  private void validateUserExtensionsWithinDisposition(ObjectNode individualEvent, Map<String, Object> context, Set<String> uxValidationErrors) {
    if (EventPredicates.HAS_DISPOSITION.test(individualEvent)) {
      final JsonNode cbvValue = individualEvent.get(Constants.DISPOSITION);
      final String prefix = cbvValue.textValue().split(":")[0];
      if (context.containsKey(prefix)) {
        final ObjectNode obj = objectMapper.createObjectNode();
        obj.set(Constants.DISPOSITION, cbvValue);
        validateCbvValueAgainstUserExtensionSchema(obj, prefix, context, uxValidationErrors);
      }
    }
  }

  private void validateUserExtensionsInBizTransactionType(ObjectNode individualEvent, Map<String, Object> context, Set<String> uxValidationErrors) {
    if (EventPredicates.HAS_BIZ_TRANSACTION_LIST.test(individualEvent)) {
      final ArrayNode bizTransactionArray = (ArrayNode) individualEvent.get(Constants.BIZ_TRANSACTION_LIST);
      final Iterator<Map.Entry<String, JsonNode>> fieldsIterator = bizTransactionArray.fields();
      while (fieldsIterator.hasNext()) {
        final Map.Entry<String, JsonNode> entry = fieldsIterator.next();
        final JsonNode bizTransaction = entry.getValue();
        String bizTransactionType = bizTransaction.get(Constants.TYPE).textValue();
        String prefix = bizTransactionType.split(":")[0];
        if (context.containsKey(prefix)) {
          final ObjectNode obj = objectMapper.createObjectNode();
          obj.put(Constants.BIZ_TRANSACTION_LIST_TYPE, bizTransactionType);
          validateCbvValueAgainstUserExtensionSchema(obj, prefix, context, uxValidationErrors);
        }
      }
    }
  }

  private void validateUserExtensionsInSourceListType(ObjectNode individualEvent, Map<String, Object> context, Set<String> uxValidationErrors) {
    if (EventPredicates.HAS_SOURCE_LIST.test(individualEvent)) {
      final ArrayNode sourceArray = (ArrayNode) individualEvent.get(Constants.SOURCE_LIST);
      final Iterator<Map.Entry<String, JsonNode>> fieldsIterator = sourceArray.fields();
      while (fieldsIterator.hasNext()) {
        final Map.Entry<String, JsonNode> entry = fieldsIterator.next();
        final JsonNode sourceListItem = entry.getValue();
        String sourceListType = sourceListItem.get(Constants.TYPE).textValue();
        String prefix = sourceListType.split(":")[0];
        if (context.containsKey(prefix)) {
          final ObjectNode obj = objectMapper.createObjectNode();
          obj.put(Constants.SOURCE_DEST_TYPE, sourceListType);
          validateCbvValueAgainstUserExtensionSchema(obj, prefix, context, uxValidationErrors);
        }
      }
    }
  }

  private void validateUserExtensionsInDestinationListType(ObjectNode individualEvent, Map<String, Object> context, Set<String> uxValidationErrors) {
    if (EventPredicates.HAS_DESTINATION_LIST.test(individualEvent)) {
      final ArrayNode destinationArray = (ArrayNode) individualEvent.get(Constants.DESTINATION_LIST);
      final Iterator<Map.Entry<String, JsonNode>> fieldsIterator = destinationArray.fields();
      while (fieldsIterator.hasNext()) {
        final Map.Entry<String, JsonNode> entry = fieldsIterator.next();
        final JsonNode destinationListItem = entry.getValue();
        String destinationListType = destinationListItem.get(Constants.TYPE).textValue();
        String prefix = destinationListType.split(":")[0];
        if (context.containsKey(prefix)) {
          final ObjectNode obj = objectMapper.createObjectNode();
          obj.put(Constants.SOURCE_DEST_TYPE, destinationListType);
          validateCbvValueAgainstUserExtensionSchema(obj, prefix, context, uxValidationErrors);
        }
      }
    }
  }

  private void validateUserExtensionsWithinErrorDeclaration(ObjectNode individualEvent, Map<String, Object> context, Set<String> uxValidationErrors) {
    if (EventPredicates.IS_ERROR_DECLARATION.test(individualEvent)) {
      final ObjectNode errorDeclaration = (ObjectNode) individualEvent.get(Constants.ERROR_DECLARATION);
      // reason is optional (EPCIS 2.0 7.4.1.2); a declaration without one used to throw here.
      final String errorDeclarationReason = errorDeclaration.path(Constants.REASON).asText("");
      final String prefix = errorDeclarationReason.split(":")[0];
      if (!errorDeclarationReason.isEmpty() && context.containsKey(prefix)) {
        final ObjectNode obj = objectMapper.createObjectNode();
        obj.put(Constants.ERROR_DECLARATION_REASON, errorDeclarationReason);
        validateCbvValueAgainstUserExtensionSchema(obj, prefix, context, uxValidationErrors);
      }
      validateAgainstSchema(errorDeclaration, context, uxValidationErrors);
    }
  }

  private void validateUserExtensionsWithinSensorElement(ObjectNode individualEvent, Map<String, Object> context, Set<String> uxValidationErrors) {
    if (EventPredicates.IS_SENSOR_EVENT.test(individualEvent)) {
      final ArrayNode sensorElementArray = (ArrayNode) individualEvent.get(EpcisEventFieldConstants.SENSOR_ELEMENT_LIST);
      for (int i = 0; i < sensorElementArray.size(); i++) {
        final ObjectNode sensorElement = (ObjectNode) sensorElementArray.get(i);
        validateAgainstSchema(sensorElement, context, uxValidationErrors);
        if (EventPredicates.HAS_SENSOR_METADATA.test(sensorElement)) {
          final ObjectNode sensorMetadata = (ObjectNode) sensorElement.get(EpcisEventFieldConstants.SENSOR_METADATA);
          validateAgainstSchema(sensorMetadata, context, uxValidationErrors);
        }
        final ArrayNode sensorReportArray = (ArrayNode) sensorElement.get(EpcisEventFieldConstants.SENSOR_REPORT);
        final Iterator<Map.Entry<String, JsonNode>> fieldsIterator = sensorReportArray.fields();
        while (fieldsIterator.hasNext()) {
          final Map.Entry<String, JsonNode> entry = fieldsIterator.next();
          final JsonNode sensorReport = entry.getValue();
          String sensorReportType = sensorReport.get(Constants.TYPE).textValue();
          String prefix = sensorReportType.split(":")[0];
          if (context.containsKey(prefix)) {
            final ObjectNode obj = objectMapper.createObjectNode();
            obj.put(Constants.SENSOR_REPORT_TYPE, sensorReportType);
            validateCbvValueAgainstUserExtensionSchema(obj, prefix, context, uxValidationErrors);
          }
          validateAgainstSchema((ObjectNode) sensorReport, context, uxValidationErrors);
        }
      }
    }
  }

  private void validateCbvValueAgainstUserExtensionSchema(ObjectNode objectWithUserExtension, String prefix, Map<String, Object> context, Set<String> uxValidationErrors) {
    final JsonSchema schema = getSchema(context, prefix, uxValidationErrors);
    if (schema != null) {
      String jsonAsStr;
      jsonAsStr = objectWithUserExtension.toString();
      final String userExtensionWithAliasRemoved = jsonAsStr.replaceAll(prefix + ":", "");
      try {
        final Set<ValidationMessage> errors = schema.validate(objectMapper.readTree(userExtensionWithAliasRemoved));
        if (!errors.isEmpty()) {
          final String messages = errors.stream().map(ValidationMessage::getMessage).collect(Collectors.joining(", "));
          uxValidationErrors.add(String.format(ExceptionMessages.USER_EXTENSION_CORRESPONDING_TO_NAMESPACE_DOES_NOT_COMPLY_WITH_SCHEMA, context.get(prefix), messages));
        }
      } catch (JsonMappingException e) {
        throw new RuntimeException(e);
      } catch (JsonProcessingException e) {
        throw new RuntimeException(e);
      }
    }
  }

  private void validateAgainstSchema(ObjectNode objectWithUserExtension, Map<String, Object> context, Set<String> uxValidationErrors) {
    Map<String, Object> userExtensions = UserExtensionExtractor.getUserExtensions(objectWithUserExtension);
    final Map<String, Map<String, Object>> namespaceAliasVsUserExtensionElements = groupUserExtensionByNamespaceAlias(userExtensions);
    namespaceAliasVsUserExtensionElements.forEach((namespaceAlias, userExtensionElements) -> {
      if (context.containsKey(namespaceAlias)) {
        final JsonSchema schema = getSchema(context, namespaceAlias, uxValidationErrors);
        if (schema != null) {
          String jsonAsStr;
          try {
            jsonAsStr = objectMapper.writeValueAsString(userExtensionElements);
          } catch (JsonProcessingException e) {
            throw new MarshallingException(e.getOriginalMessage());
          }
          // todo : find an elegant way to repalce all alias prefix
          final String userExtensionWithAliasRemoved = jsonAsStr.replaceAll(namespaceAlias + ":", "");
          try {
            final Set<ValidationMessage> errors = schema.validate(objectMapper.readTree(userExtensionWithAliasRemoved));
            if (!errors.isEmpty()) {
              final String messages = errors.stream().map(ValidationMessage::getMessage).collect(Collectors.joining(", "));
              uxValidationErrors.add(String.format(ExceptionMessages.USER_EXTENSION_CORRESPONDING_TO_NAMESPACE_DOES_NOT_COMPLY_WITH_SCHEMA, context.get(namespaceAlias), messages));
            }
          } catch (JsonMappingException e) {
            throw new RuntimeException(e);
          } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
          }
        }
      }
    });
  }

  private JsonSchema getSchema(Map<String, Object> context, String namespaceAlias, Set<String> uxValidationErrors) {
    JsonSchema schema = null;
    final String namespace = context.get(namespaceAlias).toString();
    if (schemaCache.hasSchema(namespace)) {
      schema = schemaCache.getSchema(namespace);
    } else if (EPCIS.EPCIS_DEFAULT_NAMESPACES.containsValue(namespace)) {
      // If schema not present and the namespace belongs to EPCIS default namespaces, return
      return schema;
    } else {
      final Optional<UserExtensionSchema> userExtensionSchema = userExtensionRepository.getSchemaByNamespace(namespace);
      if (userExtensionSchema.isEmpty() || (StringUtils.isBlank(userExtensionSchema.get().getJsonSchemaS3Key()) && StringUtils.isBlank(userExtensionSchema.get().getJsonSchemaUrl()))) {
        uxValidationErrors.add("No JSON schema found for namespace " + namespace);
      } else {
        try {
          schema = StringUtils.isBlank(userExtensionSchema.get().getJsonSchemaS3Key()) ? JsonSchemaLoader.loadSchema(new URL(userExtensionSchema.get().getJsonSchemaUrl())) : JsonSchemaLoader.loadSchema(storageService.get(userExtensionSchema.get().getJsonSchemaS3Key()));
          schemaCache.updateSchema(namespace, schema);
        } catch (MalformedURLException e) {
          log.error(e.getMessage(), e);
        }
      }
    }
    return schema;
  }

  private Map<String, Map<String, Object>> groupUserExtensionByNamespaceAlias(Map<String, Object> userExtensions) {
    final Map<String, Map<String, Object>> namespaceAliasVsUserExtensionElements = new HashMap<>();
    userExtensions.forEach((k, v) -> {
      final String namespaceAlias = k.substring(0, k.indexOf(":"));
      final Map<String, Object> userExtensionElements;
      if (namespaceAliasVsUserExtensionElements.containsKey(namespaceAlias)) {
        userExtensionElements = namespaceAliasVsUserExtensionElements.get(namespaceAlias);
      } else {
        userExtensionElements = new HashMap<>();
      }
      userExtensionElements.put(k, v);
      namespaceAliasVsUserExtensionElements.put(namespaceAlias, userExtensionElements);
    });
    return namespaceAliasVsUserExtensionElements;
  }

  private void validateUserExtensionsWithinIlmd(ObjectNode individualEvent, Map<String, Object> context, Set<String> uxValidationErrors) {
    if (EventPredicates.HAS_ILMD.test(individualEvent)) {
      final ObjectNode ilmd = (ObjectNode) individualEvent.get(Constants.ILMD);
      validateAgainstSchema(ilmd, context, uxValidationErrors);
    }
  }

  public UserExtensionValidator(UserExtensionRepository userExtensionRepository, StorageService storageService, UserExtensionSchemaCache schemaCache, ObjectMapper objectMapper) {
    this.userExtensionRepository = userExtensionRepository;
    this.storageService = storageService;
    this.schemaCache = schemaCache;
    this.objectMapper = objectMapper;
  }
}
