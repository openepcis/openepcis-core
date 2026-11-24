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

import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import io.openepcis.core.exception.InvalidURIException;
import io.openepcis.core.exception.SchemaValidationException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

public class JsonSchemaLoader {
  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(JsonSchemaLoader.class);

  private JsonSchemaLoader() {
    throw new UnsupportedOperationException("Invalid invocation of constructor");
  }

  public static JsonSchema loadSchema(String pathToSchema) {
    try {
      return loadSchema(new URL(pathToSchema).openConnection().getInputStream());
    } catch (IOException e) {
      throw new InvalidURIException(e.getMessage());
    }
  }

  public static JsonSchema loadSchema(URL url) {
    try {
      final InputStream inputStream = url.openConnection().getInputStream();
      return loadSchema(inputStream, url.toString().substring(0, url.toString().lastIndexOf("/")));
    } catch (IOException e) {
      throw new InvalidURIException(e.getMessage());
    }
  }

  public static JsonSchema loadSchema(InputStream inputStream, String resolutionScope) {
    try {
      JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
      return factory.getSchema(inputStream);
    } catch (Exception e) {
      log.error(e.getMessage());
      throw new SchemaValidationException("Error while loading schema: " + e.getMessage());
    }
  }

  public static JsonSchema loadSchema(InputStream inputStream) {
    return loadSchema(inputStream, null);
  }
}
