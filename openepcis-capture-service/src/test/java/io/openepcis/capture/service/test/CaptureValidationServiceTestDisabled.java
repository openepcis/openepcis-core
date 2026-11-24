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

import static java.util.List.of;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openepcis.capture.service.EPCISEventValidationService;
import io.openepcis.capture.service.test.config.ServiceTestConfig;
import io.openepcis.custom.extension.validators.UserExtensionValidator;
import io.openepcis.model.dto.InvalidEPCISEventInfo;
import io.openepcis.model.epcis.exception.CaptureValidationException;
import io.openepcis.model.epcis.exception.ExceptionMessages;
import io.openepcis.reactive.publisher.ObjectNodePublisher;
import io.openepcis.repository.api.QueryRepository;
import io.openepcis.validation.service.JsonSchemaValidator;
import io.smallrye.mutiny.Multi;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.function.Executable;
import org.mockito.Mockito;

class CaptureValidationServiceTestDisabled {
  private static final ObjectMapper objectMapper = ServiceTestConfig.objectMapper();
  private static final JsonSchemaValidator jsonSchemaValidator = new JsonSchemaValidator();
  private static final QueryRepository queryRepo = Mockito.mock(QueryRepository.class);
  private static final UserExtensionValidator userExtensionValidator =
      Mockito.mock(UserExtensionValidator.class);
  private static final EPCISEventValidationService epcisEventValidationService =
      Mockito.mock(EPCISEventValidationService.class);
  private static final String JSONLDNAMESPACE_VALIDATION = "JSONLDNamespaceValidation";
  private static final String JSONSCHEMA_CONSTRAINT_VALIDATIONS = "JSONSchemaConstraintValidations";

  @BeforeAll
  public static void beforeAll() {
    jsonSchemaValidator.postConstruct();
  }

