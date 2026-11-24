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
package io.openepcis.custom.extension.service;

import io.openepcis.capture.context.StorageService;
import io.openepcis.core.exception.SchemaValidationException;
import io.openepcis.custom.extension.util.FileOperation;
import io.openepcis.custom.extension.validators.UserExtensionSchemaValidator;
import io.openepcis.model.epcis.UserExtensionSchema;
import io.openepcis.model.epcis.exception.PersistenceException;
import io.openepcis.repository.api.UserExtensionRepository;
import io.openepcis.service.api.UserExtensionService;
import io.openepcis.service.config.CloudStorageConfig;
import io.openepcis.service.util.Constants;
import jakarta.enterprise.context.ApplicationScoped;
import java.net.URL;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

@ApplicationScoped
public class UserExtensionServiceImpl implements UserExtensionService {
  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(UserExtensionServiceImpl.class);
  private final UserExtensionRepository userExtensionRepository;
  private final UserExtensionSchemaValidator schemaValidator;
  private final StorageService storageService;
  private final CloudStorageConfig cloudStorageConfig;

  @Override
  public UserExtensionSchema getById(String id) {
    return null;
  }

  @Override
  public UserExtensionSchema getByNamespace(String namespace) {
    return null;
  }

  @Override
  public void storeJsonSchema(Path tempPathToSchema, String namespace, String defaultPrefix, String createdBy, String jsonldContextUrl) {
    final String schemaId = UUID.randomUUID().toString();
    final String keyToSchema = cloudStorageConfig.jsonSchemaKeyPrefix() + schemaId + "_" + tempPathToSchema.getFileName();
    log.info("Validate user extension schema");
    schemaValidator.validateUserExtensionSchema(tempPathToSchema, namespace, defaultPrefix, false);
    final UserExtensionSchema userExtensionSchema = buildUserExtensionSchema(namespace, defaultPrefix, schemaId, keyToSchema, StringUtils.EMPTY, createdBy, jsonldContextUrl);
    Map<String, String> tags = createTags(userExtensionSchema, false);
    saveSchemaToS3(keyToSchema, tempPathToSchema, tags);
    saveSchema(tempPathToSchema, userExtensionSchema);
    FileOperation.delete(tempPathToSchema);
  }

  private Map<String, String> createTags(UserExtensionSchema userExtensionSchema, boolean isUpdate) {
    Map<String, String> tags = new HashMap<>();
    tags.put(Constants.CREATED_AT, userExtensionSchema.getCreatedAt().toString());
    tags.put(Constants.CREATED_BY, userExtensionSchema.getCreatedBy());
    tags.put(Constants.NAMESPACE, userExtensionSchema.getNamespace());
    tags.put(Constants.DEFAULT_PREFIX, userExtensionSchema.getDefaultPrefix());
    // Add the jsonld context URL tag only if present
    if (StringUtils.isNotBlank(userExtensionSchema.getJsonldContextUrl())) {
      tags.put(Constants.JSONLD_CONTEXT_URL, userExtensionSchema.getJsonldContextUrl());
    }
    if (isUpdate) {
      tags.put(Constants.UPDATED_BY, userExtensionSchema.getUpdatedBy());
      tags.put(Constants.UPDATED_AT, userExtensionSchema.getUpdatedAt().toString());
    }
    return tags;
  }

  @Override
  public void storeJsonSchema(URL url, String defaultPrefix, String createdBy) {
    final String schemaId = UUID.randomUUID().toString();
    log.info("Validate user extension schema");
    boolean isUpdate = schemaValidator.validateUserExtensionSchema(url, defaultPrefix);
    final UserExtensionSchema userExtensionSchema = buildUserExtensionSchema(url.toString(), defaultPrefix, schemaId, StringUtils.EMPTY, url.toString(), createdBy, null);
    if (isUpdate) {
      String id = userExtensionRepository.getIdFromNamespace(url.toString());
      userExtensionRepository.updateJSONSchema(id, userExtensionSchema);
    } else {
      userExtensionRepository.save(userExtensionSchema);
    }
  }

  private void saveSchemaToS3(String keyToSchema, Path tempPathToSchema, Map<String, String> tags) {
    log.info("Save schema {} in S3 bucket", keyToSchema);
    final long contentLength = tempPathToSchema.toFile().length();
    storageService.userExtensionSchemaUpload(keyToSchema, tempPathToSchema, tags, contentLength);
  }

  private void saveSchema(Path tempPathToSchema, UserExtensionSchema userExtensionSchema) {
    log.info("Save user extension schema details");
    try {
      userExtensionRepository.save(userExtensionSchema);
    } catch (Exception e) {
      FileUtils.deleteQuietly(tempPathToSchema.toFile());
      throw new PersistenceException(e.getMessage(), e);
    }
  }

  private UserExtensionSchema buildUserExtensionSchema(String namespace, String defaultPrefix, String schemaId, String jsonSchemaS3Key, String jsonSchemaUrl, String createdBy, String jsonldContextUrl) {
    final UserExtensionSchema userExtensionSchema = new UserExtensionSchema();
    userExtensionSchema.setId(schemaId);
    userExtensionSchema.setJsonSchemaS3Key(jsonSchemaS3Key);
    userExtensionSchema.setJsonSchemaUrl(jsonSchemaUrl);
    userExtensionSchema.setNamespace(namespace);
    userExtensionSchema.setDefaultPrefix(defaultPrefix);
    userExtensionSchema.setCreatedAt(OffsetDateTime.now());
    userExtensionSchema.setCreatedBy(createdBy);
    // Add the jsonld context URL only if present
    if (StringUtils.isNotBlank(jsonldContextUrl)) {
      userExtensionSchema.setJsonldContextUrl(jsonldContextUrl);
    }
    return userExtensionSchema;
  }

  public void updateJSONSchema(Path tempPathToSchema, String namespace, String defaultPrefix, String updatedBy) {
    boolean isValidUpdate = schemaValidator.validateUserExtensionSchema(tempPathToSchema, namespace, defaultPrefix, true);
    if (isValidUpdate) {
      UserExtensionSchema userExtensionSchema = userExtensionRepository.getSchemaByNamespaceOrPrefix(namespace, defaultPrefix).stream().findFirst().get();
      final String keyToSchema = cloudStorageConfig.jsonSchemaKeyPrefix() + userExtensionSchema.getId() + "_" + tempPathToSchema.getFileName();
      userExtensionSchema.setUpdatedAt(OffsetDateTime.now());
      userExtensionSchema.setUpdatedBy(updatedBy);
      userExtensionSchema.setJsonSchemaS3Key(keyToSchema);
      Map<String, String> tags = createTags(userExtensionSchema, isValidUpdate);
      storageService.userExtensionSchemaUpload(keyToSchema, tempPathToSchema, tags, tempPathToSchema.toFile().length());
      userExtensionRepository.updateJSONSchema(userExtensionSchema.getId(), userExtensionSchema);
    } else {
      throw new SchemaValidationException("Update Operation Failed: Provided JSON schema is incorrect");
    }
    FileOperation.delete(tempPathToSchema);
  }

  public UserExtensionServiceImpl(UserExtensionRepository userExtensionRepository, UserExtensionSchemaValidator schemaValidator, StorageService storageService, CloudStorageConfig cloudStorageConfig) {
    this.userExtensionRepository = userExtensionRepository;
    this.schemaValidator = schemaValidator;
    this.storageService = storageService;
    this.cloudStorageConfig = cloudStorageConfig;
  }
}
