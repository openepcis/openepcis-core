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
package io.openepcis.validation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import io.openepcis.model.epcis.exception.CaptureValidationException;
import io.openepcis.model.epcis.exception.QueryValidationException;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.InputStream;
import java.util.Set;

@ApplicationScoped
public class JsonSchemaValidator {
  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(JsonSchemaValidator.class);
  private final ObjectMapper objectMapper = new ObjectMapper();
  private JsonSchema captureJsonSchema;
  private JsonSchema queryJsonSchema;

  @PostConstruct
  public void postConstruct() {
    captureJsonSchema = loadSchema("/modular-EPCIS-JSON-schema.json");
    queryJsonSchema = loadSchema("/query-schema.json");
  }

  private JsonSchema loadSchema(final String path) {
    final JsonSchemaFactory validatorFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
    final InputStream inputStream = JsonSchemaValidator.class.getResourceAsStream(path);
    return validatorFactory.getSchema(inputStream);
  }

  public void validateAgainstCaptureJSONSchema(final String document) {
    try {
      final Set<ValidationMessage> captureErrors = captureJsonSchema.validate(objectMapper.readTree(document));
      if (!captureErrors.isEmpty()) {
        throw new CaptureValidationException(ValidationMessageFormatter.format(captureErrors));
      }
    } catch (Exception exception) {
      throw new CaptureValidationException("Exception occurred during validation against EPCIS JSON Capture schema : " + exception.getMessage(), exception);
    }
  }

  public void validateAgainstQueryJSONSchema(final String document) {
    try {
      final Set<ValidationMessage> errors = queryJsonSchema.validate(objectMapper.readTree(document));
      if (!errors.isEmpty()) {
        throw new QueryValidationException(ValidationMessageFormatter.format(errors));
      }
    } catch (Exception exception) {
      throw new CaptureValidationException("Exception occurred during validation against EPCIS JSON Query schema : " + exception.getMessage(), exception);
    }
  }

  public JsonSchemaValidator() {
  }
}
