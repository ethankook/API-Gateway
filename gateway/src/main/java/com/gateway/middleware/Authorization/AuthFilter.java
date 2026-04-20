package com.gateway.middleware.Authorization;

import com.common.helper.FilterErrorResponseWriter;
import com.gateway.config.FilterOrder;
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
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    Route route = (Route) request.getAttribute("matchedRoute");
    if (route.getRequiresAuth() != true) {
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
      Long userId = claims.get("userId", Long.class);
      Long serviceId = claims.get("serviceId", Long.class);
      if ((userId == null && serviceId == null) || (userId != null && serviceId != null)) {
        request.setAttribute("authResult", "invalid");
        FilterErrorResponseWriter.writeError(
            response, HttpServletResponse.SC_UNAUTHORIZED, "Invalid token", request);
        return;
      }
      request.setAttribute("authResult", "valid");
      request.setAttribute("X-Authenticated-Principal-Type", userId != null ? "USER" : "SERVICE");
      request.setAttribute("X-Authenticated-Principal-Id", userId != null ? userId : serviceId);
      filterChain.doFilter(request, response);

    } catch (JwtException | IllegalArgumentException e) {
      request.setAttribute("authResult", "invalid");
      FilterErrorResponseWriter.writeError(
          response, HttpServletResponse.SC_UNAUTHORIZED, "Invalid or expired token", request);
    }
  }
}
