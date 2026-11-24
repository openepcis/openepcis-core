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

import io.openepcis.service.context.DefaultEPCISContext;
import io.openepcis.service.context.EPCISContext;
import java.util.Map;
import java.util.Set;

public class DefaultQueryContext extends DefaultEPCISContext implements QueryContext {
  Map<String, Object> params;
  Set<String> eventAccessRoles;

  public DefaultQueryContext(EPCISContext ctx, Map<String, Object> params) {
    super(ctx);
    super.gs1Extensions.set(ctx.getGs1Extensions().get());
    this.params = params;
  }

  public Map<String, Object> getParams() {
    return this.params;
  }

  public Set<String> getEventAccessRoles() {
    return this.eventAccessRoles;
  }

  public void setParams(Map<String, Object> params) {
    this.params = params;
  }

  public void setEventAccessRoles(Set<String> eventAccessRoles) {
    this.eventAccessRoles = eventAccessRoles;
  }

  @Override
  public String toString() {
    return "DefaultQueryContext(params=" + this.getParams() + ", eventAccessRoles=" + this.getEventAccessRoles() + ")";
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) return true;
    if (!(o instanceof DefaultQueryContext)) return false;
    DefaultQueryContext other = (DefaultQueryContext) o;
    if (!other.canEqual((Object) this)) return false;
    if (!super.equals(o)) return false;
    Object this$params = this.getParams();
    Object other$params = other.getParams();
    if (this$params == null ? other$params != null : !this$params.equals(other$params)) return false;
    Object this$eventAccessRoles = this.getEventAccessRoles();
    Object other$eventAccessRoles = other.getEventAccessRoles();
    if (this$eventAccessRoles == null ? other$eventAccessRoles != null : !this$eventAccessRoles.equals(other$eventAccessRoles)) return false;
    return true;
  }

  protected boolean canEqual(Object other) {
    return other instanceof DefaultQueryContext;
  }

  @Override
  public int hashCode() {
    int PRIME = 59;
    int result = super.hashCode();
    Object $params = this.getParams();
    result = result * PRIME + ($params == null ? 43 : $params.hashCode());
    Object $eventAccessRoles = this.getEventAccessRoles();
    result = result * PRIME + ($eventAccessRoles == null ? 43 : $eventAccessRoles.hashCode());
    return result;
  }
}
