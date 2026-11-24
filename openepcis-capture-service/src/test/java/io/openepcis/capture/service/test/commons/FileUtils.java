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
package io.openepcis.capture.service.test.commons;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class FileUtils {

  private FileUtils() {
    throw new UnsupportedOperationException("Invalid invocation of constructor");
  }

  public static String loadFileContentAsString(String filePath) throws Exception {
    try {
      return Files.readString(Paths.get(FileUtils.class.getResource(filePath).toURI()));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
