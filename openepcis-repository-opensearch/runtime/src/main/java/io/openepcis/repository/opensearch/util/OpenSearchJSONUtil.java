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
package io.openepcis.repository.opensearch.util;

import io.openepcis.repository.util.JSONUtil;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.commons.collections4.CollectionUtils;
import org.opensearch.client.opensearch.core.search.Hit;

public class OpenSearchJSONUtil extends JSONUtil {
  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(OpenSearchJSONUtil.class);

  private OpenSearchJSONUtil() {
    // Bunch of static methods so there should be no need to create object of this class
    throw new UnsupportedOperationException("Invalid invocation of constructor");
  }

  public static <T> List<T> convert(List<? extends Hit<?>> hits, Class<T> targetType) {
    if (CollectionUtils.isEmpty(hits)) {
      return Collections.emptyList();
    }
    final List<T> list = new ArrayList<>();
    hits.forEach(hit -> {
      if (targetType.isAssignableFrom(hit.source().getClass())) {
        list.add((T) hit.source());
      } else {
        list.add(OBJECT_MAPPER.convertValue(hit.source(), targetType));
      }
    });
    return list;
  }
}
