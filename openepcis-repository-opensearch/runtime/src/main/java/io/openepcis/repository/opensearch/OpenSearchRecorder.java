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
package io.openepcis.repository.opensearch;

import io.openepcis.repository.opensearch.configuration.OpenSearchInitializer;
import io.quarkus.arc.runtime.BeanContainer;
import io.quarkus.arc.runtime.BeanContainerListener;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class OpenSearchRecorder {

  public BeanContainerListener setOpenSearchProducer() {
    return beanContainer -> {
      OpenSearchProducer producer = beanContainer.beanInstance(OpenSearchProducer.class);
    };
  }

  public void execute(BeanContainer beanContainer) {
    OpenSearchInitializer openSearchInitializer =
        beanContainer.beanInstance(OpenSearchInitializer.class);
    openSearchInitializer.createIngestPipeline();
        openSearchInitializer.createTemplates();
        openSearchInitializer.createIndices();
    }
}
