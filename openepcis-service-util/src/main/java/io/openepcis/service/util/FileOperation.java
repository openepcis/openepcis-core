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

import io.openepcis.core.exception.ImplementationException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.commons.io.FileUtils;

public class FileOperation {
  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(FileOperation.class);

  private FileOperation() {
    throw new UnsupportedOperationException("Invalid invocation of constructor");
  }

  public static void delete(Path path) {
    try {
      if (Files.exists(path)) {
        Files.delete(path);
      } else {
        log.info("File {} does not exist", path);
      }
    } catch (IOException e) {
      log.error(e.getMessage());
    }
  }

  public static void move(Path path, Path target) {
    try {
      if (Files.notExists(target)) {
        Files.move(path, target);
      }
    } catch (IOException e) {
      log.error(e.getMessage());
      FileUtils.deleteQuietly(path.toFile());
      throw new ImplementationException(e.getMessage(), e);
    }
  }
}
