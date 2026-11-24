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

@JsonInclude(JsonInclude.Include.NON_NULL)
public class StreamingSubscriptionQueryES {
  private String queryName;
  private Object query;


  public static class StreamingSubscriptionQueryESBuilder {
    private String queryName;
    private Object query;

    StreamingSubscriptionQueryESBuilder() {
    }

    /**
     * @return {@code this}.
     */
    public StreamingSubscriptionQueryES.StreamingSubscriptionQueryESBuilder queryName(String queryName) {
      this.queryName = queryName;
      return this;
    }

    /**
     * @return {@code this}.
     */
    public StreamingSubscriptionQueryES.StreamingSubscriptionQueryESBuilder query(Object query) {
      this.query = query;
      return this;
    }

    public StreamingSubscriptionQueryES build() {
      return new StreamingSubscriptionQueryES(this.queryName, this.query);
    }

    @Override
    public String toString() {
      return "StreamingSubscriptionQueryES.StreamingSubscriptionQueryESBuilder(queryName=" + this.queryName + ", query=" + this.query + ")";
    }
  }

  public static StreamingSubscriptionQueryES.StreamingSubscriptionQueryESBuilder builder() {
    return new StreamingSubscriptionQueryES.StreamingSubscriptionQueryESBuilder();
  }

  public String getQueryName() {
    return this.queryName;
  }

  public Object getQuery() {
    return this.query;
  }

  public void setQueryName(String queryName) {
    this.queryName = queryName;
  }

  public void setQuery(Object query) {
    this.query = query;
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) return true;
    if (!(o instanceof StreamingSubscriptionQueryES)) return false;
    StreamingSubscriptionQueryES other = (StreamingSubscriptionQueryES) o;
    if (!other.canEqual((Object) this)) return false;
    Object this$queryName = this.getQueryName();
    Object other$queryName = other.getQueryName();
    if (this$queryName == null ? other$queryName != null : !this$queryName.equals(other$queryName)) return false;
    Object this$query = this.getQuery();
    Object other$query = other.getQuery();
    if (this$query == null ? other$query != null : !this$query.equals(other$query)) return false;
    return true;
  }

  protected boolean canEqual(Object other) {
    return other instanceof StreamingSubscriptionQueryES;
  }

  @Override
  public int hashCode() {
    int PRIME = 59;
    int result = 1;
    Object $queryName = this.getQueryName();
    result = result * PRIME + ($queryName == null ? 43 : $queryName.hashCode());
    Object $query = this.getQuery();
    result = result * PRIME + ($query == null ? 43 : $query.hashCode());
    return result;
  }

  @Override
  public String toString() {
    return "StreamingSubscriptionQueryES(queryName=" + this.getQueryName() + ", query=" + this.getQuery() + ")";
  }

  public StreamingSubscriptionQueryES() {
  }

  public StreamingSubscriptionQueryES(String queryName, Object query) {
    this.queryName = queryName;
    this.query = query;
  }
}
