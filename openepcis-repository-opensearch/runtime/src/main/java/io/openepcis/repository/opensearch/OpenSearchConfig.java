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

import io.quarkus.runtime.annotations.StaticInitSafe;
import io.smallrye.config.ConfigMapping;
import java.util.Optional;

/**
 * OpenSearch configuration for EPCIS repository.
 */
@StaticInitSafe
@ConfigMapping(prefix = "opensearch")
public interface OpenSearchConfig {

    /**
     * Index templates configuration.
     */
    Templates templates();

    /**
     * Index names configuration.
     */
    Indices indices();

    /**
     * Ingest pipelines configuration.
     */
    Pipelines pipelines();

    interface Templates {
        /**
         * Template name for capture job index.
         */
        String captureJob();

        /**
         * Template name for EPCIS event index.
         */
        String epcisEvent();

        /**
         * Template name for EPC index.
         */
        String epc();

        /**
         * Template name for EPCIS query index.
         */
        String epcisQuery();

        /**
         * Template name for EPCIS subscription index.
         */
        String epcisSubscription();

        /**
         * Template name for EPCIS percolator queries index.
         */
        String epcisPercolatorQueries();

        /**
         * Replica count for the indices these templates create, when the deployment
         * wants one other than the template's own.
         *
         * <p>It needs saying because the composable switch took the choice away from
         * somewhere else. A single-node cluster cannot allocate a replica, so such a
         * cluster carries a catch-all template that sets the count to zero - and that
         * catch-all is precisely what a composable template at our priority now
         * overrides. Without a way to say "zero" here, repairing the mapping would
         * leave a dev cluster permanently yellow. Absent, the template's own value
         * stands, which is what a real multi-node deployment wants.
         */
        Optional<Integer> numberOfReplicas();
    }

    interface Indices {
        /**
         * Index name for user extension fields.
         */
        String userExtensionFields();

        /**
         * Index name for user extension schema.
         */
        String userExtensionSchema();

        /**
         * Further indices to create at startup when missing: index name to the classpath location
         * of its mapping.
         */
        java.util.Map<String, String> extra();
    }

    interface Pipelines {
        /**
         * Ingest pipeline name for adding metadata.
         */
        String addMetaDataPipeline();
    }
}
