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
package io.openepcis.query.aggregation;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The answer of {@code GET /events/aggregate}:
 *
 * <pre>{"groupBy":"bizStep","total":12345,"buckets":[{"key":"...","count":512}],"otherCount":17}</pre>
 *
 * <p>{@code total} is every event the query matches, {@code otherCount} the events in groups
 * beyond the returned buckets (always 0 for a calendar histogram). Events without the grouped
 * field are in {@code total} and in no bucket, so the buckets and {@code otherCount} together can
 * be less than {@code total}.
 */
@JsonPropertyOrder({"groupBy", "total", "buckets", "otherCount"})
public record EventAggregation(String groupBy, long total, List<Bucket> buckets, long otherCount) {

  @JsonPropertyOrder({"key", "count"})
  public record Bucket(String key, long count) {}

  public EventAggregation {
    buckets = buckets == null ? List.of() : List.copyOf(buckets);
  }

  /** A calendar histogram: every bucket, oldest first, nothing beyond them. */
  public static EventAggregation histogram(final EventGroupBy groupBy, final long total, final List<Bucket> buckets) {
    return new EventAggregation(groupBy.name(), total, buckets, 0L);
  }

  /**
   * A terms grouping from the buckets of the normalised field and those of the fallback field
   * (events without a normalised value). A key in both is one group. The largest {@code size}
   * groups are kept, by count and then by key; what is cut off goes into {@code otherCount}
   * together with what the search itself left out.
   */
  public static EventAggregation terms(
      final EventGroupBy groupBy,
      final long total,
      final List<Bucket> primary,
      final long primaryOther,
      final List<Bucket> fallback,
      final long fallbackOther,
      final int size) {
    final Map<String, Long> counts = new LinkedHashMap<>();
    for (final List<Bucket> list : List.of(nullSafe(primary), nullSafe(fallback))) {
      for (final Bucket b : list) {
        counts.merge(b.key(), b.count(), Long::sum);
      }
    }
    final List<Bucket> merged = new ArrayList<>();
    counts.forEach((key, count) -> merged.add(new Bucket(key, count)));
    merged.sort(Comparator.comparingLong(Bucket::count).reversed().thenComparing(Bucket::key));
    final List<Bucket> kept = merged.size() > size ? merged.subList(0, size) : merged;
    long other = primaryOther + fallbackOther;
    for (int i = kept.size(); i < merged.size(); i++) {
      other += merged.get(i).count();
    }
    return new EventAggregation(groupBy.name(), total, kept, other);
  }

  private static List<Bucket> nullSafe(final List<Bucket> buckets) {
    return buckets == null ? List.of() : buckets;
  }
}
