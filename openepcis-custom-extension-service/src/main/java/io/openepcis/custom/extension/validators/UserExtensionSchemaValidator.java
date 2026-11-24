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

import com.networknt.schema.JsonSchema;
import io.openepcis.core.exception.ImplementationException;
import io.openepcis.core.exception.SchemaValidationException;
import io.openepcis.model.epcis.UserExtensionSchema;
import io.openepcis.repository.api.UserExtensionRepository;
import io.openepcis.service.util.JsonSchemaLoader;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

@ApplicationScoped
public class UserExtensionSchemaValidator {
  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(UserExtensionSchemaValidator.class);
  private final UserExtensionRepository userExtensionRepository;

  public boolean validateUserExtensionSchema(Path tempPathToSchema, String namespace, String defaultPrefix, boolean isUpdate) {
    boolean isValidUpdate = false;
    final List<UserExtensionSchema> userExtensionSchemas = userExtensionRepository.getSchemaByNamespaceOrPrefix(namespace, defaultPrefix);
    if (CollectionUtils.isNotEmpty(userExtensionSchemas)) {
      isValidUpdate = true;
      final List<UserExtensionSchema> schemasAlreadyMapped = userExtensionSchemas.stream().filter(s -> StringUtils.isNotBlank(s.getJsonSchemaS3Key()) || StringUtils.isNotBlank(s.getJsonSchemaUrl())).toList();
      if (!isUpdate) {
        checkIfNamespaceOrPrefixIsMapped(namespace, defaultPrefix, schemasAlreadyMapped);
      }
    }
    if (!tempPathToSchema.getFileName().toString().endsWith(".json")) {
      FileUtils.deleteQuietly(tempPathToSchema.toFile());
      throw new SchemaValidationException("File is required in JSON format");
    }
    try (FileInputStream fis = new FileInputStream(tempPathToSchema.toFile())) {
      final Optional<JsonSchema> schema = readUserExtensionSchema(fis, null);
      if (schema.isEmpty()) {
        FileUtils.deleteQuietly(tempPathToSchema.toFile());
        throw new SchemaValidationException("Provided JSON schema is incorrect");
      }
    } catch (IOException e) {
      log.error(e.getMessage());
      FileUtils.deleteQuietly(tempPathToSchema.toFile());
      throw new ImplementationException(e.getMessage());
    }
    return isValidUpdate;
  }

  public boolean validateUserExtensionSchema(URL url, String defaultPrefix) {
    final Optional<JsonSchema> schema = readUserExtensionSchema(url);
    if (schema.isEmpty()) {
      throw new SchemaValidationException("Provided JSON schema is incorrect");
    }
    boolean isUpdate = false;
    final List<UserExtensionSchema> userExtensionSchemas = userExtensionRepository.getSchemaByNamespaceOrPrefix(url.toString(), defaultPrefix);
    if (CollectionUtils.isNotEmpty(userExtensionSchemas)) {
      isUpdate = true;
      final List<UserExtensionSchema> schemasAlreadyMapped = userExtensionSchemas.stream().filter(s -> StringUtils.isNotBlank(s.getJsonSchemaS3Key()) || StringUtils.isNotBlank(s.getJsonSchemaUrl())).toList();
      checkIfNamespaceOrPrefixIsMapped(url.toString(), defaultPrefix, schemasAlreadyMapped);
    }
    return isUpdate;
  }

  public Optional<JsonSchema> readUserExtensionSchema(final InputStream is, final String resolutionScope) {
    try {
      final JsonSchema schema = JsonSchemaLoader.loadSchema(is, resolutionScope);
      return Optional.of(schema);
    } catch (Exception e) {
      log.error(e.getMessage(), e);
    }
    return Optional.empty();
  }

  public Optional<JsonSchema> readUserExtensionSchema(final URL url) {
    try {
      final JsonSchema schema = JsonSchemaLoader.loadSchema(url);
      return Optional.of(schema);
    } catch (Exception e) {
      log.error(e.getMessage(), e);
    }
    return Optional.empty();
  }

  private void checkIfNamespaceOrPrefixIsMapped(String namespace, String defaultPrefix, List<UserExtensionSchema> schemasAlreadyMapped) {
    if (CollectionUtils.isNotEmpty(schemasAlreadyMapped)) {
      schemasAlreadyMapped.forEach(s -> {
        if (StringUtils.equals(namespace, s.getNamespace()) && StringUtils.equals(defaultPrefix, s.getDefaultPrefix())) {
          throw new SchemaValidationException("The given namespace and default prefix already " + "mapped");
        }
        if (StringUtils.equals(defaultPrefix, s.getDefaultPrefix())) {
          throw new SchemaValidationException("The default prefix is already mapped");
        }
        if (StringUtils.equals(namespace, s.getNamespace())) {
          throw new SchemaValidationException("The namespace is already mapped");
        }
      });
    }
  }

  public UserExtensionSchemaValidator(UserExtensionRepository userExtensionRepository) {
    this.userExtensionRepository = userExtensionRepository;
  }
}
