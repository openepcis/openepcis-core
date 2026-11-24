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
package io.openepcis.validation.rule;

/**
 * How much a finding weighs, in the terms of the SmartHub Connect API: a {@link #HARD_ERROR} keeps
 * the event out of the repository, a {@link #SOFT_ERROR} is reported while the event is stored.
 */
public enum Severity {
  HARD_ERROR,
  SOFT_ERROR;

  public boolean blocks() {
    return this == HARD_ERROR;
  }
}
