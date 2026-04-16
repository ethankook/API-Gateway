package com.gateway.middleware.RouteMatching;

import com.gateway.middleware.FilterErrorResponseWriter;
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

    private final RouteResolver routeResolver;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        Route route = routeResolver.resolve(path);

        if (route == null) {
            FilterErrorResponseWriter.writeError(
                    response,
                    HttpServletResponse.SC_NOT_FOUND,
                    "No route found for path: " + path,
                    request
            );
            return;
        }
        request.setAttribute("matchedRoute", route);

        filterChain.doFilter(request, response);
    }
}
