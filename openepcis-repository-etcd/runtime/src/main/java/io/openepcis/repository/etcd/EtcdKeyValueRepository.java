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

import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.KV;
import io.etcd.jetcd.Lease;
import io.etcd.jetcd.kv.GetResponse;
import io.etcd.jetcd.kv.PutResponse;
import io.etcd.jetcd.lease.LeaseGrantResponse;
import io.etcd.jetcd.options.PutOption;
import io.openepcis.core.exception.ResourceNotFoundException;
import io.openepcis.model.epcis.exception.ExceptionMessages;
import io.openepcis.model.epcis.exception.PersistenceException;
import io.openepcis.repository.Constants;
import io.openepcis.repository.api.KeyValueRepository;
import io.openepcis.repository.config.RepositoryConfiguration;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.UUID;

public class EtcdKeyValueRepository implements KeyValueRepository {
  private final KV kvClient;
  private final Lease leaseClient;
  private final RepositoryConfiguration configService;

  public EtcdKeyValueRepository(
      KV kvClient, Lease leaseClient, RepositoryConfiguration configuration) {
    this.kvClient = kvClient;
    this.leaseClient = leaseClient;
    this.configService = configuration;
  }

  public Uni<String> save(String entity) {
    Log.info("saving entity to etcd");
    final String strKey = UUID.randomUUID().toString();

    final ByteSequence key = ByteSequence.from(strKey.getBytes());
    final ByteSequence value = ByteSequence.from(entity.getBytes());
    return lease()
        .chain(leaseGrantResponse -> put(key, value, leaseGrantResponse.getID()))
        .map(putResponse -> strKey);
  }

  public Uni<String> getAndLease(String strKey) {
    final ByteSequence key = ByteSequence.from(strKey.getBytes());
    return get(key)
        .map(
            response -> {
              if (response.getKvs().isEmpty()) {
                throw new ResourceNotFoundException(
                    String.format(ExceptionMessages.NEXT_PAGE_TOKEN_DOES_NOT_EXIST, strKey));
              }
              return response.getKvs();
            })
        .chain(
            keyValues ->
                lease()
                    .chain(
                        leaseGrantResponse ->
                            put(key, keyValues.get(0).getValue(), leaseGrantResponse.getID())
                                .map(putResponse -> keyValues)))
        .map(keyValues -> keyValues.get(0).getValue().toString(Charset.defaultCharset()));
  }

  public Uni<String> get(String strKey) {
    final ByteSequence key = ByteSequence.from(strKey.getBytes());
    return get(key)
        .map(GetResponse::getKvs)
        .map(
            keyValues -> {
              if (keyValues.isEmpty()) {
                throw new ResourceNotFoundException(
                    String.format(ExceptionMessages.NEXT_PAGE_TOKEN_DOES_NOT_EXIST, strKey));
              }
              return keyValues;
            })
        .map(keyValues -> keyValues.get(0).getValue().toString(Charset.defaultCharset()));
  }

  public Uni<String> delete(String strKey) {
    final ByteSequence key = ByteSequence.from(strKey.getBytes());
    return Uni.createFrom()
        .completionStage(kvClient.delete(key))
        .map(deleteResponse -> strKey)
        .onFailure()
        .transform(
            e -> {
              Log.error(ExceptionMessages.ERROR_WHILE_DELETING_KEY_VALUE_FROM_ETCD);
              return new PersistenceException(
                  ExceptionMessages.ERROR_WHILE_DELETING_KEY_VALUE_FROM_ETCD, e);
            });
  }

  private Uni<LeaseGrantResponse> lease() {
    return Uni.createFrom()
        .completionStage(
            leaseClient.grant(
                Duration.parse(
                        Constants.PARSE_TIME
                            + configService.epcis().nextPageTokenTTL().toUpperCase())
                    .getSeconds()))
        .ifNoItem()
        .after(Duration.ofSeconds(5))
        .failWith(new PersistenceException("unable to get lease from etcd within 5 seconds"));
  }

  private Uni<PutResponse> put(
      final ByteSequence key, final ByteSequence value, final long leaseId) {
    return Uni.createFrom()
        .completionStage(
            kvClient.put(key, value, PutOption.newBuilder().withLeaseId(leaseId).build()))
        .ifNoItem()
        .after(Duration.ofSeconds(5))
        .failWith(new PersistenceException("unable to put to etcd within 5 seconds"));
  }

  private Uni<GetResponse> get(final ByteSequence key) {
    return Uni.createFrom()
        .completionStage(kvClient.get(key))
        .ifNoItem()
        .after(Duration.ofSeconds(5))
        .failWith(
            new PersistenceException(ExceptionMessages.ERROR_WHILE_FETCHING_KEY_VALUE_FROM_ETCD));
  }
}
