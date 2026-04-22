package com.gateway.middleware.Proxy;

import com.common.helper.FilterErrorResponseWriter;
import com.gateway.config.FilterOrder;
import com.gateway.config.GatewayFilterExclusions;
import com.gateway.config.GatewayProperties;
import com.gateway.middleware.RouteMatching.Route;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Enumeration;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
@Order(FilterOrder.PROXY)
@Component("gatewayProxyFilter")
public class ProxyFilter extends OncePerRequestFilter {

  private final WebClient webClient;
  private final GatewayProperties properties;
  private final Set<String> excluded =
      Set.of(
          // Security - already handled
          "authorization",

          // Protocol - HTTP client handles
          "content-length",
          "transfer-encoding",
          "connection",
          "keep-alive",
          "proxy-authenticate",
          "proxy-authorization",
          "te",
          "trailer",
          "upgrade",

          // Gateway-internal - never trust from client
          "x-request-id",
          "x-authenticated-user",
          "x-authenticated-principal-type",
          "x-authenticated-principal-id",
          "x-authenticated-is-admin",
          "x-gateway-internal-token",
          "x-forwarded-for",
          "x-forwarded-host",
          "x-forwarded-proto",

          // Special - handled separately
          "host");

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return GatewayFilterExclusions.shouldBypassRouteFilters(request);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    Route route = (Route) request.getAttribute("matchedRoute");

    if (route == null) {
      filterChain.doFilter(request, response);
      return;
    }

    String tail = request.getRequestURI().substring(route.getPathPrefix().length());
    String query =
        request.getQueryString() == null || request.getQueryString().isBlank()
            ? ""
            : "?" + request.getQueryString();
    String downstreamUrl = route.getDownstreamUrl() + tail + query;
    String downstreamUrlForLog = route.getRouteId() + tail;
    byte[] body = hasBody(request) ? request.getInputStream().readAllBytes() : null;
    long downstreamStart = System.currentTimeMillis();

    HttpHeaders headers = buildForwardHeaders(request);
    String requestId = FilterErrorResponseWriter.requestId(request);

    Object principalType = request.getAttribute("X-Authenticated-Principal-Type");
    Object principalId = request.getAttribute("X-Authenticated-Principal-Id");
    Object isAdmin = request.getAttribute("X-Authenticated-Is-Admin");
    if (principalType != null && principalId != null) {
      headers.set("X-Authenticated-Principal-Type", String.valueOf(principalType));
      headers.set("X-Authenticated-Principal-Id", String.valueOf(principalId));
      headers.set("X-Authenticated-Is-Admin", String.valueOf(Boolean.TRUE.equals(isAdmin)));
    }
    if (Boolean.TRUE.equals(route.getRequiresInternalToken())) {
      headers.set("X-Gateway-Internal-Token", properties.getInternalToken());
    }
    headers.set("X-Forwarded-For", request.getRemoteAddr());
    headers.set("X-Request-Id", requestId);
    request.setAttribute("downstreamUrl", downstreamUrlForLog);

    try {
      ResponseEntity<byte[]> downstreamResponse =
          webClient
              .method(HttpMethod.valueOf(request.getMethod()))
              .uri(downstreamUrl)
              .headers(h -> h.addAll(headers))
              .body(body == null ? BodyInserters.empty() : BodyInserters.fromValue(body))
              .retrieve()
              .onStatus(s -> true, r -> Mono.empty())
              .toEntity(byte[].class)
              .block(Duration.ofMillis(properties.getReadTimeoutMs()));

      if (downstreamResponse == null) {
        request.setAttribute("downstreamLatencyMs", System.currentTimeMillis() - downstreamStart);
        FilterErrorResponseWriter.writeError(
            response,
            HttpServletResponse.SC_BAD_GATEWAY,
            "No response from downstream service",
            request);
        return;
      }

      response.setStatus(downstreamResponse.getStatusCode().value());
      downstreamResponse
          .getHeaders()
          .forEach((name, values) -> values.forEach(value -> response.addHeader(name, value)));

      byte[] responseBody = downstreamResponse.getBody();
      if (responseBody != null) {
        response.getOutputStream().write(responseBody);
      }
      request.setAttribute("downstreamStatus", downstreamResponse.getStatusCode().value());
      request.setAttribute("downstreamLatencyMs", System.currentTimeMillis() - downstreamStart);
    } catch (IllegalStateException e) {
      request.setAttribute("downstreamLatencyMs", System.currentTimeMillis() - downstreamStart);
      FilterErrorResponseWriter.writeError(
          response, HttpServletResponse.SC_GATEWAY_TIMEOUT, "Server timeout", request);
    } catch (WebClientRequestException e) {
      if (isTimeoutException(e)) {
        request.setAttribute("downstreamLatencyMs", System.currentTimeMillis() - downstreamStart);
        FilterErrorResponseWriter.writeError(
            response, HttpServletResponse.SC_GATEWAY_TIMEOUT, "Server timeout", request);
        return;
      }

      request.setAttribute("downstreamLatencyMs", System.currentTimeMillis() - downstreamStart);
      FilterErrorResponseWriter.writeError(
          response, HttpServletResponse.SC_BAD_GATEWAY, "Service unavailable", request);
    }
  }

  private boolean hasBody(HttpServletRequest request) {
    return request.getContentLength() > 0;
  }

  private HttpHeaders buildForwardHeaders(HttpServletRequest request) {
    Enumeration<String> headerNames = request.getHeaderNames();
    HttpHeaders headers = new HttpHeaders();
    while (headerNames.hasMoreElements()) {
      String headerName = headerNames.nextElement();
      if (excluded.contains(headerName.toLowerCase())) {
        if (headerName.equalsIgnoreCase("host")) {
          headers.set("X-Forwarded-Host", request.getHeader("Host"));
        }
        continue;
      }
      Enumeration<String> headerValues = request.getHeaders(headerName);

      while (headerValues.hasMoreElements()) {
        headers.add(headerName, headerValues.nextElement());
      }
    }
    return headers;
  }

  private boolean isTimeoutException(Throwable throwable) {
    Throwable current = throwable;
    while (current != null) {
      if (current instanceof TimeoutException) {
        return true;
      }

      String simpleName = current.getClass().getSimpleName();
      if ("ReadTimeoutException".equals(simpleName)
          || "ConnectTimeoutException".equals(simpleName)
          || "TimeoutException".equals(simpleName)) {
        return true;
      }

      current = current.getCause();
    }

    return false;
  }
}
