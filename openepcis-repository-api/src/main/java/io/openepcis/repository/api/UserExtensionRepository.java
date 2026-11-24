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
package io.openepcis.repository.api;

import io.openepcis.model.epcis.UserExtensionSchema;
import java.util.List;
import java.util.Optional;

public interface UserExtensionRepository {
  void save(UserExtensionSchema userExtensionSchema);

  Optional<UserExtensionSchema> getSchemaByNamespace(String namespace);

  List<UserExtensionSchema> getAllSchema();

  List<UserExtensionSchema> getSchemaByNamespaceOrPrefix(String namespace, String prefixName);

  void updateJSONSchema(String id, UserExtensionSchema userExtensionSchema);

  String getIdFromNamespace(String namespace);
}
