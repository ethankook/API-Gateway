package com.gateway.config;

import com.gateway.middleware.RouteMatching.Route;
import org.springframework.boot.context.properties.ConfigurationProperties;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "gateway")
public class GatewayProperties {

    private Route[] routes;

    private Integer connectTimeoutMs;
    private Integer readTimeoutMs;

}
