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
package io.openepcis.repository.opensearch.cache;

import io.openepcis.model.epcis.UserExtensionField;
import io.openepcis.model.epcis.exception.QueryParameterException;
import io.openepcis.repository.cache.UserExtensionsCache;
import io.openepcis.repository.opensearch.QueryRepositoryImpl;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.*;
import java.util.stream.Collectors;

@ApplicationScoped
public class OpenSearchUserExtensionsCache implements UserExtensionsCache {
  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(OpenSearchUserExtensionsCache.class);
  // ToDo: Replace local cache with distributed cache
  private final Map<String, String> userExtensionFieldsAsMap = new HashMap<>();
  private final Set<UserExtensionField> userExtensionFields = new HashSet<>();
  private final QueryRepositoryImpl queryRepository;

  @PostConstruct
  public void postConstruct() {
    log.debug("Fetch all user extension fields from user-extension-fields index");
    queryRepository.getAllUserExtensionFields().subscribe().with(userExtensions -> {
      userExtensionFields.addAll(userExtensions);
      userExtensionFieldsAsMap.putAll(userExtensionFields.stream().filter(u -> Objects.nonNull(u.getName()) && Objects.nonNull(u.getType())).collect(Collectors.toMap(UserExtensionField::getName, UserExtensionField::getType, (x, y) -> x)));
      log.debug("Caching of user extension fields done");
    });
  }

  public Uni<String> getType(String field) {
    if (userExtensionFieldsAsMap.containsKey(field)) {
      return Uni.createFrom().item(userExtensionFieldsAsMap.get(field));
    } else {
      return queryRepository.getUserExtensionField(field).map(uxField -> {
        if (uxField.isPresent()) {
          userExtensionFields.add(uxField.get());
          userExtensionFieldsAsMap.put(uxField.get().getName(), uxField.get().getType());
          return uxField.get().getType();
        } else {
          log.error("Unknown user extension field in orderBy");
          throw new QueryParameterException("Unknown user extension field in orderBy");
        }
      });
    }
  }

  public void updateField(Set<UserExtensionField> fields) {
    userExtensionFields.addAll(fields);
    userExtensionFieldsAsMap.putAll(fields.stream().collect(Collectors.toMap(UserExtensionField::getName, UserExtensionField::getType, (x, y) -> x)));
  }

  public Set<UserExtensionField> getUserExtensionFields() {
    return userExtensionFields;
  }

  public OpenSearchUserExtensionsCache(QueryRepositoryImpl queryRepository) {
    this.queryRepository = queryRepository;
  }
}
