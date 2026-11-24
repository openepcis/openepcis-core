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
package io.openepcis.repository.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import io.openepcis.repository.model.CapturedByES;
import io.openepcis.repository.model.CbvES;
import io.openepcis.repository.model.EPCISEventES;
import io.openepcis.repository.model.ErrorDeclarationES;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Where an event is stored and under which document id.
 *
 * <p>The id is the event's content hash (CBV 8.9.2); an error declaration has the same hash as the
 * event it declares erroneous, so it is stored under the hash with {@link #DECLARATION_QUALIFIER}.
 * The index is chosen by the month of the eventTime, in UTC, and not by tenant or capture time: the
 * hash covers eventTime, so the same event always lands in the same index, and writing with
 * op_type create refuses a second copy there, whoever sends it and however late. That is the
 * pattern of Elastic's "Efficient duplicate prevention for event-based data" and replaces the
 * search on the hash across every index before each write.
 *
 * <p>Events stored before this carry a random id in {@code <prefix>-<tenant>-<recordMonth>}.
 * Readers keep a fallback for them.
 */
public final class EventDocuments {

  /** Appended to the hash for the document id of an error declaration. One per event. */
  public static final String DECLARATION_QUALIFIER = "#errorDeclaration";

  private EventDocuments() {}

  public static String documentId(final String hash, final boolean errorDeclaration) {
    return errorDeclaration ? hash + DECLARATION_QUALIFIER : hash;
  }

  /**
   * Where a stored event is: its index and document id, written as {@code index/_id}. An index
   * name has no slash, a hash id has several, so the first slash separates them.
   */
  public record Location(String index, String id) {
    public static Location parse(final String document) {
      final int slash = document == null ? -1 : document.indexOf('/');
      if (slash <= 0 || slash == document.length() - 1) {
        throw new IllegalArgumentException("not an index/_id document reference: " + document);
      }
      return new Location(document.substring(0, slash), document.substring(slash + 1));
    }

    @Override
    public String toString() {
      return index + "/" + id;
    }
  }

  public static String indexName(final String indexPrefix, final OffsetDateTime eventTime) {
    final OffsetDateTime utc = eventTime.withOffsetSameInstant(ZoneOffset.UTC);
    return indexPrefix + "-" + utc.getYear() + "-" + String.format("%02d", utc.getMonthValue());
  }
  /**
   * Whether the stored event became known to a tenant in this capture, and so is news for that
   * tenant's subscribers. False for an idempotent replay: the tenant of this capture's
   * {@code capturedBy} entry had captured the content before, under another capture. A second
   * tenant sharing the document is news for that tenant and stays in.
   */
  public static boolean newInCapture(final EPCISEventES event, final String captureID) {
    final List<CapturedByES> capturedBy =
        event == null || event.getMetadata() == null ? null : event.getMetadata().getCapturedBy();
    if (capturedBy == null || captureID == null) {
      return true;
    }
    boolean named = false;
    for (final CapturedByES entry : capturedBy) {
      if (entry == null || !captureID.equals(entry.getCaptureID())) {
        continue;
      }
      named = true;
      final String tenant = tenantKey(entry);
      final Optional<CapturedByES> first =
          capturedBy.stream().filter(Objects::nonNull).filter(e -> tenant.equals(tenantKey(e))).findFirst();
      if (first.isPresent() && captureID.equals(first.get().getCaptureID())) {
        return true;
      }
    }
    return !named;
  }

  /** Whether a capture is listed in the stored event's {@code capturedBy} already. */
  public static boolean capturedIn(final EPCISEventES event, final String captureID) {
    return captureID != null
        && event != null
        && event.getMetadata() != null
        && event.getMetadata().getCapturedBy() != null
        && event.getMetadata().getCapturedBy().stream()
            .filter(Objects::nonNull)
            .anyMatch(e -> captureID.equals(e.getCaptureID()));
  }

  /** Whether the stored event was captured by this tenant (its default group, else its user). */
  public static boolean capturedByTenant(final EPCISEventES event, final String defaultGroup, final String userID) {
    if (event == null || event.getMetadata() == null || event.getMetadata().getCapturedBy() == null) {
      return false;
    }
    return event.getMetadata().getCapturedBy().stream()
        .filter(Objects::nonNull)
        .anyMatch(
            e ->
                (e.getDefaultGroup() != null && e.getDefaultGroup().equalsIgnoreCase(defaultGroup))
                    || (e.getDefaultGroup() == null && e.getUserID() != null && e.getUserID().equals(userID)));
  }

  private static String tenantKey(final CapturedByES entry) {
    return entry.getDefaultGroup() != null
        ? "group:" + entry.getDefaultGroup().toLowerCase(Locale.ROOT)
        : "user:" + entry.getUserID();
  }

  /**
   * Whether two error declarations say the same: declarationTime as an instant, reason,
   * correctiveEventIDs in any order, and the extensions. A repeated declaration that says the same
   * is a replay; one that says something else is a second declaration, and there is one per event.
   */
  public static boolean sameDeclaration(final ErrorDeclarationES a, final ErrorDeclarationES b) {
    if (a == null || b == null) {
      return a == b;
    }
    if (a.getDeclarationTime() == null
        ? b.getDeclarationTime() != null
        : b.getDeclarationTime() == null || !a.getDeclarationTime().isEqual(b.getDeclarationTime())) {
      return false;
    }
    if (!asSet(a.getCorrectiveEventIDs()).equals(asSet(b.getCorrectiveEventIDs()))) {
      return false;
    }
    return sameReason(a.getReason(), b.getReason())
        && tree(a.getUserExtensions()).equals(tree(b.getUserExtensions()))
        && tree(a.getInnerUserExtensions()).equals(tree(b.getInnerUserExtensions()));
  }

  /** One reason in any of its spellings: the URN, the URI or the bare word, else as captured. */
  private static boolean sameReason(final CbvES a, final CbvES b) {
    if (a == null || b == null) {
      return a == b;
    }
    if (a.getAsURN() != null && b.getAsURN() != null) {
      return a.getAsURN().equals(b.getAsURN());
    }
    if (a.getAsBareString() != null && b.getAsBareString() != null) {
      return a.getAsBareString().equals(b.getAsBareString());
    }
    if (a.getAsURI() != null && b.getAsURI() != null) {
      return a.getAsURI().equals(b.getAsURI());
    }
    return Objects.equals(a.getAsCaptured(), b.getAsCaptured());
  }

  private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();

  private static HashSet<String> asSet(final List<String> values) {
    return values == null ? new HashSet<>() : new HashSet<>(values);
  }

  /** Through text and back, so a value read from the index and one built in memory compare alike. */
  private static JsonNode tree(final Object value) {
    if (value == null || (value instanceof Collection<?> c && c.isEmpty())) {
      return NullNode.getInstance();
    }
    try {
      return JSON.readTree(JSON.writeValueAsString(value));
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Cannot compare " + value.getClass().getSimpleName(), e);
    }
  }
}
