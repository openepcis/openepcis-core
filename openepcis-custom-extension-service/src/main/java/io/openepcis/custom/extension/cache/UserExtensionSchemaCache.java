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
package io.openepcis.custom.extension.cache;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import io.openepcis.capture.context.StorageService;
import io.openepcis.model.epcis.UserExtensionSchema;
import io.openepcis.repository.api.UserExtensionRepository;
import io.openepcis.service.util.JsonSchemaLoader;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
@Startup
public class UserExtensionSchemaCache {
  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(UserExtensionSchemaCache.class);
  // ToDo: Replace local cache with distributed cache
  private final Map<String, JsonSchema> namespaceVsSchema = new HashMap<>();
  private final UserExtensionRepository userExtensionRepository;
  private final StorageService storageService;
  private final ObjectMapper objectMapper;
  private final List<UserExtensionSchema> extensionSchema = new ArrayList<>();
  @ConfigProperty(name = "openepcis.user-extension.bundle-defaults", defaultValue = "true")
  boolean bundleDefaults;
  /** Manifests of bundled schemas, in order: the first that names a namespace wins. */
  @ConfigProperty(
      name = "openepcis.user-extension.bundled-manifests",
      defaultValue = "bundled-extension-schemas/manifest.json")
  List<String> bundledManifests = List.of("bundled-extension-schemas/manifest.json");

  @PostConstruct
  public void postConstruct() {
    log.info("Fetch all user extension schemas from user-extension-schema index");
    final List<UserExtensionSchema> userExtensionSchemas = userExtensionRepository.getAllSchema();
    log.info("Caching user extension schema begins");
    userExtensionSchemas.stream().forEach(userExtensionSchema -> {
      try {
        final String namespace = userExtensionSchema.getNamespace();
        if (StringUtils.isNotBlank(userExtensionSchema.getJsonSchemaS3Key())) {
          final String schemaPath = userExtensionSchema.getJsonSchemaS3Key();
          namespaceVsSchema.put(namespace, JsonSchemaLoader.loadSchema(storageService.get(schemaPath)));
        } else if (StringUtils.isNotBlank(userExtensionSchema.getJsonSchemaUrl())) {
          final String schemaPath = userExtensionSchema.getJsonSchemaUrl();
          namespaceVsSchema.put(namespace, JsonSchemaLoader.loadSchema(schemaPath));
        }
        extensionSchema.add(userExtensionSchema);
      } catch (Exception e) {
        log.error(e.getMessage());
      }
    });
    loadBundledSchemas();
    log.info("Caching of user extension schema done");
  }

  /**
   * Load the permissive DPP extension schemas bundled in the image (see {@code
   * resources/bundled-extension-schemas/}) into the cache, but only for namespaces not already
   * registered in the index. This lets a fresh environment capture events that declare a
   * GS1-Extensions namespace (oec / eubat / eutex / …) without an operator first POSTing each
   * schema. An operator-registered schema for the same namespace is loaded above and always wins.
   */
  private void loadBundledSchemas() {
    if (!bundleDefaults) {
      log.info("Bundled extension-schema defaults disabled (openepcis.user-extension.bundle-defaults=false)");
      return;
    }
    for (final String manifest : bundledManifests) {
      loadBundledSchemas(manifest);
    }
  }

  private void loadBundledSchemas(final String bundledManifest) {
    final ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
    try (InputStream manifestStream = classLoader.getResourceAsStream(bundledManifest)) {
      if (manifestStream == null) {
        log.warn("No bundled extension-schema manifest found at {}", bundledManifest);
        return;
      }
      final JsonNode manifest = objectMapper.readTree(manifestStream);
      int loaded = 0;
      for (final JsonNode entry : manifest) {
        final String namespace = entry.path("namespace").asText(null);
        final String resource = entry.path("resource").asText(null);
        if (StringUtils.isBlank(namespace) || StringUtils.isBlank(resource)) {
          continue;
        }
        if (namespaceVsSchema.containsKey(namespace)) {
          continue; // an operator-registered schema already covers this namespace
        }
        try (InputStream schemaStream = classLoader.getResourceAsStream(resource)) {
          if (schemaStream == null) {
            log.warn("Bundled extension schema resource missing: {}", resource);
            continue;
          }
          namespaceVsSchema.put(namespace, JsonSchemaLoader.loadSchema(schemaStream));
          final UserExtensionSchema bundled = new UserExtensionSchema();
          bundled.setNamespace(namespace);
          bundled.setDefaultPrefix(entry.path("defaultPrefix").asText(null));
          bundled.setJsonldContextUrl(entry.path("jsonldContextUrl").asText(null));
          extensionSchema.add(bundled);
          loaded++;
        } catch (Exception e) {
          log.error("Failed to load bundled extension schema {}: {}", resource, e.getMessage());
        }
      }
      log.info("Loaded {} bundled extension schema(s) from {} for namespaces not already registered", loaded, bundledManifest);
    } catch (Exception e) {
      log.error("Failed to process bundled extension-schema manifest: {}", e.getMessage());
    }
  }

  public JsonSchema getSchema(String namespace) {
    return namespaceVsSchema.get(namespace);
  }

  public boolean hasSchema(String namespace) {
    return namespaceVsSchema.containsKey(namespace);
  }

  public void updateSchema(String namespace, JsonSchema schema) {
    namespaceVsSchema.put(namespace, schema);
  }

  public List<UserExtensionSchema> getUserExtensionSchema() {
    return extensionSchema;
  }

  public UserExtensionSchemaCache(UserExtensionRepository userExtensionRepository, StorageService storageService, ObjectMapper objectMapper) {
    this.userExtensionRepository = userExtensionRepository;
    this.storageService = storageService;
    this.objectMapper = objectMapper;
  }
}
