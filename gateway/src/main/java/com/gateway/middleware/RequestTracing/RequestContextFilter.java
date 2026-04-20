package com.gateway.middleware.RequestTracing;

import static com.gateway.config.FilterOrder.REQUEST_CONTEXT;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Order(REQUEST_CONTEXT)
@Component("gatewayRequestContextFilter")
@Slf4j
public class RequestContextFilter extends OncePerRequestFilter {

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String requestId = UUID.randomUUID().toString().substring(0, 8);

    Instant startTime = Instant.now();
    String method = request.getMethod();
    String path = request.getRequestURI();
    String clientIp = request.getRemoteAddr();

    request.setAttribute("requestId", requestId);
    request.setAttribute("startTime", startTime);
    request.setAttribute("authResult", "skipped");
    request.setAttribute("rateLimitResult", "skipped");
    request.setAttribute("downstreamUrl", null);
    request.setAttribute("downstreamStatus", null);
    request.setAttribute("downstreamLatencyMs", null);

    MDC.put("requestId", requestId);
    log.info(
        "event=request_received requestId={} method={} path={} clientIp={}",
        requestId,
        method,
        path,
        clientIp);

    response.setHeader("X-Request-Id", requestId);

    try {
      filterChain.doFilter(request, response);
    } finally {
      String routeId = "none";
      Object matchedRoute = request.getAttribute("matchedRoute");
      if (matchedRoute instanceof com.gateway.middleware.RouteMatching.Route route
          && route.getRouteId() != null
          && !route.getRouteId().isBlank()) {
        routeId = route.getRouteId();
      }
      log.info(
          "event=request_completed requestId={} method={} path={} route={} authResult={} authenticatedPrincipalType={} authenticatedPrincipalId={} rateLimitResult={} downstreamUrl={} downstreamStatus={} downstreamLatencyMs={} totalLatencyMs={} responseStatus={}",
          requestId,
          method,
          path,
          routeId,
          request.getAttribute("authResult"),
          request.getAttribute("X-Authenticated-Principal-Type"),
          request.getAttribute("X-Authenticated-Principal-Id"),
          request.getAttribute("rateLimitResult"),
          request.getAttribute("downstreamUrl"),
          request.getAttribute("downstreamStatus"),
          request.getAttribute("downstreamLatencyMs"),
          Instant.now().toEpochMilli() - startTime.toEpochMilli(),
          response.getStatus());
      MDC.remove("requestId");
    }
  }
}
