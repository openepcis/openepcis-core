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
package io.openepcis.repository.opensearch;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.quarkiverse.opensearch.transport.OpenSearchTransportConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.HttpResponseInterceptor;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Outbound OTel instrumentation for the OpenSearch HttpClient5 async transport.
 *
 * <p>The upstream {@code opentelemetry-apache-httpclient-5.2:2.x} library only ships
 * synchronous (classic) instrumentation; the async {@code HttpAsyncClientBuilder}
 * has no public OTel wrapper. Until that gap closes upstream we wire the W3C
 * trace-context propagation and span lifecycle by hand using the OTel API.
 *
 * <p>Picked up automatically by {@code ApacheHttpTransportProvider} via its
 * {@code @OpenSearchTransportConfig HttpClientConfigCallback} CDI SPI.
 *
 * <p>Note: the request and response interceptors of HttpAsyncClientBuilder may run
 * on different reactor threads, so we never call {@code Scope.makeCurrent()} —
 * we only inject propagation headers from the request thread and end the span
 * from whatever thread the response is delivered on. The span is bookkept
 * through the {@code HttpContext} attribute.
 */
@ApplicationScoped
@OpenSearchTransportConfig
public class OpenSearchOtelHttpClientCallback
        implements ApacheHttpClient5TransportBuilder.HttpClientConfigCallback {

    private static final String CTX_ATTR = "io.openepcis.otel.opensearch.span";
    private static final String INSTRUMENTATION_NAME = "io.openepcis.opensearch";
    private static final String INSTRUMENTATION_VERSION = "1.0.0";

    private static final TextMapSetter<HttpRequest> HEADER_SETTER = (carrier, key, value) -> {
        if (carrier != null) {
            carrier.setHeader(key, value);
        }
    };

    // Use the CDI-managed OpenTelemetry bean rather than GlobalOpenTelemetry.get().
    // This callback runs while the OpenSearch client is built at early startup, before
    // Quarkus's OpenTelemetry recorder configures the SDK. GlobalOpenTelemetry.get() on an
    // as-yet-unset global installs and *locks* a no-op instance, so the recorder's later
    // GlobalOpenTelemetry.set() throws "set has already been called" and the app fails to start.
    // Injecting the bean lets Quarkus perform the one-and-only global registration.
    //
    // Injected as an Instance so distribution profiles built without the quarkus-opentelemetry
    // extension (e.g. the AKS profile) still satisfy this injection point: when no OpenTelemetry
    // bean is present we skip outbound instrumentation instead of failing CDI validation at
    // build-time augmentation.
    @Inject Instance<OpenTelemetry> openTelemetry;

    @Override
    public HttpAsyncClientBuilder customizeHttpClient(HttpAsyncClientBuilder builder) {
        if (!openTelemetry.isResolvable()) {
            // No OpenTelemetry bean on the classpath; leave the transport uninstrumented.
            return builder;
        }
        final OpenTelemetry otel = openTelemetry.get();
        final Tracer tracer = otel.getTracer(INSTRUMENTATION_NAME, INSTRUMENTATION_VERSION);

        final HttpRequestInterceptor onRequest = (request, entity, ctx) -> {
            final Context parent = Context.current();
            final String method = request.getMethod();
            final String url = safeUri(request);

            final Span span = tracer.spanBuilder(method)
                    .setParent(parent)
                    .setSpanKind(SpanKind.CLIENT)
                    .setAttribute("http.method", method)
                    .setAttribute("http.url", url)
                    .setAttribute("peer.service", "opensearch")
                    .setAttribute("db.system", "opensearch")
                    .startSpan();

            otel.getPropagators()
                    .getTextMapPropagator()
                    .inject(parent.with(span), request, HEADER_SETTER);

            ctx.setAttribute(CTX_ATTR, span);
        };

        final HttpResponseInterceptor onResponse = (response, entity, ctx) -> {
            final Object stored = ctx.getAttribute(CTX_ATTR);
            if (!(stored instanceof Span span)) {
                return;
            }
            final int code = response.getCode();
            span.setAttribute("http.status_code", code);
            if (code >= 400) {
                span.setStatus(StatusCode.ERROR);
            }
            span.end();
            ctx.removeAttribute(CTX_ATTR);
        };

        builder.addRequestInterceptorFirst(onRequest);
        builder.addResponseInterceptorLast(onResponse);
        return builder;
    }

    private static String safeUri(HttpRequest request) {
        try {
            final URI u = request.getUri();
            return u == null ? "" : u.toString();
        } catch (URISyntaxException e) {
            return "";
        }
    }
}
