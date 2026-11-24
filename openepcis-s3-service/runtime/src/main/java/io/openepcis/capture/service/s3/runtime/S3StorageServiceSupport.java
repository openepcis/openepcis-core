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
package io.openepcis.capture.service.s3.runtime;

import io.openepcis.model.dto.CaptureJobStatusMessage;
import io.openepcis.s3.AmazonS3Service;
import io.openepcis.service.config.CloudStorageConfig;
import io.smallrye.reactive.messaging.MutinyEmitter;
import io.smallrye.reactive.messaging.kafka.Record;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.OnOverflow;

@ApplicationScoped
public class S3StorageServiceSupport {

  @Inject CloudStorageConfig cloudStorageConfig;

  @Inject AmazonS3Service amazonS3Service;

  @Inject ManagedExecutor managedExecutor;

  @Channel("capture-document-out")
  @OnOverflow(OnOverflow.Strategy.UNBOUNDED_BUFFER)
  MutinyEmitter<Record<String, CaptureJobStatusMessage>> captureJobEmitter;

  public S3StorageServiceImpl newInstance() {
    return new S3StorageServiceImpl(
        cloudStorageConfig, amazonS3Service, managedExecutor, captureJobEmitter);
  }
}
