package com.gateway.middleware.RouteMatching;

import com.gateway.config.GatewayProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RouteResolver {
    private final GatewayProperties gatewayProperties;

    public Route resolve(String path) {
        if (path == null || path.isBlank()) {
            log.warn("Path is null or blank");
            return null;
        }

        Route[] routes = gatewayProperties.getRoutes();
        if (routes == null || routes.length == 0) {
            log.warn("No routes configured");
            return null;
        }

        Route bestMatch = null;

        for (Route route : routes) {
            if (route == null || route.getPathPrefix() == null || route.getPathPrefix().isBlank()) {
                continue;
            }

            if (!path.startsWith(route.getPathPrefix())) {
                continue;
            }

            if (bestMatch == null || route.getPathPrefix().length() > bestMatch.getPathPrefix().length()) {
                bestMatch = route;
            }
        }

        log.debug("Resolved route {} for {}", bestMatch, path);
        return bestMatch;
    }
}
