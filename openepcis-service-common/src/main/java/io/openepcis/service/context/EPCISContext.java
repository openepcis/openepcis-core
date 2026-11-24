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
package io.openepcis.service.context;

import io.openepcis.constants.CBVVersion;
import io.openepcis.constants.EPCISFormat;
import io.openepcis.constants.EPCISVersion;
import io.openepcis.model.epcis.format.FormatPreference;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public interface EPCISContext {
  String getContentType();

  EPCISVersion getEpcisVersion();

  EPCISFormat getEpcisFormat();

  EPCISVersion getEpcisVersionMin();

  EPCISVersion getEpcisVersionMax();

  CBVVersion getCbvVersion();

  CBVVersion getCbvVersionMin();

  CBVVersion getCbvVersionMax();

  void setEpcisVersionMin(EPCISVersion epcisVersionMin);

  void setEpcisVersionMax(EPCISVersion epcisVersionMax);

  void setCbvVersion(CBVVersion cbvVersion);

  void setCbvVersionMin(CBVVersion cbvVersionMin);

  void setCbvVersionMax(CBVVersion cbvVersionMax);

  FormatPreference getFormatPreference();

  void setFormatPreference(FormatPreference formatPreference);

  AtomicReference<Optional<String>> getGs1Extensions();
}
