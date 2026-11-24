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
package io.openepcis.respoitory.opensearch.test;

import io.openepcis.repository.opensearch.util.SearchSourceUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.opensearch.client.opensearch._types.FieldSort;
import org.opensearch.client.opensearch._types.SortOptions;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.query_dsl.MatchAllQuery;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.search.Pit;

public class SearchSourceUtilTest {

  @Test
  public void testSearchRequestSerDe() {
    final SearchRequest.Builder builder = new SearchRequest.Builder();
    builder.pit(Pit.of(b -> b.id("myid").keepAlive("10m")));
    builder.sort(
        SortOptions.of(b -> b.field(FieldSort.of(s -> s.field("MYFIELD").order(SortOrder.Asc)))));
    builder.query(b -> b.matchAll(MatchAllQuery.of(q -> q)));
    final String json = SearchSourceUtil.toString(builder.build());
    final SearchRequest searchRequest = SearchSourceUtil.searchRequest(json);
    Assertions.assertEquals(json, SearchSourceUtil.toString(searchRequest));
  }

  @Test
  public void testSortOptionSerDe() {
    final SortOptions sortOptions =
        SortOptions.of(b -> b.field(FieldSort.of(f -> f.field("MYFIELD").order(SortOrder.Asc))));
    final String json = SearchSourceUtil.serialize(sortOptions);
    final SortOptions deserializedSortOptions =
        SearchSourceUtil.deserialize(json, SortOptions.class);
    Assertions.assertEquals(json, SearchSourceUtil.serialize(deserializedSortOptions));
  }

  @Test
  public void testSearchRequestBuilder() {
    final SearchRequest.Builder builder = new SearchRequest.Builder();
    builder.pit(Pit.of(b -> b.id("myid").keepAlive("10m")));
    builder.sort(
        SortOptions.of(b -> b.field(FieldSort.of(s -> s.field("MYFIELD").order(SortOrder.Asc)))));
    builder.query(b -> b.matchAll(MatchAllQuery.of(q -> q)));
    final SearchRequest searchRequest = builder.build();
    SearchRequest.Builder newBuilder = SearchSourceUtil.searchRequestBuilder(searchRequest);
    final String json = SearchSourceUtil.toString(searchRequest);
    Assertions.assertEquals(json, SearchSourceUtil.toString(newBuilder.build()));
  }
}
