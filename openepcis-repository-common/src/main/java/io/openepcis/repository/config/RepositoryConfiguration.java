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
package io.openepcis.repository.config;

import io.quarkus.runtime.annotations.StaticInitSafe;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@StaticInitSafe
@ConfigMapping(prefix = "repository")
public interface RepositoryConfiguration {

  Epcis epcis();

  User user();

  Capture capture();

  Epc epc();

    interface Epc {
        String index();
    }

  interface Epcis {
    Event event();

    Query query();

    Subscription subscription();

    Streaming streaming();

    String nextPageTokenTTL();

    Integer perPageDefault();

    Integer perPageMax();

    TopLevel topLevel();

    Pit pit();

    /** How the query path uses OpenSearch points in time (see PitPaginationProvider). */
    interface Pit {
      /**
       * Open a point in time only when a result has a next page. The first page is a plain search
       * with one hit more than asked for, which tells whether there is more. Off: open it before
       * every search, as before.
       */
      @WithDefault("true")
      boolean lazy();

      /** How often expired points in time the cluster has not reclaimed are closed, e.g. {@code 15s}. */
      @WithDefault("15s")
      String sweepInterval();
    }

    interface TopLevel {
      Integer perPageDefault();

      Integer perPageMax();
    }

    interface Event {
      String index();
    }

    interface Query {
      String index();
    }

    interface Subscription {
      String index();
    }

    interface Streaming {
      String subscription();
    }
  }

  interface Capture {
    Job job();

    interface Job {
      String index();
    }
  }

  interface User {
    String defaultGroup();

    @WithDefault("false")
    boolean usernameIsDefaultGroup();

    String defaultUser();

    Extension extension();

    interface Extension {
      Fields fields();

      Schema schema();

      interface Fields {
        String index();
      }

      interface Schema {
        String index();
      }
    }
  }

}
