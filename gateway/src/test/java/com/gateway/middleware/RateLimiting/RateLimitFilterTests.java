package com.gateway.middleware.RateLimiting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.gateway.middleware.RateLimiting.entities.RateLimitResult;
import com.gateway.middleware.RouteMatching.Route;
import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RateLimitFilterTests {

  private RateLimiter rateLimiter;
  private RateLimitFilter filter;

  @BeforeEach
  void setUp() {
    rateLimiter = mock(RateLimiter.class);
    filter = new RateLimitFilter(rateLimiter);
  }

  @Test
  void returns429WithRetryAfterHeaderWhenRateLimitExceeded() throws ServletException, IOException {
    when(rateLimiter.isAllowed(any(), any())).thenReturn(new RateLimitResult(false, 0L, 1L));

    MockHttpServletRequest request = authenticatedRequest(99L, "route-1");
    request.setAttribute("requestId", "req-limited");
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    filter.doFilter(request, response, chain);

    assertThat(response.getStatus()).isEqualTo(429);
    assertThat(response.getHeader("Retry-After")).isEqualTo("1");
    assertThat(response.getContentType()).isEqualTo("application/json");
    assertThat(chain.getRequest()).isNull();
  }

  @Test
  void allowedRequestProceedsToChain() throws ServletException, IOException {
    when(rateLimiter.isAllowed(any(), any())).thenReturn(new RateLimitResult(true, 9L, 0L));

    MockHttpServletRequest request = authenticatedRequest(42L, "route-2");
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    filter.doFilter(request, response, chain);

    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(chain.getRequest()).isSameAs(request);
  }

  @Test
  void unauthenticatedRouteUsesIpAndRouteIdAsKey() throws ServletException, IOException {
    when(rateLimiter.isAllowed(any(), any())).thenReturn(new RateLimitResult(true, 9L, 0L));

    Route route = new Route();
    route.setRouteId("public-route");
    route.setRequiresAuth(false);

    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/public");
    request.setRemoteAddr("192.168.1.1");
    request.setAttribute("matchedRoute", route);

    filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

    ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
    verify(rateLimiter).isAllowed(keyCaptor.capture(), any());
    assertThat(keyCaptor.getValue()).isEqualTo("192.168.1.1-public-route");
  }

  @Test
  void authenticatedRouteUsesUserIdAndRouteIdAsKey() throws ServletException, IOException {
    when(rateLimiter.isAllowed(any(), any())).thenReturn(new RateLimitResult(true, 9L, 0L));

    MockHttpServletRequest request = authenticatedRequest(77L, "protected-route");

    filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

    ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
    verify(rateLimiter).isAllowed(keyCaptor.capture(), any());
    assertThat(keyCaptor.getValue()).isEqualTo("77-protected-route");
  }

  private MockHttpServletRequest authenticatedRequest(Long userId, String routeId) {
    Route route = new Route();
    route.setRouteId(routeId);
    route.setRequiresAuth(true);

    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/protected");
    request.setAttribute("matchedRoute", route);
    request.setAttribute("X-Authorized-User", userId);
    return request;
  }
}
