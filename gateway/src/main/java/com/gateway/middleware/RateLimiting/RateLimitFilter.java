package com.gateway.middleware.RateLimiting;


import com.gateway.config.FilterOrder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Order(FilterOrder.RATE_LIMIT)
@RequiredArgsConstructor
@Slf4j
@Component("gatewayRateLimitFilter")
public class RateLimitFilter {

}
