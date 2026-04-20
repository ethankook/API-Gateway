package com.gateway.middleware.RequestTracing;

import com.gateway.middleware.RouteMatching.Route;
import java.time.Instant;

public class RequestContext {

  public String requestId;
  Instant startTime;
  Route matchedRoute;
  String authenticatedPrincipalType;
  Long authenticatedPrincipalId;
}
