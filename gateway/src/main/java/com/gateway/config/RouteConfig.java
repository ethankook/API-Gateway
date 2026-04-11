package com.gateway.config;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class RouteConfig {
    String routeId;
    String pathPrefix;
    String downstreamUrl;
    Boolean requiresAuth;
    Integer rateLimit;
    List<String> methods;
}