  @TestFactory
  Collection<DynamicTest> testJSONSchemaConstraintValidations() {
    final Map<String, List<String>> validations = new HashMap<>();

    validations.put(
        JSONSCHEMA_CONSTRAINT_VALIDATIONS + "/ObjectEvent-isA-field-wrong-value.json",
        of("epcisBody/eventList/0/isA", "is not a valid enum value"));
    validations.put(
        JSONSCHEMA_CONSTRAINT_VALIDATIONS + "/ObjectEvent-action-field-missing.jsonld",
        of("required key [action] not found"));
    validations.put(
        JSONSCHEMA_CONSTRAINT_VALIDATIONS + "/ObjectEvent-eventTime-field-missing.json",
        of("required key [eventTime] not found"));
    validations.put(
        JSONSCHEMA_CONSTRAINT_VALIDATIONS + "/ObjectEvent-eventTimeOffSet-field-missing.json",
        of("required key [eventTimeZoneOffset] not found"));
    validations.put(
        JSONSCHEMA_CONSTRAINT_VALIDATIONS + "/ObjectEvent-eventID-wrong-type.json",
        of("epcisBody/eventList/0/eventID", " is not a valid URI"));
    validations.put(
        JSONSCHEMA_CONSTRAINT_VALIDATIONS + "/ObjectEvent-action-field-wrong-value.json",
        of("epcisBody/eventList/0/action", "is not a valid enum value"));
    validations.put(
        JSONSCHEMA_CONSTRAINT_VALIDATIONS + "/ObjectEvent-eventTime-field-wrong-value.json",
        of("epcisBody/eventList/0/eventTime", "is not a valid date-time"));
    validations.put(
        JSONSCHEMA_CONSTRAINT_VALIDATIONS + "/ObjectEvent-eventTimeOffSet-field-wrong-value.json",
        of("epcisBody/eventList/0/eventTimeZoneOffset", "does not match pattern"));
    validations.put(
        JSONSCHEMA_CONSTRAINT_VALIDATIONS + "/ObjectEvent-recordTime-field-wrong-value.json",
        of("epcisBody/eventList/0/recordTime", "is not a valid date-time"));
    validations.put(
        JSONSCHEMA_CONSTRAINT_VALIDATIONS + "/ObjectEvent-epcList-field-wrong-value.json",
        of("epcisBody/eventList/0/epcList", "is not a valid URI"));
    validations.put(
        JSONSCHEMA_CONSTRAINT_VALIDATIONS + "/ObjectEvent-bizStep-field-wrong-value.json",
        of("epcisBody/eventList/0/bizStep", "is not a valid URI"));
    validations.put(
        JSONSCHEMA_CONSTRAINT_VALIDATIONS
            + "/ObjectEvent-persistentDisposition-field-wrong-value.json",
        of(
            "epcisBody/eventList/0/persistentDisposition/set/0",
            "is not a valid URI",
            "epcisBody/eventList/0/persistentDisposition/unset/0",
            "is not a valid URI"));
    validations.put(
        JSONSCHEMA_CONSTRAINT_VALIDATIONS + "/ObjectEvent-disposition-field-wrong-value.json",
        of("epcisBody/eventList/0/disposition", "is not a valid URI"));
    // TODO: fix below test cases
    //        validations.put(JSONSCHEMA_CONSTRAINT_VALIDATIONS +
    // "/ObjectEvent-readPoint-field-wrong-value.json",
    //                of("epcisBody/eventList/0/readPoint", "is not a valid URI"));
    //        validations.put(JSONSCHEMA_CONSTRAINT_VALIDATIONS +
    // "/ObjectEvent-bizLocation-field-wrong-value.json",
    //                of("epcisBody/eventList/0/bizLocation", "is not a valid URI"));
    validations.put(
        JSONSCHEMA_CONSTRAINT_VALIDATIONS + "/ObjectEvent-bizTransaction-field-validations.json",
        of(
            "epcisBody/eventList/0/bizTransactionList/0",
            "required key [bizTransaction] not found",
            "epcisBody/eventList/0/bizTransactionList/1/bizTransaction",
            "is not a valid URI",
            "epcisBody/eventList/0/bizTransactionList/1/type",
            "is not a valid URI"));
    validations.put(
        JSONSCHEMA_CONSTRAINT_VALIDATIONS + "/ObjectEvent-errorDeclaration-field-validations.json",
        of(
            "epcisBody/eventList/0/errorDeclaration",
            "required key [reason] not found",
            "epcisBody/eventList/0/errorDeclaration/declarationTime",
            "is not a valid date-time",
            "epcisBody/eventList/0/errorDeclaration/correctiveEventIDs",
            "expected type: JSONArray, found: String"));
    validations.put(
        JSONSCHEMA_CONSTRAINT_VALIDATIONS + "/ObjectEvent-quantityList-field-validations.json",
        of(
            "epcisBody/eventList/0/quantityList/1",
            "required key [epcClass] not found",
            "epcisBody/eventList/0/quantityList/2",
            "required key [quantity] not found",
            "epcisBody/eventList/0/quantityList/0/uom",
            "does not match pattern",
            "epcisBody/eventList/0/quantityList/0/epcClass",
            "is not a valid URI",
            "epcisBody/eventList/0/quantityList/0/quantity",
            "expected type: Number, found: String"));
    validations.put(
        JSONSCHEMA_CONSTRAINT_VALIDATIONS + "/ObjectEvent-sourceList-field-validations.json",
        of(
            "epcisBody/eventList/0/sourceList/0",
            "required key [source] not found",
            "epcisBody/eventList/0/sourceList/1",
            "required key [type] not found",
            "epcisBody/eventList/0/sourceList/2/source",
            "is not a valid URI",
            "epcisBody/eventList/0/sourceList/2/type",
            "is not a valid URI"));
    validations.put(
        JSONSCHEMA_CONSTRAINT_VALIDATIONS + "/ObjectEvent-destinationList-field-validations.json",
        of(
            "epcisBody/eventList/0/destinationList/0",
            "required key [destination] not found",
            "epcisBody/eventList/0/destinationList/1",
            "required key [type] not found",
            "epcisBody/eventList/0/destinationList/2/destination",
            "is not a valid URI",
            "epcisBody/eventList/0/destinationList/2/type",
            "is not a valid URI"));
    validations.put(
        JSONSCHEMA_CONSTRAINT_VALIDATIONS
            + "/ObjectEvent-sensorElementList-field-validations1.json",
        of(
            "epcisBody/eventList/0/sensorElementList/2: required key [sensorReport] not found",
            "epcisBody/eventList/0/sensorElementList/0/sensorMetaData/startTime: [asdasd] is not a valid date-time",
            "epcisBody/eventList/0/sensorElementList/0/sensorMetaData/time: [asdasdasd] is not a valid date-time",
            "epcisBody/eventList/0/sensorElementList/0/sensorMetaData/endTime: [asdasd] is not a valid date-time",
            "epcisBody/eventList/0/sensorElementList/1/sensorMetaData/dataProcessingMethod: [http://xyz.com#frag1#frag2] is not a valid"
                + " URI",
            "epcisBody/eventList/0/sensorElementList/1/sensorMetaData/deviceMetaData: [http://xyz.com#frag1#frag2] is not a valid URI",
            "epcisBody/eventList/0/sensorElementList/1/sensorMetaData/rawData: [http://xyz.com#frag1#frag2] is not a valid URI",
            "epcisBody/eventList/0/sensorElementList/1/sensorMetaData/deviceID: [http://xyz.com#frag1#frag2] is not a valid URI",
            "epcisBody/eventList/0/sensorElementList/1/sensorMetaData/bizRules: [http://xyz.com#frag1#frag2] is not a valid URI"));
    validations.put(
        JSONSCHEMA_CONSTRAINT_VALIDATIONS + "/AggregationEvent-parentID-field-wrong-value.json",
        of("epcisBody/eventList/0/parentID", "is not a valid URI"));
    validations.put(
        JSONSCHEMA_CONSTRAINT_VALIDATIONS + "/AssociationEvent-parentID-field-missing.json",
        of("epcisBody/eventList/0: required key [parentID] not found"));
    validations.put(
        JSONSCHEMA_CONSTRAINT_VALIDATIONS + "/AggregationEvent-childEPCs-field-wrong-value.json",
        of("epcisBody/eventList/0/childEPCs/1", "is not a valid URI"));
    validations.put(
        JSONSCHEMA_CONSTRAINT_VALIDATIONS
            + "/AggregationEvent-childQuantityList-field-wrong-value.json",
        of(
            "epcisBody/eventList/0/childQuantityList/1",
            "required key [epcClass] not found",
            "epcisBody/eventList/0/childQuantityList/2",
            "required key [quantity] not found",
            "epcisBody/eventList/0/childQuantityList/0/uom",
            "does not match pattern",
            "epcisBody/eventList/0/childQuantityList/0/epcClass",
            "is not a valid URI",
            "epcisBody/eventList/0/childQuantityList/0/quantity",
            "expected type: Number, found: String"));
    validations.put(
        JSONSCHEMA_CONSTRAINT_VALIDATIONS
            + "/TransactionEvent-bizTransactionList-field-missing.json",
        of("epcisBody/eventList/0: required key [bizTransactionList] not found"));
    validations.put(
        JSONSCHEMA_CONSTRAINT_VALIDATIONS
            + "/TransformationEvent-inputEPCList-field-wrong-value.json",
        of("epcisBody/eventList/0/inputEPCList/2", "is not a valid URI"));
    validations.put(
        JSONSCHEMA_CONSTRAINT_VALIDATIONS
            + "/TransformationEvent-outputEPCList-field-wrong-value.json",
        of("epcisBody/eventList/0/outputEPCList/0", "is not a valid URI"));
    validations.put(
        JSONSCHEMA_CONSTRAINT_VALIDATIONS
            + "/TransformationEvent-inputQuantityList-field-wrong-value.json",
        of(
            "epcisBody/eventList/0/inputQuantityList/1",
            "required key [epcClass] not found",
            "epcisBody/eventList/0/inputQuantityList/2",
            "required key [quantity] not found",
            "epcisBody/eventList/0/inputQuantityList/0/uom",
            "does not match pattern",
            "epcisBody/eventList/0/inputQuantityList/0/epcClass",
            "is not a valid URI",
            "epcisBody/eventList/0/inputQuantityList/0/quantity",
            "expected type: Number, found: String"));
    validations.put(
        JSONSCHEMA_CONSTRAINT_VALIDATIONS
            + "/TransformationEvent-outputQuantityList-field-wrong-value.json",
        of(
            "epcisBody/eventList/0/outputQuantityList/1",
            "required key [epcClass] not found",
            "epcisBody/eventList/0/outputQuantityList/2",
            "required key [quantity] not found",
            "epcisBody/eventList/0/outputQuantityList/0/uom",
            "does not match pattern",
            "epcisBody/eventList/0/outputQuantityList/0/epcClass",
            "is not a valid URI",
            "epcisBody/eventList/0/outputQuantityList/0/quantity",
            "expected type: Number, found: String"));
    validations.put(
        JSONSCHEMA_CONSTRAINT_VALIDATIONS
            + "/TransformationEvent-transformationID-field-wrong-value.json",
        of("epcisBody/eventList/0/transformationID", "is not a valid URI"));

    return validations.entrySet().stream()
        .map(
            entry -> {
              Executable exec =
                  () -> validateAgainstCaptureJSONSchema(entry.getKey(), entry.getValue());
              return DynamicTest.dynamicTest(entry.getKey(), exec);
            })
        .collect(Collectors.toList());
  }

