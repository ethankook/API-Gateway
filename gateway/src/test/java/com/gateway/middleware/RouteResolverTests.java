package com.gateway.middleware;

import com.gateway.config.GatewayProperties;
import com.gateway.middleware.RouteMatching.Route;
import com.gateway.middleware.RouteMatching.RouteResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class RouteResolverTests {

    private RouteResolver resolver;

    @BeforeEach
    void setUp() {
        GatewayProperties properties = new GatewayProperties();
        properties.setRoutes(new Route[] {
                route("Ingestion-Service", "/ingestion"),
                route("Scheduler-Service", "/scheduler"),
                route("Scheduler-Admin", "/scheduler/v2"),
        });
        this.resolver = new RouteResolver(properties);
    }

    @Test
    void returnsNullIfNoRouteFound() {
        Route resolved = resolver.resolve("/league");
        assertNull(resolved);
    }

    @Test
    void returnsRouteIfFound() {
        Route resolved = resolver.resolve("/scheduler");
        assertEquals("Scheduler-Service", resolved.getRouteId());

    }

    @Test
    void returnsNullForEmptyPath() {
        Route resolved = resolver.resolve("");
        assertNull(resolved);
    }

    @Test
    void returnsRouteWithLongestPrefixIfMultipleRoutesMatch() {
        Route resolved = resolver.resolve("/scheduler/v2");
        assertEquals("Scheduler-Admin", resolved.getRouteId());
    }

    @Test
    void returnsNullWhenRoutesAreMissing() {
        GatewayProperties properties = new GatewayProperties();
        RouteResolver routeResolver = new RouteResolver(properties);

        Route resolved = routeResolver.resolve("/scheduler");

        assertNull(resolved);
    }

    @Test
    void ignoresRoutesWithMissingPrefix() {
        GatewayProperties properties = new GatewayProperties();
        properties.setRoutes(new Route[] {
                route("Broken-Route", null),
                route("Scheduler-Service", "/scheduler"),
        });

        RouteResolver routeResolver = new RouteResolver(properties);
        assertEquals("Scheduler-Service", routeResolver.resolve("/scheduler").getRouteId());
    }

    private static Route route(String routeId, String pathPrefix) {
        Route route = new Route();
        route.setRouteId(routeId);
        route.setPathPrefix(pathPrefix);
        route.setDownstreamUrl("http://localhost");
        return route;
    }
}
