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
package io.openepcis.repository.opensearch;

import io.openepcis.model.epcis.UserExtensionSchema;
import io.openepcis.model.epcis.exception.PersistenceException;
import io.openepcis.repository.Constants;
import io.openepcis.repository.api.UserExtensionRepository;
import io.openepcis.repository.config.RepositoryConfiguration;
import io.openepcis.repository.model.CapturedByES;
import io.openepcis.repository.model.MetadataES;
import io.openepcis.repository.model.UserExtensionSchemaES;
import io.openepcis.service.api.UserDetailsExtractor;
import io.quarkiverse.opensearch.OpenSearchClientName;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;

@ApplicationScoped
public class UserExtensionRepositoryImpl implements UserExtensionRepository {
  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(UserExtensionRepositoryImpl.class);
  private final RepositoryConfiguration configService;
  private final UserDetailsExtractor userDetailsExtractor;
  // Named "admin" client for user extension write operations
  @Inject
  @OpenSearchClientName("admin")
  OpenSearchAsyncClient superUserAsyncClient;
  @Inject
  @OpenSearchClientName("admin")
  OpenSearchClient superUserClient;
  @Inject
  SecurityIdentity securityIdentity;

  @Override
  public void save(UserExtensionSchema userExtensionSchema) {
    try {
      final String defaultGroup = userDetailsExtractor.getDefaultGroup(securityIdentity);
      final UserExtensionSchemaES userExtensionSchemaES = new UserExtensionSchemaES(userExtensionSchema, defaultGroup);
      userExtensionSchemaES.setMetadata(MetadataES.builder().capturedBy(List.of(CapturedByES.builder().timestamp(userExtensionSchema.getCreatedAt()).userID(userExtensionSchema.getCreatedBy()).defaultGroup(defaultGroup).build())).build());
      final IndexRequest<UserExtensionSchemaES> request = new IndexRequest.Builder<UserExtensionSchemaES>().index(configService.user().extension().schema().index()).id(userExtensionSchema.getId()).document(userExtensionSchemaES).build();
      Uni.createFrom().completionStage(superUserAsyncClient.index(request)).map(indexResponse -> indexResponse.id()).subscribe().with(responseId -> {
        log.info("Saved in ES: {} with id : {} ", configService.user().extension().schema().index(), responseId);
      }, throwable -> {
        throw new PersistenceException("Unable to save in ES", throwable);
      });
    } catch (Exception exception) {
      throw new PersistenceException("Error while saving user extension JSON schema " + exception.getMessage(), exception);
    }
  }

  @Override
  public Optional<UserExtensionSchema> getSchemaByNamespace(String namespace) {
    SearchRequest request = new SearchRequest.Builder().index(configService.user().extension().schema().index()).query(q -> q.term(t -> t.field(Constants.NAMESPACE).value(FieldValue.of(namespace)))).build();
    try {
      final SearchResponse<UserExtensionSchema> response = superUserClient.search(request, UserExtensionSchema.class);
      return response.hits().hits().stream().findFirst().map(Hit::source);
    } catch (Exception e) {
      log.error(e.getMessage(), e);
      return Optional.empty();
    }
  }

  @Override
  public List<UserExtensionSchema> getAllSchema() {
    SearchRequest searchRequest = new SearchRequest.Builder().index(configService.user().extension().schema().index()).query(q -> q.matchAll(m -> m)).build();
    try {
      SearchResponse<UserExtensionSchema> response = superUserClient.search(searchRequest, UserExtensionSchema.class);
      return response.hits().hits().stream().map(Hit::source).toList();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public List<UserExtensionSchema> getSchemaByNamespaceOrPrefix(String namespace, String prefixName) {
    final SearchRequest searchRequest = new SearchRequest.Builder().index(configService.user().extension().schema().index()).query(q -> q.bool(b -> b.should(s1 -> s1.term(t -> t.field(Constants.DEFAULT_PREFIX).value(FieldValue.of(prefixName)))).should(s2 -> s2.term(t1 -> t1.field(Constants.NAMESPACE).value(FieldValue.of(namespace)))))).build();
    try {
      return superUserClient.search(searchRequest, UserExtensionSchema.class).hits().hits().stream().map(Hit::source).toList();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void updateJSONSchema(String id, UserExtensionSchema userExtensionSchema) {
    userExtensionSchema.setId(id);
    save(userExtensionSchema);
  }

  @Override
  public String getIdFromNamespace(String namespace) {
    final SearchRequest searchRequest = new SearchRequest.Builder().index(configService.user().extension().schema().index()).query(q -> q.term(t -> t.field(Constants.NAMESPACE).value(FieldValue.of(namespace)))).build();
    try {
      Optional<UserExtensionSchema> schema = superUserClient.search(searchRequest, UserExtensionSchema.class).hits().hits().stream().findFirst().map(Hit::source);
      return schema.isPresent() ? schema.get().getId() : StringUtils.EMPTY;
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public UserExtensionRepositoryImpl(RepositoryConfiguration configService, UserDetailsExtractor userDetailsExtractor) {
    this.configService = configService;
    this.userDetailsExtractor = userDetailsExtractor;
  }
}
