package com.gateway.middleware;

import com.gateway.config.GatewayProperties;
import com.gateway.config.RouteConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class RouteResolverTests {

    private RouteResolver resolver;

    @BeforeEach
    void setUp() {
        GatewayProperties properties = new GatewayProperties();
        properties.routes = new RouteConfig[] {
                route("Ingestion-Service", "/ingestion"),
                route("Scheduler-Service", "/scheduler"),
                route("Scheduler-Admin", "/scheduler/v2"),
        };
        this.resolver = new RouteResolver(properties);
    };

    @Test
    void returnsNullIfNoRouteFound() {
        RouteConfig resolved = resolver.resolve("/league");
        assertNull(resolved);
    }

    @Test
    void returnsRouteIfFound() {
        RouteConfig resolved = resolver.resolve("/scheduler");
        assertEquals("Scheduler-Service", resolved.getRouteId());

    }

    @Test
    void returnsNullForEmptyPath() {
        RouteConfig resolved = resolver.resolve("");
        assertNull(resolved);
    }

    @Test
    void returnsRouteWithLongestPrefixIfMultipleRoutesMatch() {
        RouteConfig resolved = resolver.resolve("/scheduler/v2");
        assertEquals("Scheduler-Admin", resolved.getRouteId());
    }

    private static RouteConfig route(String routeId, String pathPrefix) {
        RouteConfig route = new RouteConfig();
        route.setRouteId(routeId);
        route.setPathPrefix(pathPrefix);
        return route;
    }
}
