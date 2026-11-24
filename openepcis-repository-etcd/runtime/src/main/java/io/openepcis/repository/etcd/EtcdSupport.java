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

import io.etcd.jetcd.KV;
import io.etcd.jetcd.Lease;
import io.openepcis.repository.api.KeyValueRepository;
import io.openepcis.repository.config.RepositoryConfiguration;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class EtcdSupport {

  private final KV kvClient;
  private final Lease leaseClient;

  public EtcdSupport(KV kv, Lease leaseClient) {
    this.kvClient = kv;
    this.leaseClient = leaseClient;
  }

  public KeyValueRepository createEtcdRepository(
      final RepositoryConfiguration repositoryConfiguration) {
    return new EtcdKeyValueRepository(kvClient, leaseClient, repositoryConfiguration);
  }
}
