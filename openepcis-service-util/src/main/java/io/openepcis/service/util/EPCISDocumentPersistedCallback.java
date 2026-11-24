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
package io.openepcis.service.util;

import io.smallrye.mutiny.subscription.UniEmitter;
import java.util.function.Consumer;

public class EPCISDocumentPersistedCallback implements Consumer<UniEmitter<? super String>> {
  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(EPCISDocumentPersistedCallback.class);
  private UniEmitter<? super String> emitter;

  @Override
  public void accept(UniEmitter<? super String> emitter) {
    this.emitter = emitter;
  }

  public void complete(String s) {
    emitter.complete(s);
  }

  public void fail(Throwable throwable) {
    emitter.fail(throwable);
  }
}
