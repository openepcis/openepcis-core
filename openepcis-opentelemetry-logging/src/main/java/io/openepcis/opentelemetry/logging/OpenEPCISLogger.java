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
package io.openepcis.opentelemetry.logging;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.trace.Span;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

public class OpenEPCISLogger {

  private Logger logger;
  public static final String TRACE_ID = "traceId";
  public static final String SPAN_ID = "spanId";

  private OpenEPCISLogger(Class<?> clazz) {
    this.logger = LoggerFactory.getLogger(clazz);
  }

  public static OpenEPCISLogger getLogger(Class<?> clazz) {
    return new OpenEPCISLogger(clazz);
  }

  public void debug(String message, Object... args) {
    if (logger.isDebugEnabled()) {
      setTracingValues();
      logger.debug(message, args);
      MDC.clear();
    }
  }

  public void info(String message, Object... args) {
    if (logger.isDebugEnabled()) {
      setTracingValues();
      logger.info(message, args);
      MDC.clear();
    }
  }

  public void warn(String message, Object... args) {
    if (logger.isDebugEnabled()) {
      setTracingValues();
      logger.warn(message, args);
      MDC.clear();
    }
  }

  public void error(String message, Object... args) {
    if (logger.isDebugEnabled()) {
      setTracingValues();
      logger.error(message, args);
      MDC.clear();
    }
  }

  public void setMDC(String key, String value) {
    MDC.put(key, value);
  }

  public void clearMDC() {
    MDC.clear();
  }

  private static void setTracingValues() {
    Baggage baggage = Baggage.current();
    if (baggage.getEntryValue(TRACE_ID) != null && baggage.getEntryValue(SPAN_ID) != null) {
      MDC.put(TRACE_ID, baggage.getEntryValue(TRACE_ID));
      MDC.put(SPAN_ID, baggage.getEntryValue(SPAN_ID));
    } else {
      MDC.put(TRACE_ID, Span.current().getSpanContext().getTraceId());
      MDC.put(SPAN_ID, Span.current().getSpanContext().getSpanId());
    }
  }
}