  @TestFactory
  Collection<DynamicTest> testJSONLDNameSpaceValidation() {
    final Map<String, String> validations = new HashMap<>();

    // Only one event
    validations.put(
        JSONLDNAMESPACE_VALIDATION + "/ObjectEvent-valid-namespaces-mising.json",
        ExceptionMessages.CONTEXT_INFORMATION_IS_MISSING_FOR_NAME_SPACE_ALIAS
            + "[example2, example]");
    validations.put(
        JSONLDNAMESPACE_VALIDATION + "/ObjectEvent-valid-namespaces-mising-for-one-field.json",
        ExceptionMessages.CONTEXT_INFORMATION_IS_MISSING_FOR_NAME_SPACE_ALIAS + "[example2]");
    validations.put(
        JSONLDNAMESPACE_VALIDATION
            + "/ObjectEvent-valid-namespaces-mising-for-one-field-out-of-three.json",
        ExceptionMessages.CONTEXT_INFORMATION_IS_MISSING_FOR_NAME_SPACE_ALIAS + "[example3]");
    validations.put(
        JSONLDNAMESPACE_VALIDATION
            + "/ObjectEvent-valid-namespaces-mising-for-two-field-out-of-three.json",
        ExceptionMessages.CONTEXT_INFORMATION_IS_MISSING_FOR_NAME_SPACE_ALIAS
            + "[example3, example2]");

    // Multiple events in same EPCIS Docuemnt
    validations.put(
        JSONLDNAMESPACE_VALIDATION + "/ObjectEvent-AggregationEvent-namespace-missing.json",
        ExceptionMessages.CONTEXT_INFORMATION_IS_MISSING_FOR_NAME_SPACE_ALIAS
            + "[example, example2]");
    validations.put(
        JSONLDNAMESPACE_VALIDATION
            + "/ObjectEvent-AggregationEvent-namespace-missing-for-ObjectEvent-field.json",
        ExceptionMessages.CONTEXT_INFORMATION_IS_MISSING_FOR_NAME_SPACE_ALIAS + "[example2]");
    validations.put(
        JSONLDNAMESPACE_VALIDATION
            + "/ObjectEvent-AggregationEvent-namespace-missing-for-AggregationEvent-field.json",
        ExceptionMessages.CONTEXT_INFORMATION_IS_MISSING_FOR_NAME_SPACE_ALIAS + "[example]");
    validations.put(
        JSONLDNAMESPACE_VALIDATION
            + "/ObjectEvent-AggregationEvent-namespace-missing-for-multiple-fields-in-ObjectEvent.json",
        ExceptionMessages.CONTEXT_INFORMATION_IS_MISSING_FOR_NAME_SPACE_ALIAS
            + "[example, example3]");
    validations.put(
        JSONLDNAMESPACE_VALIDATION + "/Multiple-Events-missing-in-all-events.json",
        ExceptionMessages.CONTEXT_INFORMATION_IS_MISSING_FOR_NAME_SPACE_ALIAS
            + "[example, example2, example4, example5, example3]");
    validations.put(
        JSONLDNAMESPACE_VALIDATION + "/Multiple-Events-missing-in-few-events.json",
        ExceptionMessages.CONTEXT_INFORMATION_IS_MISSING_FOR_NAME_SPACE_ALIAS
            + "[example2, example4]");

    return validations.entrySet().stream()
        .map(
            entry -> {
              Executable exec = () -> validateJSONLDNameSpace(entry.getKey(), entry.getValue());
              return DynamicTest.dynamicTest(entry.getKey(), exec);
            })
        .collect(Collectors.toList());
  }

