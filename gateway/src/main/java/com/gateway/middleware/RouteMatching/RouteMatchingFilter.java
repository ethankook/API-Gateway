package com.gateway.middleware.RouteMatching;

import static com.gateway.config.FilterOrder.ROUTE_MATCHING;

import com.common.helper.FilterErrorResponseWriter;
import com.gateway.config.GatewayFilterExclusions;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Order(ROUTE_MATCHING)
@Component("gatewayRouteMatchingFilter")
@RequiredArgsConstructor
public class RouteMatchingFilter extends OncePerRequestFilter {

  private final RouteResolver routeResolver;

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return GatewayFilterExclusions.shouldBypassRouteFilters(request);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    String path = request.getRequestURI();
    Route route = routeResolver.resolve(path);

    if (route == null) {
      FilterErrorResponseWriter.writeError(
          response, HttpServletResponse.SC_NOT_FOUND, "No route found for path: " + path, request);
      return;
    }

    if (route.getMethods() != null
        && !route.getMethods().isEmpty()
        && route.getMethods().stream()
            .noneMatch(method -> method.equalsIgnoreCase(request.getMethod()))) {
      FilterErrorResponseWriter.writeError(
          response,
          HttpServletResponse.SC_METHOD_NOT_ALLOWED,
          "Method not allowed for path: " + path,
          request);
      return;
    }

    request.setAttribute("matchedRoute", route);

    filterChain.doFilter(request, response);
  }
}
