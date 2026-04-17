package com.gateway.middleware.RouteMatching;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class Route {
    String routeId;
    String pathPrefix;
    String downstreamUrl;
    Boolean requiresAuth;
    Long rateLimitCapacity;
    Long rateLimitRefillRate;
    List<String> methods;
}
