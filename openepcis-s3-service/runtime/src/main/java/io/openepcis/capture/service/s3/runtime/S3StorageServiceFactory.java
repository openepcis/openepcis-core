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

import io.openepcis.capture.context.StorageService;
import io.openepcis.storage.service.runtime.StorageServiceFactory;
import jakarta.enterprise.inject.spi.CDI;

public class S3StorageServiceFactory implements StorageServiceFactory {

  public static final String NAME = "s3";

  @Override
  public StorageService newInstance() {
    if (CDI.current().select(S3StorageServiceSupport.class).isUnsatisfied()) {
      throw new RuntimeException(
          S3StorageServiceSupport.class.getName() + " is unsatisfied from CDI");
    }
    return CDI.current().select(S3StorageServiceSupport.class).get().newInstance();
  }

  @Override
  public String getName() {
    return S3StorageServiceFactory.NAME;
  }
}
