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
package io.openepcis.query.util;

import io.openepcis.model.epcis.exception.ExceptionMessages;
import java.io.IOException;
import java.io.InputStreamReader;
import javax.script.*;
import stormpot.BasePoolable;
import stormpot.Slot;

public class PoolableScriptEngine extends BasePoolable {
  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PoolableScriptEngine.class);
  private final ScriptEngine scriptEngine;
  private static final ScriptEngineFactory SCRIPT_ENGINE_FACTORY;

  static {
    SCRIPT_ENGINE_FACTORY = new ScriptEngineManager().getEngineByName("Graal.js").getFactory();
  }

  public PoolableScriptEngine(Slot slot) {
    super(slot);
    var engine = SCRIPT_ENGINE_FACTORY.getScriptEngine();
    if (engine == null) {
      throw new RuntimeException("unable to find Graal.js ScriptEngine");
    }
    try (InputStreamReader inputStreamReader = new InputStreamReader(UnitConverter.class.getResourceAsStream("/script.js"))) {
      engine.eval(inputStreamReader);
      engine.eval("var scriptObj = new script()");
    } catch (NullPointerException e) {
      log.error(ExceptionMessages.FILE_MISSING, e);
      throw new RuntimeException(e);
    } catch (ScriptException e) {
      log.error(ExceptionMessages.ERROR_PROCESSING_JAVASCRIPT_ENGINE, e);
      throw new RuntimeException(e);
    } catch (IOException e) {
      log.error("Reading script.js file failed.", e);
      throw new RuntimeException(e);
    }
    scriptEngine = engine;
  }

  public Invocable getScriptEngine() {
    return (Invocable) scriptEngine;
  }

  public Object getScriptObject() {
    return scriptEngine.get("scriptObj");
  }
}
