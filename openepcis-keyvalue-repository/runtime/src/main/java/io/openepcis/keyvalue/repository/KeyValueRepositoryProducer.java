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

import io.openepcis.repository.api.KeyValueRepository;
import io.openepcis.repository.config.RepositoryConfiguration;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import java.util.ServiceLoader;

@ApplicationScoped
public class KeyValueRepositoryProducer {

  private final RepositoryConfiguration configService;

  public KeyValueRepositoryProducer(final RepositoryConfiguration configService) {
    this.configService = configService;
  }

  @Produces
  @Singleton
  public KeyValueRepository createKeyValueRepository() {
    var factory = ServiceLoader.load(KeyValueRepositoryFactory.class).findFirst();
    if (factory.isPresent()) {
      return factory.get().newInstance(configService);
    }
    return new CaffeineCacheKeyValueRepository(configService).initialize();
  }
}
