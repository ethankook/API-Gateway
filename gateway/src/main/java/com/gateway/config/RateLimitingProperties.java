package com.gateway.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "rate-limiting")
public class RateLimitingProperties {

    private Long defaultCapacity = 20L;
    private Long defaultRefillRate = 2L;
}
