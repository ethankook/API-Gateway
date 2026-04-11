package com.gateway.middleware.RouteMatching;

import com.gateway.config.GatewayProperties;
import org.springframework.stereotype.Component;

@Component
public class RouteResolver {
    private final GatewayProperties gatewayProperties;

    public RouteResolver(GatewayProperties gatewayProperties) {
        this.gatewayProperties = gatewayProperties;
    }


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

            if (!path.startsWith(route.getPathPrefix())) {
                continue;
            }

            if (bestMatch == null || route.getPathPrefix().length() > bestMatch.getPathPrefix().length()) {
                bestMatch = route;
            }
        }

        return bestMatch;
    }
}
