package com.gateway.middleware.Proxy;

import com.gateway.config.FilterOrder;
import com.gateway.config.GatewayProperties;
import com.gateway.middleware.FilterErrorResponseWriter;
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
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
@RequiredArgsConstructor
@Order(FilterOrder.PROXY)
@Component("gatewayProxyFilter")
public class ProxyFilter extends OncePerRequestFilter {

  private final WebClient webClient;
  private final GatewayProperties properties;
  private final Set<String> excluded = Set.of("host", "content-length", "authorization");

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
    byte[] body = hasBody(request) ? request.getInputStream().readAllBytes() : null;

    HttpHeaders headers = buildForwardHeaders(request);
    String requestId = FilterErrorResponseWriter.requestId(request);
    log.info(
        "Proxying requestId={} {} {} -> {}",
        requestId,
        request.getMethod(),
        request.getRequestURI(),
        downstreamUrl);

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
      log.info(
          "Proxy response requestId={} {} {} <- {}",
          requestId,
          request.getMethod(),
          request.getRequestURI(),
          downstreamResponse.getStatusCode().value());
    } catch (IllegalStateException e) {
      log.warn(
          "Proxy timeout requestId={} {} {}",
          requestId,
          request.getMethod(),
          request.getRequestURI(),
          e);
      FilterErrorResponseWriter.writeError(
          response, HttpServletResponse.SC_GATEWAY_TIMEOUT, "Server timeout", request);
    } catch (WebClientRequestException e) {
      if (isTimeoutException(e)) {
        log.warn(
            "Proxy timeout requestId={} {} {}",
            requestId,
            request.getMethod(),
            request.getRequestURI(),
            e);
        FilterErrorResponseWriter.writeError(
            response, HttpServletResponse.SC_BAD_GATEWAY, "Server timeout", request);
        return;
      }

      log.warn(
          "Proxy downstream unavailable requestId={} {} {}",
          requestId,
          request.getMethod(),
          request.getRequestURI(),
          e);
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
        continue;
      }
      Enumeration<String> headerValues = request.getHeaders(headerName);

      while (headerValues.hasMoreElements()) {
        headers.add(headerName, headerValues.nextElement());
      }
    }
    Object userId = request.getAttribute("X-Authorized-User");
    if (userId != null) {
      headers.set("X-Authorized-User", String.valueOf(userId));
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
      if ("ReadTimeoutException".equals(simpleName) || "TimeoutException".equals(simpleName)) {
        return true;
      }

      current = current.getCause();
    }

    return false;
  }
}
