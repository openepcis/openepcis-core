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
package io.openepcis.capture.context;

public final class CloudStorageResult {
  private String bucketName;
  private String eTag;
  private String storageKey;
  private String versionId;


  public static class CloudStorageResultBuilder {
    private String bucketName;
    private String eTag;
    private String storageKey;
    private String versionId;

    CloudStorageResultBuilder() {
    }

    /**
     * @return {@code this}.
     */
    public CloudStorageResult.CloudStorageResultBuilder bucketName(String bucketName) {
      this.bucketName = bucketName;
      return this;
    }

    /**
     * @return {@code this}.
     */
    public CloudStorageResult.CloudStorageResultBuilder eTag(String eTag) {
      this.eTag = eTag;
      return this;
    }

    /**
     * @return {@code this}.
     */
    public CloudStorageResult.CloudStorageResultBuilder storageKey(String storageKey) {
      this.storageKey = storageKey;
      return this;
    }

    /**
     * @return {@code this}.
     */
    public CloudStorageResult.CloudStorageResultBuilder versionId(String versionId) {
      this.versionId = versionId;
      return this;
    }

    public CloudStorageResult build() {
      return new CloudStorageResult(this.bucketName, this.eTag, this.storageKey, this.versionId);
    }

    @Override
    public String toString() {
      return "CloudStorageResult.CloudStorageResultBuilder(bucketName=" + this.bucketName + ", eTag=" + this.eTag + ", storageKey=" + this.storageKey + ", versionId=" + this.versionId + ")";
    }
  }

  public static CloudStorageResult.CloudStorageResultBuilder builder() {
    return new CloudStorageResult.CloudStorageResultBuilder();
  }

  public CloudStorageResult(String bucketName, String eTag, String storageKey, String versionId) {
    this.bucketName = bucketName;
    this.eTag = eTag;
    this.storageKey = storageKey;
    this.versionId = versionId;
  }

  public String getBucketName() {
    return this.bucketName;
  }

  public String getETag() {
    return this.eTag;
  }

  public String getStorageKey() {
    return this.storageKey;
  }

  public String getVersionId() {
    return this.versionId;
  }

  @Override
  public String toString() {
    return "CloudStorageResult(bucketName=" + this.getBucketName() + ", eTag=" + this.getETag() + ", storageKey=" + this.getStorageKey() + ", versionId=" + this.getVersionId() + ")";
  }
}
