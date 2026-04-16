package com.gateway.middleware;

import com.gateway.config.GatewayProperties;
import com.gateway.middleware.RouteMatching.Route;
import com.gateway.middleware.RouteMatching.RouteMatchingFilter;
import com.gateway.middleware.RouteMatching.RouteResolver;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class RouteMatchingFilterTests {

    @Test
    void storesMatchedRouteOnRequest() throws Exception {
        GatewayProperties properties = new GatewayProperties();
        Route route = new Route();
        route.setRouteId("League-Service");
        route.setPathPrefix("/api/v1/leagues");
        properties.setRoutes(new Route[] {route});

        RouteMatchingFilter filter = new RouteMatchingFilter(new RouteResolver(properties));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/leagues/42");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        doAnswer(invocation -> {
            MockHttpServletRequest req = (MockHttpServletRequest) invocation.getArgument(0);
            assertThat(req.getAttribute("matchedRoute")).isEqualTo(route);
            return null;
        }).when(chain).doFilter(request, response);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void returnsSharedJsonErrorWhenRouteIsMissing() throws Exception {
        GatewayProperties properties = new GatewayProperties();
        properties.setRoutes(new Route[0]);

        RouteMatchingFilter filter = new RouteMatchingFilter(new RouteResolver(properties));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/missing");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, mock(FilterChain.class));

        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(response.getContentType()).isEqualTo("application/json");
        assertThat(response.getContentAsString()).contains("\"error\":\"No route found for path: /missing\"");
        assertThat(response.getContentAsString()).contains("\"status\":404");
        assertThat(response.getContentAsString()).contains("\"requestId\":\"unknown\"");
    }
}
