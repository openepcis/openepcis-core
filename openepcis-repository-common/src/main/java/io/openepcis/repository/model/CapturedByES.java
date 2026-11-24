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

import static com.fasterxml.jackson.annotation.JsonFormat.Feature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE;
import static com.fasterxml.jackson.annotation.JsonFormat.Feature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.openepcis.model.epcis.modifier.OffsetDateTimeSerializer;
import io.openepcis.repository.Constants;
import java.time.OffsetDateTime;
import java.util.Map;

public class CapturedByES {
  private String captureID;
  @JsonSerialize(using = OffsetDateTimeSerializer.class)
  @JsonFormat(without = {ADJUST_DATES_TO_CONTEXT_TIME_ZONE, WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS})
  private OffsetDateTime timestamp;
  private String userID;
  private String defaultGroup;

  public static CapturedByES fromMetadata(Map<String, Object> metadata, String captureID, OffsetDateTime recordTime) {
    final Object capturedByObj = metadata.get(Constants.CAPTURED_BY);
    if (capturedByObj instanceof CapturedByES existing) {
      return CapturedByES.builder().captureID(captureID).timestamp(recordTime).userID(existing.getUserID()).defaultGroup(existing.getDefaultGroup()).build();
    }
    @SuppressWarnings("unchecked")
    final Map<String, Object> capturedBy = (Map<String, Object>) capturedByObj;
    return CapturedByES.builder().captureID(captureID).timestamp(recordTime).userID((String) capturedBy.get(Constants.USER_ID)).defaultGroup((String) capturedBy.get(Constants.DEFAULT_GROUP)).build();
  }


  public static class CapturedByESBuilder {
    private String captureID;
    private OffsetDateTime timestamp;
    private String userID;
    private String defaultGroup;

    CapturedByESBuilder() {
    }

    /**
     * @return {@code this}.
     */
    public CapturedByES.CapturedByESBuilder captureID(String captureID) {
      this.captureID = captureID;
      return this;
    }

    /**
     * @return {@code this}.
     */
    @JsonFormat(without = {ADJUST_DATES_TO_CONTEXT_TIME_ZONE, WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS})
    public CapturedByES.CapturedByESBuilder timestamp(OffsetDateTime timestamp) {
      this.timestamp = timestamp;
      return this;
    }

    /**
     * @return {@code this}.
     */
    public CapturedByES.CapturedByESBuilder userID(String userID) {
      this.userID = userID;
      return this;
    }

    /**
     * @return {@code this}.
     */
    public CapturedByES.CapturedByESBuilder defaultGroup(String defaultGroup) {
      this.defaultGroup = defaultGroup;
      return this;
    }

    public CapturedByES build() {
      return new CapturedByES(this.captureID, this.timestamp, this.userID, this.defaultGroup);
    }

    @Override
    public String toString() {
      return "CapturedByES.CapturedByESBuilder(captureID=" + this.captureID + ", timestamp=" + this.timestamp + ", userID=" + this.userID + ", defaultGroup=" + this.defaultGroup + ")";
    }
  }

  public static CapturedByES.CapturedByESBuilder builder() {
    return new CapturedByES.CapturedByESBuilder();
  }

  public String getCaptureID() {
    return this.captureID;
  }

  public OffsetDateTime getTimestamp() {
    return this.timestamp;
  }

  public String getUserID() {
    return this.userID;
  }

  public String getDefaultGroup() {
    return this.defaultGroup;
  }

  public void setCaptureID(String captureID) {
    this.captureID = captureID;
  }

  public void setTimestamp(OffsetDateTime timestamp) {
    this.timestamp = timestamp;
  }

  public void setUserID(String userID) {
    this.userID = userID;
  }

  public void setDefaultGroup(String defaultGroup) {
    this.defaultGroup = defaultGroup;
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) return true;
    if (!(o instanceof CapturedByES)) return false;
    CapturedByES other = (CapturedByES) o;
    if (!other.canEqual((Object) this)) return false;
    Object this$captureID = this.getCaptureID();
    Object other$captureID = other.getCaptureID();
    if (this$captureID == null ? other$captureID != null : !this$captureID.equals(other$captureID)) return false;
    Object this$timestamp = this.getTimestamp();
    Object other$timestamp = other.getTimestamp();
    if (this$timestamp == null ? other$timestamp != null : !this$timestamp.equals(other$timestamp)) return false;
    Object this$userID = this.getUserID();
    Object other$userID = other.getUserID();
    if (this$userID == null ? other$userID != null : !this$userID.equals(other$userID)) return false;
    Object this$defaultGroup = this.getDefaultGroup();
    Object other$defaultGroup = other.getDefaultGroup();
    if (this$defaultGroup == null ? other$defaultGroup != null : !this$defaultGroup.equals(other$defaultGroup)) return false;
    return true;
  }

  protected boolean canEqual(Object other) {
    return other instanceof CapturedByES;
  }

  @Override
  public int hashCode() {
    int PRIME = 59;
    int result = 1;
    Object $captureID = this.getCaptureID();
    result = result * PRIME + ($captureID == null ? 43 : $captureID.hashCode());
    Object $timestamp = this.getTimestamp();
    result = result * PRIME + ($timestamp == null ? 43 : $timestamp.hashCode());
    Object $userID = this.getUserID();
    result = result * PRIME + ($userID == null ? 43 : $userID.hashCode());
    Object $defaultGroup = this.getDefaultGroup();
    result = result * PRIME + ($defaultGroup == null ? 43 : $defaultGroup.hashCode());
    return result;
  }

  @Override
  public String toString() {
    return "CapturedByES(captureID=" + this.getCaptureID() + ", timestamp=" + this.getTimestamp() + ", userID=" + this.getUserID() + ", defaultGroup=" + this.getDefaultGroup() + ")";
  }

  public CapturedByES(String captureID, OffsetDateTime timestamp, String userID, String defaultGroup) {
    this.captureID = captureID;
    this.timestamp = timestamp;
    this.userID = userID;
    this.defaultGroup = defaultGroup;
  }

  public CapturedByES() {
  }
}
