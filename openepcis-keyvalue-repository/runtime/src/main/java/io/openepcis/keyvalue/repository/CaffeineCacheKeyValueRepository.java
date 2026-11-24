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

package io.openepcis.keyvalue.repository;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.openepcis.core.exception.ResourceNotFoundException;
import io.openepcis.model.epcis.exception.ExceptionMessages;
import io.openepcis.repository.Constants;
import io.openepcis.repository.api.KeyValueRepository;
import io.openepcis.repository.config.RepositoryConfiguration;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import java.time.Duration;
import java.util.UUID;

public class CaffeineCacheKeyValueRepository implements KeyValueRepository {
  private final RepositoryConfiguration configService;
  private Cache<String, String> inMemoryCache;

  public CaffeineCacheKeyValueRepository(final RepositoryConfiguration configService) {
    this.configService = configService;
  }

  CaffeineCacheKeyValueRepository initialize() {
    inMemoryCache =
        Caffeine.newBuilder()
            .expireAfterAccess(
                Duration.parse(
                    Constants.PARSE_TIME + configService.epcis().nextPageTokenTTL().toUpperCase()))
            .build();
    return this;
  }

  public Uni<String> save(String entity) {
    return Uni.createFrom()
        .item(
            () -> {
              Log.debug("saving entity to in-memory");
              final String strKey = UUID.randomUUID().toString();
              inMemoryCache.put(strKey, entity);
              return strKey;
            });
  }

  @Override
  public Uni<String> get(String strKey) {
    String cachedValue = inMemoryCache.getIfPresent(strKey);
    if (cachedValue == null) {
      return Uni.createFrom()
          .failure(
              new ResourceNotFoundException(
                  String.format(ExceptionMessages.NEXT_PAGE_TOKEN_DOES_NOT_EXIST, strKey)));
    }
    return Uni.createFrom().item(cachedValue);
  }

  @Override
  public Uni<String> getAndLease(String strKey) {
    String cachedValue = inMemoryCache.getIfPresent(strKey);
    if (cachedValue != null) {
      return Uni.createFrom().item(cachedValue);
    } else {
      return Uni.createFrom()
          .failure(
              new ResourceNotFoundException(
                  String.format(ExceptionMessages.NEXT_PAGE_TOKEN_DOES_NOT_EXIST, strKey)));
    }
  }

  @Override
  public Uni<String> delete(String strKey) {
    String cachedValue = inMemoryCache.getIfPresent(strKey);
    if (cachedValue != null) {
      inMemoryCache.invalidate(strKey);
      return Uni.createFrom().item(strKey);
    } else {
      return Uni.createFrom()
          .failure(
              new ResourceNotFoundException(
                  String.format(ExceptionMessages.NEXT_PAGE_TOKEN_DOES_NOT_EXIST, strKey)));
    }
  }
}
