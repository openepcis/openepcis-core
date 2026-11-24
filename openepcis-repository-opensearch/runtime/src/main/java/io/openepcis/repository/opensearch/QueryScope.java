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
import java.util.List;
import java.util.Optional;

/**
 * Which stored events an event query and its aggregation look at. The Community Edition has one
 * caller and looks at all of them ({@link AllEventsQueryScope}).
 */
public interface QueryScope {

  /** The request's filters, plus whatever restricts them to the caller's events. */
  List<QueryParam> filters(SimpleEventQueryRequest request, Optional<String> defaultGroup);

  /** The index pattern to search, given the configured event index. */
  String eventIndexPattern(String eventIndex, Optional<String> defaultGroup);
}
