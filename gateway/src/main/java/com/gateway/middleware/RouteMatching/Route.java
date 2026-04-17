package com.gateway.middleware.RouteMatching;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

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
