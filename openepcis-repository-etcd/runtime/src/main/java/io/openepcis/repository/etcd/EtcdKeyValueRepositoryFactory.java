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
package io.openepcis.repository.etcd;

import io.openepcis.keyvalue.repository.KeyValueRepositoryFactory;
import io.openepcis.repository.api.KeyValueRepository;
import io.openepcis.repository.config.RepositoryConfiguration;
import jakarta.enterprise.inject.spi.CDI;

public class EtcdKeyValueRepositoryFactory implements KeyValueRepositoryFactory {

  @Override
  public KeyValueRepository newInstance(RepositoryConfiguration configService) {
    if (CDI.current().select(EtcdSupport.class).isUnsatisfied()) {
      throw new RuntimeException("unable to get EtcdSupport for setting up EtcdKeyValueRepository");
    }
    EtcdSupport etcdSupport = CDI.current().select(EtcdSupport.class).get();
    return etcdSupport.createEtcdRepository(configService);
  }
}
