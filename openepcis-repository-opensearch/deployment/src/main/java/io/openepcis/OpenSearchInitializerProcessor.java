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
package io.openepcis;

import io.openepcis.repository.opensearch.OpenSearchProducer;
import io.openepcis.repository.opensearch.OpenSearchRecorder;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.BeanContainerBuildItem;
import io.quarkus.arc.deployment.BeanContainerListenerBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;

public class OpenSearchInitializerProcessor {

  private static final String FEATURE = "openepcis-repository-opensearch";

  @BuildStep
  FeatureBuildItem feature() {
    return new FeatureBuildItem(FEATURE);
  }

  @Record(ExecutionTime.STATIC_INIT)
  @BuildStep
  void Build(
      BuildProducer<AdditionalBeanBuildItem> additionalBeanProducer,
      BuildProducer<FeatureBuildItem> featureProducer,
      OpenSearchRecorder recorder,
      BuildProducer<BeanContainerListenerBuildItem> containerListenerProducer) {

    featureProducer.produce(new FeatureBuildItem("OpenSearchInitializer"));

    AdditionalBeanBuildItem unremovableProducer =
        AdditionalBeanBuildItem.unremovableOf(OpenSearchProducer.class);
    additionalBeanProducer.produce(unremovableProducer);

    containerListenerProducer.produce(
        new BeanContainerListenerBuildItem(recorder.setOpenSearchProducer()));
  }

  @Record(ExecutionTime.RUNTIME_INIT)
  @BuildStep
  void processMigration(OpenSearchRecorder recorder, BeanContainerBuildItem beanContainer) {
    recorder.execute(beanContainer.getValue());
  }
}
