package com.gateway.middleware.RouteMatching;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

import static com.gateway.config.FilterOrder.ROUTE_MATCHING;

@Order(ROUTE_MATCHING)
@Component("gatewayRouteMatchingFilter")
@RequiredArgsConstructor
public class RouteMatchingFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;
    private final RouteResolver routeResolver;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        Route route = routeResolver.resolve(path);

        if (route == null) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            response.setContentType("application/json");
            try {
                response.getWriter().write(
                        objectMapper.writeValueAsString(
                                java.util.Map.of("error", "No route found for path: " + path)
                        )
                );
            } catch (JacksonException ex) {
                throw new ServletException("Failed to serialize route-matching error response", ex);
            }
            return;
        }
        request.setAttribute("matchedRoute", route);

        filterChain.doFilter(request, response);
    }
}
