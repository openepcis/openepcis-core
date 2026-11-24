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
import io.openepcis.model.epcis.VocabularyElements;
import java.util.List;
import java.util.stream.Collectors;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class VocabularyElementsES {
  private List<VocabularyElementES> vocabularyElementList;

  public VocabularyElementsES(final VocabularyElements vocabularyElements) {
    this.setVocabularyElementList(vocabularyElements.getVocabularyElementList().stream().map(vocabularyElement -> new VocabularyElementES(vocabularyElement)).collect(Collectors.toList()));
  }

  @JsonIgnore
  public VocabularyElements getCoreModel() {
    return VocabularyElements.builder().vocabularyElementList(this.getVocabularyElementList().stream().map(VocabularyElementES::getCoreModel).collect(Collectors.toList())).build();
  }

  public List<VocabularyElementES> getVocabularyElementList() {
    return this.vocabularyElementList;
  }

  public void setVocabularyElementList(List<VocabularyElementES> vocabularyElementList) {
    this.vocabularyElementList = vocabularyElementList;
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) return true;
    if (!(o instanceof VocabularyElementsES)) return false;
    VocabularyElementsES other = (VocabularyElementsES) o;
    if (!other.canEqual((Object) this)) return false;
    Object this$vocabularyElementList = this.getVocabularyElementList();
    Object other$vocabularyElementList = other.getVocabularyElementList();
    if (this$vocabularyElementList == null ? other$vocabularyElementList != null : !this$vocabularyElementList.equals(other$vocabularyElementList)) return false;
    return true;
  }

  protected boolean canEqual(Object other) {
    return other instanceof VocabularyElementsES;
  }

  @Override
  public int hashCode() {
    int PRIME = 59;
    int result = 1;
    Object $vocabularyElementList = this.getVocabularyElementList();
    result = result * PRIME + ($vocabularyElementList == null ? 43 : $vocabularyElementList.hashCode());
    return result;
  }

  @Override
  public String toString() {
    return "VocabularyElementsES(vocabularyElementList=" + this.getVocabularyElementList() + ")";
  }

  public VocabularyElementsES(List<VocabularyElementES> vocabularyElementList) {
    this.vocabularyElementList = vocabularyElementList;
  }

  public VocabularyElementsES() {
  }
}
