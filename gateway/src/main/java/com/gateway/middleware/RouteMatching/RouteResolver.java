package com.gateway.middleware.RouteMatching;

import com.gateway.config.GatewayProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RouteResolver {
  private final GatewayProperties gatewayProperties;

  public Route resolve(String path) {
    if (path == null || path.isBlank()) {
      return null;
    }

    Route[] routes = gatewayProperties.getRoutes();
    if (routes == null || routes.length == 0) {
      return null;
    }

    Route bestMatch = null;

    for (Route route : routes) {
      if (route == null || route.getPathPrefix() == null || route.getPathPrefix().isBlank()) {
        continue;
      }

      if (route.getDownstreamUrl() == null || route.getDownstreamUrl().isBlank()) {
        continue;
      }

      if (!path.startsWith(route.getPathPrefix())) {
        continue;
      }

      if (bestMatch == null
          || route.getPathPrefix().length() > bestMatch.getPathPrefix().length()) {
        bestMatch = route;
      }
    }

    return bestMatch;
  }
}
