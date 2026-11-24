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

import io.openepcis.model.dto.Tracer;

public class EventCountMessage extends Tracer {
  private Integer count;

  public Integer getCount() {
    return this.count;
  }

  public void setCount(Integer count) {
    this.count = count;
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) return true;
    if (!(o instanceof EventCountMessage)) return false;
    EventCountMessage other = (EventCountMessage) o;
    if (!other.canEqual((Object) this)) return false;
    Object this$count = this.getCount();
    Object other$count = other.getCount();
    if (this$count == null ? other$count != null : !this$count.equals(other$count)) return false;
    return true;
  }

  protected boolean canEqual(Object other) {
    return other instanceof EventCountMessage;
  }

  @Override
  public int hashCode() {
    int PRIME = 59;
    int result = 1;
    Object $count = this.getCount();
    result = result * PRIME + ($count == null ? 43 : $count.hashCode());
    return result;
  }

  @Override
  public String toString() {
    return "EventCountMessage(count=" + this.getCount() + ")";
  }

  public EventCountMessage(Integer count) {
    this.count = count;
  }

  public EventCountMessage() {
  }
}
