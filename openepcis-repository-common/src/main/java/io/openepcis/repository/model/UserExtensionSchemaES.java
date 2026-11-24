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

import com.fasterxml.jackson.annotation.JsonInclude;
import io.openepcis.model.epcis.UserExtensionSchema;
import java.time.OffsetDateTime;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserExtensionSchemaES {
  private String id;
  private String namespace;
  private String jsonSchemaS3Key;
  private String jsonSchemaUrl;
  private String defaultPrefix;
  private OffsetDateTime createdAt;
  private OffsetDateTime updatedAt;
  private String createdBy;
  private String updatedBy;
  private MetadataES metadata;
  private String jsonldContextUrl;

  public UserExtensionSchemaES(UserExtensionSchema userExtensionSchema, String defaultGroup) {
    this.id = userExtensionSchema.getId();
    this.namespace = userExtensionSchema.getNamespace();
    this.jsonSchemaS3Key = userExtensionSchema.getJsonSchemaS3Key();
    this.jsonSchemaUrl = userExtensionSchema.getJsonSchemaUrl();
    this.defaultPrefix = userExtensionSchema.getDefaultPrefix();
    this.createdAt = userExtensionSchema.getCreatedAt();
    this.createdBy = userExtensionSchema.getCreatedBy();
    this.updatedAt = userExtensionSchema.getUpdatedAt();
    this.updatedBy = userExtensionSchema.getUpdatedBy();
    this.jsonldContextUrl = userExtensionSchema.getJsonldContextUrl();
    this.setMetadata(MetadataES.builder().capturedBy(List.of(CapturedByES.builder().timestamp(userExtensionSchema.getCreatedAt()).userID(userExtensionSchema.getCreatedBy()).defaultGroup(defaultGroup).build())).build());
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) return true;
    if (!(o instanceof UserExtensionSchemaES)) return false;
    UserExtensionSchemaES other = (UserExtensionSchemaES) o;
    if (!other.canEqual((Object) this)) return false;
    Object this$id = this.getId();
    Object other$id = other.getId();
    if (this$id == null ? other$id != null : !this$id.equals(other$id)) return false;
    Object this$namespace = this.getNamespace();
    Object other$namespace = other.getNamespace();
    if (this$namespace == null ? other$namespace != null : !this$namespace.equals(other$namespace)) return false;
    Object this$jsonSchemaS3Key = this.getJsonSchemaS3Key();
    Object other$jsonSchemaS3Key = other.getJsonSchemaS3Key();
    if (this$jsonSchemaS3Key == null ? other$jsonSchemaS3Key != null : !this$jsonSchemaS3Key.equals(other$jsonSchemaS3Key)) return false;
    Object this$jsonSchemaUrl = this.getJsonSchemaUrl();
    Object other$jsonSchemaUrl = other.getJsonSchemaUrl();
    if (this$jsonSchemaUrl == null ? other$jsonSchemaUrl != null : !this$jsonSchemaUrl.equals(other$jsonSchemaUrl)) return false;
    Object this$defaultPrefix = this.getDefaultPrefix();
    Object other$defaultPrefix = other.getDefaultPrefix();
    if (this$defaultPrefix == null ? other$defaultPrefix != null : !this$defaultPrefix.equals(other$defaultPrefix)) return false;
    Object this$createdAt = this.getCreatedAt();
    Object other$createdAt = other.getCreatedAt();
    if (this$createdAt == null ? other$createdAt != null : !this$createdAt.equals(other$createdAt)) return false;
    Object this$updatedAt = this.getUpdatedAt();
    Object other$updatedAt = other.getUpdatedAt();
    if (this$updatedAt == null ? other$updatedAt != null : !this$updatedAt.equals(other$updatedAt)) return false;
    Object this$createdBy = this.getCreatedBy();
    Object other$createdBy = other.getCreatedBy();
    if (this$createdBy == null ? other$createdBy != null : !this$createdBy.equals(other$createdBy)) return false;
    Object this$updatedBy = this.getUpdatedBy();
    Object other$updatedBy = other.getUpdatedBy();
    if (this$updatedBy == null ? other$updatedBy != null : !this$updatedBy.equals(other$updatedBy)) return false;
    Object this$metadata = this.getMetadata();
    Object other$metadata = other.getMetadata();
    if (this$metadata == null ? other$metadata != null : !this$metadata.equals(other$metadata)) return false;
    Object this$jsonldContextUrl = this.getJsonldContextUrl();
    Object other$jsonldContextUrl = other.getJsonldContextUrl();
    if (this$jsonldContextUrl == null ? other$jsonldContextUrl != null : !this$jsonldContextUrl.equals(other$jsonldContextUrl)) return false;
    return true;
  }

  protected boolean canEqual(Object other) {
    return other instanceof UserExtensionSchemaES;
  }

  @Override
  public int hashCode() {
    int PRIME = 59;
    int result = 1;
    Object $id = this.getId();
    result = result * PRIME + ($id == null ? 43 : $id.hashCode());
    Object $namespace = this.getNamespace();
    result = result * PRIME + ($namespace == null ? 43 : $namespace.hashCode());
    Object $jsonSchemaS3Key = this.getJsonSchemaS3Key();
    result = result * PRIME + ($jsonSchemaS3Key == null ? 43 : $jsonSchemaS3Key.hashCode());
    Object $jsonSchemaUrl = this.getJsonSchemaUrl();
    result = result * PRIME + ($jsonSchemaUrl == null ? 43 : $jsonSchemaUrl.hashCode());
    Object $defaultPrefix = this.getDefaultPrefix();
    result = result * PRIME + ($defaultPrefix == null ? 43 : $defaultPrefix.hashCode());
    Object $createdAt = this.getCreatedAt();
    result = result * PRIME + ($createdAt == null ? 43 : $createdAt.hashCode());
    Object $updatedAt = this.getUpdatedAt();
    result = result * PRIME + ($updatedAt == null ? 43 : $updatedAt.hashCode());
    Object $createdBy = this.getCreatedBy();
    result = result * PRIME + ($createdBy == null ? 43 : $createdBy.hashCode());
    Object $updatedBy = this.getUpdatedBy();
    result = result * PRIME + ($updatedBy == null ? 43 : $updatedBy.hashCode());
    Object $metadata = this.getMetadata();
    result = result * PRIME + ($metadata == null ? 43 : $metadata.hashCode());
    Object $jsonldContextUrl = this.getJsonldContextUrl();
    result = result * PRIME + ($jsonldContextUrl == null ? 43 : $jsonldContextUrl.hashCode());
    return result;
  }

  public String getId() {
    return this.id;
  }

  public String getNamespace() {
    return this.namespace;
  }

  public String getJsonSchemaS3Key() {
    return this.jsonSchemaS3Key;
  }

  public String getJsonSchemaUrl() {
    return this.jsonSchemaUrl;
  }

  public String getDefaultPrefix() {
    return this.defaultPrefix;
  }

  public OffsetDateTime getCreatedAt() {
    return this.createdAt;
  }

  public OffsetDateTime getUpdatedAt() {
    return this.updatedAt;
  }

  public String getCreatedBy() {
    return this.createdBy;
  }

  public String getUpdatedBy() {
    return this.updatedBy;
  }

  public MetadataES getMetadata() {
    return this.metadata;
  }

  public String getJsonldContextUrl() {
    return this.jsonldContextUrl;
  }

  public void setId(String id) {
    this.id = id;
  }

  public void setNamespace(String namespace) {
    this.namespace = namespace;
  }

  public void setJsonSchemaS3Key(String jsonSchemaS3Key) {
    this.jsonSchemaS3Key = jsonSchemaS3Key;
  }

  public void setJsonSchemaUrl(String jsonSchemaUrl) {
    this.jsonSchemaUrl = jsonSchemaUrl;
  }

  public void setDefaultPrefix(String defaultPrefix) {
    this.defaultPrefix = defaultPrefix;
  }

  public void setCreatedAt(OffsetDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public void setUpdatedAt(OffsetDateTime updatedAt) {
    this.updatedAt = updatedAt;
  }

  public void setCreatedBy(String createdBy) {
    this.createdBy = createdBy;
  }

  public void setUpdatedBy(String updatedBy) {
    this.updatedBy = updatedBy;
  }

  public void setMetadata(MetadataES metadata) {
    this.metadata = metadata;
  }

  public void setJsonldContextUrl(String jsonldContextUrl) {
    this.jsonldContextUrl = jsonldContextUrl;
  }

  @Override
  public String toString() {
    return "UserExtensionSchemaES(id=" + this.getId() + ", namespace=" + this.getNamespace() + ", jsonSchemaS3Key=" + this.getJsonSchemaS3Key() + ", jsonSchemaUrl=" + this.getJsonSchemaUrl() + ", defaultPrefix=" + this.getDefaultPrefix() + ", createdAt=" + this.getCreatedAt() + ", updatedAt=" + this.getUpdatedAt() + ", createdBy=" + this.getCreatedBy() + ", updatedBy=" + this.getUpdatedBy() + ", metadata=" + this.getMetadata() + ", jsonldContextUrl=" + this.getJsonldContextUrl() + ")";
  }

  public UserExtensionSchemaES(String id, String namespace, String jsonSchemaS3Key, String jsonSchemaUrl, String defaultPrefix, OffsetDateTime createdAt, OffsetDateTime updatedAt, String createdBy, String updatedBy, MetadataES metadata, String jsonldContextUrl) {
    this.id = id;
    this.namespace = namespace;
    this.jsonSchemaS3Key = jsonSchemaS3Key;
    this.jsonSchemaUrl = jsonSchemaUrl;
    this.defaultPrefix = defaultPrefix;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
    this.createdBy = createdBy;
    this.updatedBy = updatedBy;
    this.metadata = metadata;
    this.jsonldContextUrl = jsonldContextUrl;
  }

  public UserExtensionSchemaES() {
  }
}
