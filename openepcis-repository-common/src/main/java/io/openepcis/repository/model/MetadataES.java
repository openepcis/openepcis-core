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
package io.openepcis.repository.model;

import io.openepcis.repository.Constants;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class MetadataES {
  /** Key of the capture metadata under which the validation lists SOFT_ERROR entries. */
  public static final String VALIDATION = "validation";

  private List<CapturedByES> capturedBy;
  private Boolean visible;

  /**
   * The SOFT_ERROR entries the validation reported for the event (the event was stored anyway), in
   * the shape of the capture report entries.
   */
  @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY)
  private List<Map<String, Object>> validation;

  public static MetadataES fromMetadata(Map<String, Object> metadata, String captureID, OffsetDateTime recordTime) {
    final CapturedByES primary = CapturedByES.fromMetadata(metadata, captureID, recordTime);
    final MetadataES metadataES = MetadataES.builder().capturedBy(capturedBy(metadata, primary, captureID, recordTime)).visible(getVisibility(metadata)).build();
    metadataES.setValidation(validationOf(metadata));
    return metadataES;
  }

  /**
   * The capturing party first, then every group the capture names under {@link
   * Constants#ALSO_CAPTURED_FOR} that is not the capturer's own.
   */
  private static List<CapturedByES> capturedBy(Map<String, Object> metadata, CapturedByES primary, String captureID, OffsetDateTime recordTime) {
    final Object also = metadata == null ? null : metadata.get(Constants.ALSO_CAPTURED_FOR);
    if (!(also instanceof List<?> entries) || entries.isEmpty()) {
      return Collections.singletonList(primary);
    }
    final List<CapturedByES> capturedBy = new java.util.ArrayList<>(List.of(primary));
    for (final Object entry : entries) {
      if (entry instanceof Map<?, ?> named
          && named.get(Constants.DEFAULT_GROUP) instanceof String group
          && !group.isBlank()
          && capturedBy.stream().noneMatch(c -> group.equalsIgnoreCase(c.getDefaultGroup()))) {
        final Object user = named.get(Constants.USER_ID);
        capturedBy.add(CapturedByES.builder().captureID(captureID).timestamp(recordTime)
            .userID(user instanceof String u ? u : primary.getUserID()).defaultGroup(group).build());
      }
    }
    return capturedBy;
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> validationOf(Map<String, Object> metadata) {
    final Object listed = metadata == null ? null : metadata.get(VALIDATION);
    if (!(listed instanceof List<?> entries) || entries.isEmpty()) {
      return null;
    }
    return entries.stream().filter(Map.class::isInstance).map(entry -> (Map<String, Object>) entry).toList();
  }

  public List<Map<String, Object>> getValidation() {
    return validation;
  }

  public void setValidation(List<Map<String, Object>> validation) {
    this.validation = validation;
  }

  private static boolean getVisibility(Map<String, Object> metadata) {
    return Optional.ofNullable(metadata.get(Constants.VISIBLE)).map(value -> (Boolean) value).orElse(false);
  }


  public static class MetadataESBuilder {
    private List<CapturedByES> capturedBy;
    private Boolean visible;

    MetadataESBuilder() {
    }

    /**
     * @return {@code this}.
     */
    public MetadataES.MetadataESBuilder capturedBy(List<CapturedByES> capturedBy) {
      this.capturedBy = capturedBy;
      return this;
    }

    /**
     * @return {@code this}.
     */
    public MetadataES.MetadataESBuilder visible(Boolean visible) {
      this.visible = visible;
      return this;
    }

    public MetadataES build() {
      return new MetadataES(this.capturedBy, this.visible);
    }

    @Override
    public String toString() {
      return "MetadataES.MetadataESBuilder(capturedBy=" + this.capturedBy + ", visible=" + this.visible + ")";
    }
  }

  public static MetadataES.MetadataESBuilder builder() {
    return new MetadataES.MetadataESBuilder();
  }

  public List<CapturedByES> getCapturedBy() {
    return this.capturedBy;
  }

  public Boolean getVisible() {
    return this.visible;
  }

  public void setCapturedBy(List<CapturedByES> capturedBy) {
    this.capturedBy = capturedBy;
  }

  public void setVisible(Boolean visible) {
    this.visible = visible;
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) return true;
    if (!(o instanceof MetadataES)) return false;
    MetadataES other = (MetadataES) o;
    if (!other.canEqual((Object) this)) return false;
    Object this$visible = this.getVisible();
    Object other$visible = other.getVisible();
    if (this$visible == null ? other$visible != null : !this$visible.equals(other$visible)) return false;
    Object this$capturedBy = this.getCapturedBy();
    Object other$capturedBy = other.getCapturedBy();
    if (this$capturedBy == null ? other$capturedBy != null : !this$capturedBy.equals(other$capturedBy)) return false;
    return true;
  }

  protected boolean canEqual(Object other) {
    return other instanceof MetadataES;
  }

  @Override
  public int hashCode() {
    int PRIME = 59;
    int result = 1;
    Object $visible = this.getVisible();
    result = result * PRIME + ($visible == null ? 43 : $visible.hashCode());
    Object $capturedBy = this.getCapturedBy();
    result = result * PRIME + ($capturedBy == null ? 43 : $capturedBy.hashCode());
    return result;
  }

  @Override
  public String toString() {
    return "MetadataES(capturedBy=" + this.getCapturedBy() + ", visible=" + this.getVisible() + ", validation=" + this.getValidation() + ")";
  }

  public MetadataES(List<CapturedByES> capturedBy, Boolean visible) {
    this.capturedBy = capturedBy;
    this.visible = visible;
  }

  public MetadataES() {
  }
}
