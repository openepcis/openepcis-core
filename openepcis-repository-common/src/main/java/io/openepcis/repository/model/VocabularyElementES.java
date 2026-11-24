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
import io.openepcis.model.epcis.VocabularyElement;
import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class VocabularyElementES {
  private URI id;
  private List<AttributeES> attributes;
  private List<URI> children;

  public VocabularyElementES(final VocabularyElement vocabularyElement) {
    this.setId(vocabularyElement.getId());
    this.setAttributes(vocabularyElement.getAttributes().stream().map(AttributeES::new).collect(Collectors.toList()));
    this.setChildren(vocabularyElement.getChildren());
  }

  @JsonIgnore
  public VocabularyElement getCoreModel() {
    return VocabularyElement.builder().id(this.getId()).attributes(CollectionUtils.isNotEmpty(this.getAttributes()) ? this.getAttributes().stream().map(AttributeES::getCoreModel).collect(Collectors.toList()) : null).children(CollectionUtils.isNotEmpty(this.getChildren()) ? this.getChildren() : null).build();
  }

  public URI getId() {
    return this.id;
  }

  public List<AttributeES> getAttributes() {
    return this.attributes;
  }

  public List<URI> getChildren() {
    return this.children;
  }

  public void setId(URI id) {
    this.id = id;
  }

  public void setAttributes(List<AttributeES> attributes) {
    this.attributes = attributes;
  }

  public void setChildren(List<URI> children) {
    this.children = children;
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) return true;
    if (!(o instanceof VocabularyElementES)) return false;
    VocabularyElementES other = (VocabularyElementES) o;
    if (!other.canEqual((Object) this)) return false;
    Object this$id = this.getId();
    Object other$id = other.getId();
    if (this$id == null ? other$id != null : !this$id.equals(other$id)) return false;
    Object this$attributes = this.getAttributes();
    Object other$attributes = other.getAttributes();
    if (this$attributes == null ? other$attributes != null : !this$attributes.equals(other$attributes)) return false;
    Object this$children = this.getChildren();
    Object other$children = other.getChildren();
    if (this$children == null ? other$children != null : !this$children.equals(other$children)) return false;
    return true;
  }

  protected boolean canEqual(Object other) {
    return other instanceof VocabularyElementES;
  }

  @Override
  public int hashCode() {
    int PRIME = 59;
    int result = 1;
    Object $id = this.getId();
    result = result * PRIME + ($id == null ? 43 : $id.hashCode());
    Object $attributes = this.getAttributes();
    result = result * PRIME + ($attributes == null ? 43 : $attributes.hashCode());
    Object $children = this.getChildren();
    result = result * PRIME + ($children == null ? 43 : $children.hashCode());
    return result;
  }

  @Override
  public String toString() {
    return "VocabularyElementES(id=" + this.getId() + ", attributes=" + this.getAttributes() + ", children=" + this.getChildren() + ")";
  }

  public VocabularyElementES(URI id, List<AttributeES> attributes, List<URI> children) {
    this.id = id;
    this.attributes = attributes;
    this.children = children;
  }

  public VocabularyElementES() {
  }
}
