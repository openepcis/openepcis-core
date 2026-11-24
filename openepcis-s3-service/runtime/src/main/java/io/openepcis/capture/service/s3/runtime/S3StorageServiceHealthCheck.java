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

import io.openepcis.service.config.CloudStorageConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
import org.eclipse.microprofile.health.Readiness;

@Readiness
@ApplicationScoped
public class S3StorageServiceHealthCheck implements HealthCheck {

  @Inject
  CloudStorageConfig config;

  @Override
  public HealthCheckResponse call() {
    String factoryName = config.factoryName().orElse("");
    boolean isS3Configured = S3StorageServiceFactory.NAME.equalsIgnoreCase(factoryName);

    HealthCheckResponseBuilder builder = HealthCheckResponse.named("OpenEPCIS S3 Storage health check");
    if (isS3Configured) {
      builder.up().withData("status", "configured");
    } else {
      builder.up().withData("status", "not configured");
    }
    return builder.build();
  }
}
