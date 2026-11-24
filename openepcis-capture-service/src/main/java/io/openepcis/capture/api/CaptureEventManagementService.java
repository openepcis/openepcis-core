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
package io.openepcis.capture.api;

import io.openepcis.capture.context.CaptureContext;
import io.openepcis.constants.CBVVersion;
import io.openepcis.constants.EPCISFormat;
import io.openepcis.constants.EPCISVersion;
import io.openepcis.model.epcis.format.CBVFormat;
import io.openepcis.model.epcis.format.EPCFormat;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public interface CaptureEventManagementService {

  Stream<CaptureEventService> getCaptureEventServices();

  default Optional<CaptureEventService> getCaptureEventService(
      EPCISFormat format, EPCISVersion version) {
    return getCaptureEventServices().filter(c -> c.isSupported(format, version)).findFirst();
  }

  default Optional<CaptureEventService> getCaptureEventService(CaptureContext ctx) {
    return getCaptureEventServices()
        .filter(c -> c.isSupported(ctx.getEpcisFormat(), ctx.getEpcisVersion()))
        .findFirst();
  }

  default List<EPCISVersion> getEPCISVersions() {
    return getCaptureEventServices()
        .map(c -> c.getEPCISVersion())
        .collect(Collectors.toCollection(HashSet::new))
        .stream()
        .sorted()
        .toList();
  }

  default Collection<EPCISFormat> getSupportedEPCISFormats() {
    return getCaptureEventServices()
        .flatMap(c -> c.getSupportedEPCISFormats().stream())
        .collect(Collectors.toCollection(HashSet::new));
  }

  default EPCISVersion getEPCISVersionMin() {
    final List<EPCISVersion> versions = getEPCISVersions();
    return versions.get(0);
  }

  default EPCISVersion getEPCISVersionMax() {
    final List<EPCISVersion> versions = getEPCISVersions();
    return versions.get(versions.size() - 1);
  }

  default EPCISVersion getEPCISVersion() {
    return EPCISVersion.VERSION_2_0_0;
  }

  default <T> Optional<T> validate(Class<T> type, Optional<String> value) {
    if (EPCISVersion.class.equals(type)) {
      if (value.isPresent()) {
        final Optional<EPCISVersion> epcisVersion = EPCISVersion.fromString(value.get());
        if (epcisVersion.isEmpty() || !getEPCISVersions().contains(epcisVersion.get())) {
          return Optional.empty();
        }
        return (Optional<T>) epcisVersion;
      }
      return Optional.of((T) EPCISVersion.VERSION_2_0_0);
    }
    if (CBVVersion.class.equals(type)) {
      if (value.isPresent()) {
        return (Optional<T>) CBVVersion.fromString(value.get());
      }
      return Optional.of((T) CBVVersion.VERSION_1_2_2);
    }
    if (CBVFormat.class.equals(type)) {
      if (value.isPresent()) {
        return (Optional<T>) CBVFormat.fromString(value.get());
      }
      return Optional.of((T) CBVFormat.No_Preference);
    }
    if (EPCFormat.class.equals(type)) {
      if (value.isPresent()) {
        return (Optional<T>) EPCFormat.fromString(value.get());
      }
      return Optional.of((T) EPCFormat.No_Preference);
    }
    return Optional.empty();
  }
}