  private void validateJSONLDNameSpace(final String resourceName, final String expectedMessage)
      throws Exception {
    final String epcisDocument = getFileContent(resourceName);
    final ObjectNodePublisher<ObjectNode> publisher =
        new ObjectNodePublisher(getClass().getResourceAsStream(resourceName), () -> getClass().getResourceAsStream(resourceName));
    final List<InvalidEPCISEventInfo> invalidInfo = new ArrayList<>();
    epcisEventValidationService.validateEventAgainstCustomValidations(
        Multi.createFrom().publisher(publisher).subscribe().asStream().toList().get(0),
        invalidInfo,
        new String[] {"sha-256"},
        UUID.randomUUID().toString(),
        new HashMap<>(),
        1,
        new HashMap<>());
    assertTrue(invalidInfo.toString().contains(expectedMessage));
  }

  private void validateAgainstCaptureJSONSchema(
      final String resourceName, final List<String> expectedMessages) {
    final String epcisDocument = getFileContent(resourceName);
    final CaptureValidationException exception =
        assertThrows(
            CaptureValidationException.class,
            () -> jsonSchemaValidator.validateAgainstCaptureJSONSchema(epcisDocument));
    expectedMessages.forEach(
        expectedMessage -> assertTrue(exception.getMessage().contains(expectedMessage)));
  }

  private String getFileContent(final String resourceName) {
    final ClassLoader classLoader = getClass().getClassLoader();
    final File file =
        new File(Objects.requireNonNull(classLoader.getResource(resourceName)).getFile());
    try {
      return FileUtils.readFileToString(file, Charset.defaultCharset());
    } catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }
}
