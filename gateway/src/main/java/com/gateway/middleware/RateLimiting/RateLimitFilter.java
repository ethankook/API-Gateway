package com.gateway.middleware.RateLimiting;


import com.gateway.config.FilterOrder;
import com.gateway.middleware.FilterErrorResponseWriter;
import com.gateway.middleware.RateLimiting.entities.RateLimitResult;
import com.gateway.middleware.RouteMatching.Route;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Order(FilterOrder.RATE_LIMIT)
@RequiredArgsConstructor
@Slf4j
@Component("gatewayRateLimitFilter")
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimiter rateLimiter;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        Route route = (Route) request.getAttribute("matchedRoute");
        String key;

        if (!route.getRequiresAuth()) {
            String ip = request.getRemoteAddr();
            key = ip + "-" + route.getRouteId();

        }
        else {
            Long userId = (Long) request.getAttribute("X-Authorized-User");
            key = userId.toString() + "-" + route.getRouteId();
        }

        RateLimitResult result = rateLimiter.isAllowed(key, route);

        if (result == null) {
            FilterErrorResponseWriter.writeError(response, 500, "Internal server error", request);
            log.warn("Internal server error while checking rate limit on route {}", route.getPathPrefix());
            return;
        }
        if (!result.isAllowed()) {
            response.setHeader("Retry-After", String.valueOf(result.getRetryAfterSeconds()));
            FilterErrorResponseWriter.writeError(response, 429, "Rate limit exceeded.", request);
            log.warn("Rate limit exceeded on route {}. Retry after {} seconds", route.getPathPrefix(), result.getRetryAfterSeconds());
            return;
        }

        filterChain.doFilter(request, response);


    }

}
