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
package io.openepcis.service.api;

import io.openepcis.model.epcis.UserExtensionSchema;
import java.net.URL;
import java.nio.file.Path;

public interface UserExtensionService {
  UserExtensionSchema getById(String id);

  UserExtensionSchema getByNamespace(String namespace);

  void storeJsonSchema(Path schemaPath, String namespace, String defaultPrefix, String createdBy, String jsonldContextUrl);

  void storeJsonSchema(URL url, String defaultPrefix, String createdBy);

  void updateJSONSchema(
      Path tempPathToSchema, String namespace, String defaultPrefix, String updatedBy);
}
