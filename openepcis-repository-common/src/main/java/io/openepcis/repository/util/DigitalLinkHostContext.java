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
package io.openepcis.repository.util;

import io.quarkus.arc.Arc;
import io.quarkus.arc.InstanceHandle;
import jakarta.enterprise.context.RequestScoped;
import org.eclipse.microprofile.config.ConfigProvider;

/**
 * Request-scoped holder for the {@code GS1-Digital-Link-Host} request header.
 *
 * <p>When set, response-side EPC formatters substitute the authority (host[:port])
 * of every outgoing GS1 Digital Link URI with this value. The URI's scheme is
 * preserved — the override only controls the host. AI paths (e.g.
 * {@code /01/.../21/…}) are also preserved so the URI still resolves against
 * the swapped host.
 *
 * <p>The header is an OpenEPCIS extension — not part of the EPCIS 2.0 REST spec —
 * intended for federation scenarios where a query proxy or downstream consumer
 * wants every DL in the response to point at its own resolver.
 *
 * <p>Without the header, the deployment's {@value #DEFAULT_HOST_PROPERTY} applies,
 * so a repository behind its own resolver (e.g. {@code id.epc.is}) answers with
 * that host instead of the canonical {@code id.gs1.org}. Unset, links stay as
 * the formatter builds them.
 */
@RequestScoped
public class DigitalLinkHostContext {

  /** Host for outgoing Digital Links when a request carries no header. */
  public static final String DEFAULT_HOST_PROPERTY = "openepcis.digital-link.host";

  private String host;

  public String getHost() {
    return host;
  }

  public void setHost(final String host) {
    this.host = host;
  }

  /**
   * Lookup helper for static call-sites (e.g. {@link EPCFormatUtil}) that need
   * the current request's host override without injecting CDI dependencies.
   * The header wins; otherwise {@value #DEFAULT_HOST_PROPERTY}. Returns
   * {@code null} when neither is set.
   */
  public static String currentHost() {
    return pick(requestHost(), defaultHost());
  }

  static String pick(final String requestHost, final String defaultHost) {
    if (requestHost != null && !requestHost.isBlank()) return requestHost;
    if (defaultHost != null && !defaultHost.isBlank()) return defaultHost;
    return null;
  }

  private static String requestHost() {
    try {
      final InstanceHandle<DigitalLinkHostContext> handle =
          Arc.container().instance(DigitalLinkHostContext.class);
      if (handle != null && handle.isAvailable()) {
        return handle.get().getHost();
      }
    } catch (Throwable ignored) {
      // No active request scope or Arc unavailable — fall through.
    }
    return null;
  }

  private static String defaultHost() {
    try {
      return ConfigProvider.getConfig().getOptionalValue(DEFAULT_HOST_PROPERTY, String.class).orElse(null);
    } catch (Throwable ignored) {
      // No config available (e.g. outside a Quarkus runtime) — no default.
      return null;
    }
  }
}
