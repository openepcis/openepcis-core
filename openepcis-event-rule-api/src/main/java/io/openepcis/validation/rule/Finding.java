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
package io.openepcis.validation.rule;

import java.util.List;
import java.util.Objects;

/**
 * One thing a rule has to say about an event. A rule fills in what it knows (severity, detail, the
 * field and the identifiers concerned); the engine adds the rule's catalogue entry (id, type,
 * title, status) and the location of the event in the document.
 *
 * @param ruleId the rule that found it
 * @param type problem type URN; the rule's {@link EventRule#type()} unless the rule sets one
 * @param title short label, constant per type; the rule's {@link EventRule#title()} unless set
 * @param status HTTP-style status; the rule's {@link EventRule#status()} unless set
 * @param severity how much it weighs; configuration may override it per rule and tenant
 * @param detail what is wrong and, if possible, how to put it right
 * @param jsonPath JSONPath relative to the event ({@code $.bizLocation}), or {@code null} for the
 *     event as a whole
 * @param identifiers the EPCs concerned, as captured; the engine reports them as URN and Digital Link
 * @param sequence positions in the capture document; {@code null} means the event's own index
 */
public record Finding(
    String ruleId,
    String type,
    String title,
    Integer status,
    Severity severity,
    String detail,
    String jsonPath,
    List<String> identifiers,
    List<Integer> sequence) {

  public Finding {
    Objects.requireNonNull(detail, "detail");
    identifiers = identifiers == null ? List.of() : List.copyOf(identifiers);
    sequence = sequence == null ? null : List.copyOf(sequence);
  }

  public static Finding hard(final String detail) {
    return new Finding(null, null, null, null, Severity.HARD_ERROR, detail, null, null, null);
  }

  public static Finding soft(final String detail) {
    return new Finding(null, null, null, null, Severity.SOFT_ERROR, detail, null, null, null);
  }

  /** The field concerned: a bare field name ({@code bizLocation}) or a JSONPath ({@code $.ilmd.x}). */
  public Finding at(final String field) {
    final String path = field == null || field.startsWith("$") ? field : "$." + field;
    return new Finding(ruleId, type, title, status, severity, detail, path, identifiers, sequence);
  }

  public Finding about(final List<String> epcs) {
    return new Finding(ruleId, type, title, status, severity, detail, jsonPath, epcs, sequence);
  }

  public Finding typed(final String problemType) {
    return new Finding(ruleId, problemType, title, status, severity, detail, jsonPath, identifiers, sequence);
  }

  public Finding titled(final String newTitle) {
    return new Finding(ruleId, type, newTitle, status, severity, detail, jsonPath, identifiers, sequence);
  }

  public Finding withStatus(final int newStatus) {
    return new Finding(ruleId, type, title, newStatus, severity, detail, jsonPath, identifiers, sequence);
  }

  public Finding inSequence(final List<Integer> positions) {
    return new Finding(ruleId, type, title, status, severity, detail, jsonPath, identifiers, positions);
  }

  public Finding withRuleId(final String id) {
    return new Finding(id, type, title, status, severity, detail, jsonPath, identifiers, sequence);
  }

  public Finding withSeverity(final Severity newSeverity) {
    return new Finding(ruleId, type, title, status, newSeverity, detail, jsonPath, identifiers, sequence);
  }
}
