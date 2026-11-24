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

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * What {@code GET /events/aggregate} can count events by, and where each grouping lives in the
 * event index.
 *
 * <p>The vocabulary and identifier groupings count on the normalised URN ({@code asURN}), which
 * is what the EPCIS filters match first and what a standard value is stored as however it was
 * captured. A value that has no URN form (a custom vocabulary written as a web URI, a read point
 * that is not a GS1 key) has only {@code asCaptured}; those are counted under that value instead,
 * so that no event with the field drops out of the buckets.
 */
public enum EventGroupBy {
  eventType("type", null, null),
  action("action", null, null),
  bizStep("bizStep.asURN", "bizStep.asCaptured", null),
  disposition("disposition.asURN", "disposition.asCaptured", null),
  readPoint("readPoint.id.asURN", "readPoint.id.asCaptured", null),
  bizLocation("bizLocation.id.asURN", "bizLocation.id.asCaptured", null),
  day("eventTime", null, "yyyy-MM-dd"),
  month("eventTime", null, "yyyy-MM");

  public static final int MIN_SIZE = 1;
  public static final int MAX_SIZE = 100;
  public static final int DEFAULT_SIZE = 20;

  private final String field;
  private final String fallbackField;
  private final String dateFormat;

  EventGroupBy(final String field, final String fallbackField, final String dateFormat) {
    this.field = field;
    this.fallbackField = fallbackField;
    this.dateFormat = dateFormat;
  }

  /** The index field the buckets are taken from. */
  public String field() {
    return field;
  }

  /** The field counted for events that have no value in {@link #field()}, if any. */
  public Optional<String> fallbackField() {
    return Optional.ofNullable(fallbackField);
  }

  /** A calendar histogram on eventTime (UTC) rather than a terms grouping. */
  public boolean isHistogram() {
    return dateFormat != null;
  }

  /** The key format of a histogram bucket. */
  public String dateFormat() {
    return dateFormat;
  }

  public static Optional<EventGroupBy> parse(final String value) {
    if (value == null) {
      return Optional.empty();
    }
    return Arrays.stream(values()).filter(g -> g.name().equals(value.trim())).findFirst();
  }

  public static String names() {
    return Arrays.stream(values()).map(Enum::name).collect(Collectors.joining(", "));
  }
}
