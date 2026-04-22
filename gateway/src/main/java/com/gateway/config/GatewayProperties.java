package com.gateway.config;

import com.gateway.middleware.RouteMatching.Route;
import jakarta.annotation.PostConstruct;
import java.util.Arrays;
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

  private String internalToken;

  @PostConstruct
  void validateInternalTokenConfiguration() {
    boolean tokenRequired =
        routes != null
            && Arrays.stream(routes)
                .anyMatch(
                    route ->
                        route != null && Boolean.TRUE.equals(route.getRequiresInternalToken()));

    if (tokenRequired && (internalToken == null || internalToken.isBlank())) {
      throw new IllegalStateException(
          "gateway.internal-token must be configured when a route requires internal token auth");
    }
  }
}
