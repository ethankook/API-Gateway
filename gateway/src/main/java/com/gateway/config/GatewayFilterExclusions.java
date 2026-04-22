package com.gateway.config;

import jakarta.servlet.http.HttpServletRequest;

public final class GatewayFilterExclusions {

  private GatewayFilterExclusions() {}

  public static boolean shouldBypassRouteFilters(HttpServletRequest request) {
    String path = request.getRequestURI();
    return "/health".equals(path) || "/actuator".equals(path) || path.startsWith("/actuator/");
  }
}
