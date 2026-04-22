package com.gateway.middleware.Authorization;

import com.common.helper.FilterErrorResponseWriter;
import com.gateway.config.FilterOrder;
import com.gateway.config.GatewayFilterExclusions;
import com.gateway.middleware.RouteMatching.Route;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@RequiredArgsConstructor
@Order(FilterOrder.AUTH)
@Component("gatewayAuthFilter")
public class AuthFilter extends OncePerRequestFilter {
  private final JwtService jwtService;

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return GatewayFilterExclusions.shouldBypassRouteFilters(request);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    Route route = (Route) request.getAttribute("matchedRoute");
    if (!Boolean.TRUE.equals(route.getRequiresAuth())) {
      request.setAttribute("authResult", "skipped");
      filterChain.doFilter(request, response);
      return;
    }

    String authHeader = request.getHeader("Authorization");

    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      request.setAttribute("authResult", "invalid");
      FilterErrorResponseWriter.writeError(
          response,
          HttpServletResponse.SC_UNAUTHORIZED,
          "Missing Authorization header or invalid format",
          request);
      return;
    }

    String token = authHeader.substring("Bearer ".length());
    try {
      Claims claims = jwtService.validate(token);
      Long userId = claimAsLong(claims, "userId");
      Long serviceId = claimAsLong(claims, "serviceId");
      Boolean admin = claims.get("admin", Boolean.class);
      if ((userId == null && serviceId == null) || (userId != null && serviceId != null)) {
        request.setAttribute("authResult", "invalid");
        FilterErrorResponseWriter.writeError(
            response, HttpServletResponse.SC_UNAUTHORIZED, "Invalid token", request);
        return;
      }
      request.setAttribute("authResult", "valid");
      request.setAttribute("X-Authenticated-Principal-Type", userId != null ? "USER" : "SERVICE");
      request.setAttribute("X-Authenticated-Principal-Id", userId != null ? userId : serviceId);
      request.setAttribute("X-Authenticated-Is-Admin", Boolean.TRUE.equals(admin));
      filterChain.doFilter(request, response);

    } catch (JwtException | IllegalArgumentException e) {
      request.setAttribute("authResult", "invalid");
      FilterErrorResponseWriter.writeError(
          response, HttpServletResponse.SC_UNAUTHORIZED, "Invalid or expired token", request);
    }
  }

  private Long claimAsLong(Claims claims, String claimName) {
    Object value = claims.get(claimName);
    if (value == null) {
      return null;
    }
    if (value instanceof Number number) {
      return number.longValue();
    }
    throw new IllegalArgumentException("Invalid principal claim type");
  }
}
