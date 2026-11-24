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
package io.openepcis.capture.kafka.streaming;

import io.openepcis.capture.context.delegates.CaptureJobStatusMessageDelegate;
import io.openepcis.capture.kafka.KafkaConfigurationService;
import io.openepcis.model.dto.CaptureJobStatusMessage;
import io.quarkus.cache.CacheKey;
import io.quarkus.cache.CacheResult;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StoreQueryParameters;
import org.apache.kafka.streams.StreamsMetadata;
import org.apache.kafka.streams.errors.InvalidStateStoreException;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;

@ApplicationScoped
public class CaptureJobStatusMessageKafkaStreams implements CaptureJobStatusMessageDelegate {
  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(CaptureJobStatusMessageKafkaStreams.class);
  private final KafkaConfigurationService kafkaConfigurationService;
  private final AtomicReference<ReadOnlyKeyValueStore<String, CaptureJobStatusMessage>> captureStatusJobKeyValueStore = new AtomicReference<>(null);
  @Inject
  KafkaStreams streams;

  public Collection<String> getStores() {
    return streams.metadataForAllStreamsClients().stream().flatMap(m -> m.stateStoreNames().stream()).toList();
  }

  public Collection<StreamsMetadata> getStoreStreamMetadata(String store) {
    return streams.streamsMetadataForStore(store).stream().toList();
  }

  @Override
  @CacheResult(cacheName = "capture-job-status-message")
  public Uni<CaptureJobStatusMessage> getCaptureJobStatusMessage(@CacheKey String captureID) {
    try {
      if (captureStatusJobKeyValueStore.get() == null) {
        return Uni.createFrom().completionStage(CompletableFuture.supplyAsync(() -> {
          ReadOnlyKeyValueStore<String, CaptureJobStatusMessage> s = streams.store(StoreQueryParameters.fromNameAndType(kafkaConfigurationService.stores().globalCaptureJobMessageStore(), QueryableStoreTypes.keyValueStore()));
          captureStatusJobKeyValueStore.set(s);
          return s.get(captureID);
        }));
      } else {
        return Uni.createFrom().item(captureStatusJobKeyValueStore.get().get(captureID));
      }
    } catch (InvalidStateStoreException e) {
      log.error(e.getMessage(), e);
      captureStatusJobKeyValueStore.set(null);
    }
    return Uni.createFrom().nothing();
  }

  public CaptureJobStatusMessageKafkaStreams(KafkaConfigurationService kafkaConfigurationService) {
    this.kafkaConfigurationService = kafkaConfigurationService;
  }
}
