package com.gateway.middleware.RequestTracing;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

import static com.gateway.config.FilterOrder.REQUEST_CONTEXT;

@Order(REQUEST_CONTEXT)
@Component("gatewayRequestContextFilter")
@RequiredArgsConstructor
public class RequestContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String requestId = UUID.randomUUID().toString().substring(0, 8);

        Instant startTime = Instant.now();

        request.setAttribute("requestId", requestId);
        request.setAttribute("startTime", startTime);

        MDC.put("requestId", requestId);

        response.setHeader("X-Request-Id", requestId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove("requestId");
        }
    }
}
