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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.openepcis.config.ScriptEngineConfig;
import io.openepcis.model.epcis.exception.ExceptionMessages;
import io.openepcis.model.epcis.exception.QueryParameterException;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.script.ScriptException;
import org.apache.commons.lang3.tuple.Pair;
import org.graalvm.polyglot.proxy.ProxyObject;
import stormpot.Pool;
import stormpot.Timeout;

@ApplicationScoped
public class UnitConverter {
  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(UnitConverter.class);
  private final ScriptEngineConfig scriptEngineConfig;
  private final Pool<PoolableScriptEngine> pool;

  public UnitConverter(ScriptEngineConfig scriptEngineConfig) {
    this.scriptEngineConfig = scriptEngineConfig;
    pool = Pool.from(new ScriptEngineAllocator()).setSize(scriptEngineConfig.poolSize()).build();
  }

  public Map<String, Number> multiConvertValueUOM(Pair<String, Object> uomValuePair) {
    PoolableScriptEngine engine = null;
    try {
      engine = pool.claim(new Timeout(5, TimeUnit.SECONDS));
      final HashMap<String, Number> map = new HashMap<>();
      map.putAll((Map<String, Number>) engine.getScriptEngine().invokeMethod(engine.getScriptObject(), "multiconvert", ProxyObject.fromMap(Map.of(uomValuePair.getKey(), uomValuePair.getValue()))));
      // keep original value exactly to what was passed in (to prevent rounding issues for matching
      // exact values)
      map.put(uomValuePair.getKey(), (Number) uomValuePair.getValue());
      return ImmutableMap.copyOf(map);
    } catch (ScriptException | NoSuchMethodException | InterruptedException e) {
      log.error(ExceptionMessages.ERROR_PROCESSING_JAVASCRIPT_ENGINE, e);
      throw new RuntimeException(ExceptionMessages.ERROR_PROCESSING_JAVASCRIPT_ENGINE, e);
    } finally {
      if (engine != null) {
        engine.release();
      }
    }
  }

  public List<String> relatedUOM(String uom) {
    PoolableScriptEngine engine = null;
    try {
      engine = pool.claim(new Timeout(5, TimeUnit.SECONDS));
      return ImmutableList.copyOf((List<String>) engine.getScriptEngine().invokeMethod(engine.getScriptObject(), "relatedUnits", uom));
    } catch (ScriptException | NoSuchMethodException | InterruptedException exception) {
      throw new QueryParameterException("Unsupported UOM: " + uom);
    } finally {
      if (engine != null) {
        engine.release();
      }
    }
  }
}
