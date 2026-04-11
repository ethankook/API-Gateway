package com.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "gateway")
public class GatewayProperties {

    public RouteConfig[] routes;
}
