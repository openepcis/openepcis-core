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
package io.openepcis.capture.spi;

import com.fasterxml.jackson.databind.JsonNode;
import io.smallrye.mutiny.Uni;

/**
 * Called after an event was stored for the first time (not for duplicates, replays or rejected
 * events). Its failure never fails the capture.
 */
public interface StoredEventListener {

  Uni<Void> stored(JsonNode event, String captureId, String tenant);
}
