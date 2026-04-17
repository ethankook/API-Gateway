package com.gateway.middleware.RateLimiting;

import com.gateway.config.FilterOrder;
import com.gateway.middleware.FilterErrorResponseWriter;
import com.gateway.middleware.RateLimiting.entities.RateLimitResult;
import com.gateway.middleware.RouteMatching.Route;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Order(FilterOrder.RATE_LIMIT)
@RequiredArgsConstructor
@Component("gatewayRateLimitFilter")
public class RateLimitFilter extends OncePerRequestFilter {

  private final RateLimiter rateLimiter;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    Route route = (Route) request.getAttribute("matchedRoute");
    String key;

    if (!route.getRequiresAuth()) {
      String ip = request.getRemoteAddr();
      key = ip + "-" + route.getRouteId();

    } else {
      Long userId = (Long) request.getAttribute("X-Authenticated-User");
      key = userId.toString() + "-" + route.getRouteId();
    }

    RateLimitResult result = rateLimiter.isAllowed(key, route);

    if (result == null) {
      request.setAttribute("rateLimitResult", "skipped");
      FilterErrorResponseWriter.writeError(response, 500, "Internal server error", request);
      return;
    }
    if (!result.isAllowed()) {
      request.setAttribute("rateLimitResult", "exceeded");
      response.setHeader("Retry-After", String.valueOf(result.getRetryAfterSeconds()));
      FilterErrorResponseWriter.writeError(response, 429, "Rate limit exceeded.", request);
      return;
    }

    request.setAttribute("rateLimitResult", "allowed");
    filterChain.doFilter(request, response);
  }
}
