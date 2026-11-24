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
package io.openepcis.repository.etcd.config;

import io.etcd.jetcd.Client;
import io.etcd.jetcd.KV;
import io.etcd.jetcd.Lease;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class EtcdClientProducer {

  private final ETCDConfiguration etcdConfig;

  public EtcdClientProducer(ETCDConfiguration configuration) {
    this.etcdConfig = configuration;
  }

  public void postConstruct() {
    Log.info("using {} as etcd endpoint " + getEndpoint());
  }

  @Produces
  public KV kvClient() {
    return Client.builder().endpoints(getEndpoint()).build().getKVClient();
  }

  @Produces
  public Lease leaseClient() {
    return Client.builder().endpoints(getEndpoint()).build().getLeaseClient();
  }

  private String getEndpoint() {
    return "http://" + etcdConfig.host() + ":2379";
  }
}
