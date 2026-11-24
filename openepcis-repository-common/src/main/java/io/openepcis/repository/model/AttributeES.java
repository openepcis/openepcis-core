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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.openepcis.model.epcis.Attribute;
import java.net.URI;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class AttributeES {
  private URI id;
  private Object objectAttribute;
  private String keywordAttribute;
  private Long longAttribute;
  private Double doubleAttribute;

  public AttributeES(final Attribute attribute) {
    this.setId(attribute.getId());
    if (attribute.getAttribute() instanceof Long || attribute.getAttribute() instanceof Integer) this.setLongAttribute(Long.valueOf(attribute.getAttribute().toString()));
     else if (attribute.getAttribute() instanceof Double || attribute.getAttribute() instanceof Float) this.setDoubleAttribute(Double.valueOf(attribute.getAttribute().toString()));
     else if (attribute.getAttribute() instanceof String) this.setKeywordAttribute((String) attribute.getAttribute());
     else this.setObjectAttribute(attribute.getAttribute());
  }

  @JsonIgnore
  public Attribute getCoreModel() {
    return Attribute.builder().id(this.getId()).attribute(getAttribute(this)).build();
  }

  private Object getAttribute(AttributeES attributeES) {
    if (this.getLongAttribute() != null) return longAttribute;
    if (this.getDoubleAttribute() != null) return doubleAttribute;
    if (this.getKeywordAttribute() != null) return keywordAttribute;
    return this.objectAttribute;
  }

  public URI getId() {
    return this.id;
  }

  public Object getObjectAttribute() {
    return this.objectAttribute;
  }

  public String getKeywordAttribute() {
    return this.keywordAttribute;
  }

  public Long getLongAttribute() {
    return this.longAttribute;
  }

  public Double getDoubleAttribute() {
    return this.doubleAttribute;
  }

  public void setId(URI id) {
    this.id = id;
  }

  public void setObjectAttribute(Object objectAttribute) {
    this.objectAttribute = objectAttribute;
  }

  public void setKeywordAttribute(String keywordAttribute) {
    this.keywordAttribute = keywordAttribute;
  }

  public void setLongAttribute(Long longAttribute) {
    this.longAttribute = longAttribute;
  }

  public void setDoubleAttribute(Double doubleAttribute) {
    this.doubleAttribute = doubleAttribute;
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) return true;
    if (!(o instanceof AttributeES)) return false;
    AttributeES other = (AttributeES) o;
    if (!other.canEqual((Object) this)) return false;
    Object this$longAttribute = this.getLongAttribute();
    Object other$longAttribute = other.getLongAttribute();
    if (this$longAttribute == null ? other$longAttribute != null : !this$longAttribute.equals(other$longAttribute)) return false;
    Object this$doubleAttribute = this.getDoubleAttribute();
    Object other$doubleAttribute = other.getDoubleAttribute();
    if (this$doubleAttribute == null ? other$doubleAttribute != null : !this$doubleAttribute.equals(other$doubleAttribute)) return false;
    Object this$id = this.getId();
    Object other$id = other.getId();
    if (this$id == null ? other$id != null : !this$id.equals(other$id)) return false;
    Object this$objectAttribute = this.getObjectAttribute();
    Object other$objectAttribute = other.getObjectAttribute();
    if (this$objectAttribute == null ? other$objectAttribute != null : !this$objectAttribute.equals(other$objectAttribute)) return false;
    Object this$keywordAttribute = this.getKeywordAttribute();
    Object other$keywordAttribute = other.getKeywordAttribute();
    if (this$keywordAttribute == null ? other$keywordAttribute != null : !this$keywordAttribute.equals(other$keywordAttribute)) return false;
    return true;
  }

  protected boolean canEqual(Object other) {
    return other instanceof AttributeES;
  }

  @Override
  public int hashCode() {
    int PRIME = 59;
    int result = 1;
    Object $longAttribute = this.getLongAttribute();
    result = result * PRIME + ($longAttribute == null ? 43 : $longAttribute.hashCode());
    Object $doubleAttribute = this.getDoubleAttribute();
    result = result * PRIME + ($doubleAttribute == null ? 43 : $doubleAttribute.hashCode());
    Object $id = this.getId();
    result = result * PRIME + ($id == null ? 43 : $id.hashCode());
    Object $objectAttribute = this.getObjectAttribute();
    result = result * PRIME + ($objectAttribute == null ? 43 : $objectAttribute.hashCode());
    Object $keywordAttribute = this.getKeywordAttribute();
    result = result * PRIME + ($keywordAttribute == null ? 43 : $keywordAttribute.hashCode());
    return result;
  }

  @Override
  public String toString() {
    return "AttributeES(id=" + this.getId() + ", objectAttribute=" + this.getObjectAttribute() + ", keywordAttribute=" + this.getKeywordAttribute() + ", longAttribute=" + this.getLongAttribute() + ", doubleAttribute=" + this.getDoubleAttribute() + ")";
  }

  public AttributeES(URI id, Object objectAttribute, String keywordAttribute, Long longAttribute, Double doubleAttribute) {
    this.id = id;
    this.objectAttribute = objectAttribute;
    this.keywordAttribute = keywordAttribute;
    this.longAttribute = longAttribute;
    this.doubleAttribute = doubleAttribute;
  }

  public AttributeES() {
  }
}
