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

import io.openepcis.query.data.QueryParam;
import io.openepcis.query.data.SimpleEventQueryRequest;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;

/** Every query sees every event; the request's filters are all there is. */
@DefaultBean
@ApplicationScoped
public class AllEventsQueryScope implements QueryScope {

  @Override
  public List<QueryParam> filters(final SimpleEventQueryRequest request, final Optional<String> defaultGroup) {
    return request.getFilters();
  }

  @Override
  public String eventIndexPattern(final String eventIndex, final Optional<String> defaultGroup) {
    return eventIndex + "*";
  }
}
