package com.gateway.middleware.Authorization;

import static org.assertj.core.api.Assertions.assertThat;

import com.gateway.config.JwtProperties;
import com.gateway.middleware.RouteMatching.Route;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AuthFilterTests {

  private static final String SECRET = "01234567890123456789012345678901";
  private static final String ISSUER = "Orchard";

  private AuthFilter authFilter;

  @BeforeEach
  void setUp() {
    JwtProperties properties = new JwtProperties();
    properties.setSecret(SECRET);
    properties.setIssuer(ISSUER);
    authFilter = new AuthFilter(new JwtService(properties));
  }

  @Test
  void returns401ForProtectedRouteWithoutToken() throws ServletException, IOException {
    MockHttpServletRequest request = protectedRequest();
    request.setAttribute("requestId", "req-missing-token");
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    authFilter.doFilter(request, response, chain);

    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(response.getContentType()).isEqualTo("application/json");
    assertThat(response.getContentAsString())
        .isEqualTo(
            "{\"error\":\"Missing Authorization header or invalid format\",\"status\":401,\"requestId\":\"req-missing-token\"}");
    assertThat(request.getAttribute("authResult")).isEqualTo("invalid");
    assertThat(chain.getRequest()).isNull();
  }

  @Test
  void forwardsProtectedRouteWithValidToken() throws ServletException, IOException {
    MockHttpServletRequest request = protectedRequest();
    request.addHeader(
        "Authorization", "Bearer " + signedToken(42L, Instant.now().plusSeconds(300)));
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    authFilter.doFilter(request, response, chain);

    assertThat(chain.getRequest()).isSameAs(request);
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(request.getAttribute("X-Authenticated-Principal-Type")).isEqualTo("USER");
    assertThat(request.getAttribute("X-Authenticated-Principal-Id")).isEqualTo(42L);
    assertThat(request.getAttribute("authResult")).isEqualTo("valid");
  }

  @Test
  void forwardsProtectedRouteWithValidServiceToken() throws ServletException, IOException {
    MockHttpServletRequest request = protectedRequest();
    request.addHeader(
        "Authorization", "Bearer " + signedServiceToken(7L, Instant.now().plusSeconds(300)));
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    authFilter.doFilter(request, response, chain);

    assertThat(chain.getRequest()).isSameAs(request);
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(request.getAttribute("X-Authenticated-Principal-Type")).isEqualTo("SERVICE");
    assertThat(request.getAttribute("X-Authenticated-Principal-Id")).isEqualTo(7L);
    assertThat(request.getAttribute("authResult")).isEqualTo("valid");
  }

  @Test
  void returns401ForProtectedRouteWithExpiredToken() throws ServletException, IOException {
    MockHttpServletRequest request = protectedRequest();
    request.setAttribute("requestId", "req-expired-token");
    request.addHeader(
        "Authorization", "Bearer " + signedToken(42L, Instant.now().minusSeconds(60)));
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    authFilter.doFilter(request, response, chain);

    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(response.getContentType()).isEqualTo("application/json");
    assertThat(response.getContentAsString())
        .isEqualTo(
            "{\"error\":\"Invalid or expired token\",\"status\":401,\"requestId\":\"req-expired-token\"}");
    assertThat(request.getAttribute("authResult")).isEqualTo("invalid");
    assertThat(chain.getRequest()).isNull();
  }

  @Test
  void returns401ForProtectedRouteWhenPrincipalIdClaimIsMissing()
      throws ServletException, IOException {
    MockHttpServletRequest request = protectedRequest();
    request.setAttribute("requestId", "req-missing-user-id");
    request.addHeader(
        "Authorization", "Bearer " + tokenWithoutPrincipalId(Instant.now().plusSeconds(300)));
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    authFilter.doFilter(request, response, chain);

    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(response.getContentType()).isEqualTo("application/json");
    assertThat(response.getContentAsString())
        .isEqualTo(
            "{\"error\":\"Invalid token\",\"status\":401,\"requestId\":\"req-missing-user-id\"}");
    assertThat(request.getAttribute("authResult")).isEqualTo("invalid");
    assertThat(chain.getRequest()).isNull();
  }

  @Test
  void returns401ForProtectedRouteWhenTokenHasUserAndServiceIds()
      throws ServletException, IOException {
    MockHttpServletRequest request = protectedRequest();
    request.setAttribute("requestId", "req-ambiguous-principal");
    request.addHeader(
        "Authorization", "Bearer " + tokenWithUserAndServiceIds(Instant.now().plusSeconds(300)));
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    authFilter.doFilter(request, response, chain);

    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(response.getContentType()).isEqualTo("application/json");
    assertThat(response.getContentAsString())
        .isEqualTo(
            "{\"error\":\"Invalid token\",\"status\":401,\"requestId\":\"req-ambiguous-principal\"}");
    assertThat(request.getAttribute("authResult")).isEqualTo("invalid");
    assertThat(chain.getRequest()).isNull();
  }

  @Test
  void forwardsPublicRouteWithoutToken() throws ServletException, IOException {
    MockHttpServletRequest request = requestForRoute(false);
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    authFilter.doFilter(request, response, chain);

    assertThat(chain.getRequest()).isSameAs(request);
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(request.getAttribute("X-Authenticated-Principal-Type")).isNull();
    assertThat(request.getAttribute("X-Authenticated-Principal-Id")).isNull();
    assertThat(request.getAttribute("authResult")).isEqualTo("skipped");
  }

  private MockHttpServletRequest protectedRequest() {
    return requestForRoute(true);
  }

  private MockHttpServletRequest requestForRoute(boolean requiresAuth) {
    MockHttpServletRequest request =
        new MockHttpServletRequest("GET", requiresAuth ? "/api/v1/leagues/42" : "/api/login");
    Route route = new Route();
    route.setRequiresAuth(requiresAuth);
    request.setAttribute("matchedRoute", route);
    return request;
  }

  private String signedToken(Long userId, Instant expiration) {
    return Jwts.builder()
        .issuer(ISSUER)
        .claim("userId", userId)
        .expiration(Date.from(expiration))
        .signWith(Keys.hmacShaKeyFor(SECRET.getBytes()))
        .compact();
  }

  private String signedServiceToken(Long serviceId, Instant expiration) {
    return Jwts.builder()
        .issuer(ISSUER)
        .claim("serviceId", serviceId)
        .expiration(Date.from(expiration))
        .signWith(Keys.hmacShaKeyFor(SECRET.getBytes()))
        .compact();
  }

  private String tokenWithoutPrincipalId(Instant expiration) {
    return Jwts.builder()
        .issuer(ISSUER)
        .expiration(Date.from(expiration))
        .signWith(Keys.hmacShaKeyFor(SECRET.getBytes()))
        .compact();
  }

  private String tokenWithUserAndServiceIds(Instant expiration) {
    return Jwts.builder()
        .issuer(ISSUER)
        .claim("userId", 42L)
        .claim("serviceId", 7L)
        .expiration(Date.from(expiration))
        .signWith(Keys.hmacShaKeyFor(SECRET.getBytes()))
        .compact();
  }
}
