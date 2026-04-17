package com.gateway.config;

import com.gateway.middleware.RouteMatching.Route;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "gateway")
public class GatewayProperties {

  private Route[] routes;

  private Integer connectTimeoutMs;
  private Integer readTimeoutMs;
}
