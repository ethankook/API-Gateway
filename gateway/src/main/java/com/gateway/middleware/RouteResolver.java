package com.gateway.middleware;

import com.gateway.config.GatewayProperties;
import com.gateway.config.RouteConfig;
import org.springframework.stereotype.Component;

@Component
public class RouteResolver {
    private final GatewayProperties gatewayProperties;

    public RouteResolver(GatewayProperties gatewayProperties) {
        this.gatewayProperties = gatewayProperties;
    }


    public RouteConfig resolve(String path) {
        if(path == null || path.isBlank()) { return null; }

        RouteConfig bestMatch = null;

        for(RouteConfig route : gatewayProperties.routes) {
            if(!path.startsWith(route.getPathPrefix())) { continue; }

            if(bestMatch == null || route.getPathPrefix().length() > bestMatch.getPathPrefix().length()) {
                bestMatch = route;
            }
        }

        return bestMatch;
    }

}
