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
package io.openepcis.query.context;

public class DefaultSubscriptionContext implements SubscriptionContext {
  private String subscribedBy;
  private String userId;
  private String defaultGroup;

  public String getSubscribedBy() {
    return this.subscribedBy;
  }

  public String getUserId() {
    return this.userId;
  }

  public String getDefaultGroup() {
    return this.defaultGroup;
  }

  public void setSubscribedBy(String subscribedBy) {
    this.subscribedBy = subscribedBy;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  public void setDefaultGroup(String defaultGroup) {
    this.defaultGroup = defaultGroup;
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) return true;
    if (!(o instanceof DefaultSubscriptionContext)) return false;
    DefaultSubscriptionContext other = (DefaultSubscriptionContext) o;
    if (!other.canEqual((Object) this)) return false;
    Object this$subscribedBy = this.getSubscribedBy();
    Object other$subscribedBy = other.getSubscribedBy();
    if (this$subscribedBy == null ? other$subscribedBy != null : !this$subscribedBy.equals(other$subscribedBy)) return false;
    Object this$userId = this.getUserId();
    Object other$userId = other.getUserId();
    if (this$userId == null ? other$userId != null : !this$userId.equals(other$userId)) return false;
    Object this$defaultGroup = this.getDefaultGroup();
    Object other$defaultGroup = other.getDefaultGroup();
    if (this$defaultGroup == null ? other$defaultGroup != null : !this$defaultGroup.equals(other$defaultGroup)) return false;
    return true;
  }

  protected boolean canEqual(Object other) {
    return other instanceof DefaultSubscriptionContext;
  }

  @Override
  public int hashCode() {
    int PRIME = 59;
    int result = 1;
    Object $subscribedBy = this.getSubscribedBy();
    result = result * PRIME + ($subscribedBy == null ? 43 : $subscribedBy.hashCode());
    Object $userId = this.getUserId();
    result = result * PRIME + ($userId == null ? 43 : $userId.hashCode());
    Object $defaultGroup = this.getDefaultGroup();
    result = result * PRIME + ($defaultGroup == null ? 43 : $defaultGroup.hashCode());
    return result;
  }

  @Override
  public String toString() {
    return "DefaultSubscriptionContext(subscribedBy=" + this.getSubscribedBy() + ", userId=" + this.getUserId() + ", defaultGroup=" + this.getDefaultGroup() + ")";
  }

  public DefaultSubscriptionContext(String subscribedBy, String userId, String defaultGroup) {
    this.subscribedBy = subscribedBy;
    this.userId = userId;
    this.defaultGroup = defaultGroup;
  }
}
