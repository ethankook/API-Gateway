package com.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(OutputCaptureExtension.class)
class GatewayIntegrationTests {

  private static final String SECRET = "01234567890123456789012345678901";
  private static final String ISSUER = "Orchard";
  private static final String INTERNAL_TOKEN = "test-gateway-internal-token";
  private static final MockWebServer DOWNSTREAM = new MockWebServer();
  private static final int CLOSED_PORT = findClosedPort();

  @LocalServerPort private int port;

  private RestClient restClient;

  @DynamicPropertySource
  static void registerProperties(DynamicPropertyRegistry registry) {
    startDownstream();
    registry.add("auth.jwt.secret", () -> SECRET);
    registry.add("auth.jwt.issuer", () -> ISSUER);
    registry.add("gateway.connect-timeout-ms", () -> "250");
    registry.add("gateway.read-timeout-ms", () -> "250");
    registry.add("gateway.internal-token", () -> INTERNAL_TOKEN);
    registry.add("rate-limiting.default-capacity", () -> "20");
    registry.add("rate-limiting.default-refill-rate", () -> "5");
    registry.add("gateway.routes[0].routeId", () -> "Public-Service");
    registry.add("gateway.routes[0].pathPrefix", () -> "/api/public");
    registry.add("gateway.routes[0].downstreamUrl", () -> downstreamBaseUrl() + "/public");
    registry.add("gateway.routes[0].requiresAuth", () -> "false");
    registry.add("gateway.routes[1].routeId", () -> "League-Service");
    registry.add("gateway.routes[1].pathPrefix", () -> "/api/v1/leagues");
    registry.add("gateway.routes[1].downstreamUrl", () -> downstreamBaseUrl() + "/leagues");
    registry.add("gateway.routes[1].requiresAuth", () -> "true");
    registry.add("gateway.routes[2].routeId", () -> "Limited-Service");
    registry.add("gateway.routes[2].pathPrefix", () -> "/api/v1/limited");
    registry.add("gateway.routes[2].downstreamUrl", () -> downstreamBaseUrl() + "/limited");
    registry.add("gateway.routes[2].requiresAuth", () -> "true");
    registry.add("gateway.routes[2].rateLimitCapacity", () -> "2");
    registry.add("gateway.routes[2].rateLimitRefillRate", () -> "1");
    registry.add("gateway.routes[3].routeId", () -> "Down-Service");
    registry.add("gateway.routes[3].pathPrefix", () -> "/api/v1/down");
    registry.add(
        "gateway.routes[3].downstreamUrl", () -> "http://127.0.0.1:" + CLOSED_PORT + "/down");
    registry.add("gateway.routes[3].requiresAuth", () -> "true");
    registry.add("gateway.routes[4].routeId", () -> "Scheduler-Service");
    registry.add("gateway.routes[4].pathPrefix", () -> "/api/v1/jobs");
    registry.add("gateway.routes[4].downstreamUrl", () -> downstreamBaseUrl() + "/jobs");
    registry.add("gateway.routes[4].requiresAuth", () -> "true");
    registry.add("gateway.routes[4].requiresInternalToken", () -> "true");
  }

  @BeforeEach
  void clearDownstreamRequests() throws InterruptedException {
    restClient = RestClient.builder().baseUrl("http://127.0.0.1:" + port).build();
    while (DOWNSTREAM.takeRequest(10, TimeUnit.MILLISECONDS) != null) {}
  }

  @AfterAll
  static void shutdownDownstream() throws IOException {
    DOWNSTREAM.shutdown();
  }

  @Test
  void authenticatedProtectedRouteIsForwardedAndReturned(CapturedOutput output) throws Exception {
    DOWNSTREAM.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .addHeader("X-Downstream", "league-service")
            .setBody("{\"leagueId\":42,\"name\":\"Premier\"}"));

    HttpHeaders headers = bearerHeaders(validToken(101L, Instant.now().plusSeconds(300)));
    headers.add("Accept", "application/json");
    ResponseEntity<String> response = exchange("/api/v1/leagues/42?view=summary", headers);

    RecordedRequest forwarded = takeDownstreamRequest();
    assertThat(forwarded.getMethod()).isEqualTo("GET");
    assertThat(forwarded.getPath()).isEqualTo("/leagues/42?view=summary");
    assertThat(forwarded.getHeader("Authorization")).isNull();
    assertThat(forwarded.getHeader("Accept")).isEqualTo("application/json");
    assertThat(forwarded.getHeader("X-Authenticated-Principal-Type")).isEqualTo("USER");
    assertThat(forwarded.getHeader("X-Authenticated-Principal-Id")).isEqualTo("101");
    assertThat(forwarded.getHeader("X-Request-Id"))
        .isEqualTo(response.getHeaders().getFirst("X-Request-Id"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().getFirst("X-Downstream")).isEqualTo("league-service");
    assertThat(response.getBody()).isEqualTo("{\"leagueId\":42,\"name\":\"Premier\"}");

    String requestId = response.getHeaders().getFirst("X-Request-Id");
    assertThat(requestId).isNotBlank();
    assertThat(output.getOut()).contains("event=request_received requestId=" + requestId);
    assertThat(output.getOut()).contains("event=request_completed requestId=" + requestId);
    assertThat(output.getOut()).contains("route=League-Service");
    assertThat(output.getOut()).contains("authResult=valid");
    assertThat(output.getOut()).contains("authenticatedPrincipalType=USER");
    assertThat(output.getOut()).contains("authenticatedPrincipalId=101");
    assertThat(output.getOut()).contains("rateLimitResult=allowed");
    assertThat(output.getOut()).contains("downstreamUrl=League-Service/42");
    assertThat(output.getOut()).contains("downstreamStatus=200");
    assertThat(output.getOut()).contains("responseStatus=200");
  }

  @Test
  void publicRouteWithoutTokenIsForwardedWithoutAuthCheck() throws Exception {
    DOWNSTREAM.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody("{\"ok\":true}"));

    ResponseEntity<String> response = get("/api/public/ping?source=test");

    RecordedRequest forwarded = takeDownstreamRequest();
    assertThat(forwarded.getPath()).isEqualTo("/public/ping?source=test");
    assertThat(forwarded.getHeader("Authorization")).isNull();
    assertThat(forwarded.getHeader("X-Authenticated-Principal-Type")).isNull();
    assertThat(forwarded.getHeader("X-Authenticated-Principal-Id")).isNull();
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isEqualTo("{\"ok\":true}");
  }

  @Test
  void protectedRouteWithoutTokenReturns401AndDoesNotForward() throws Exception {
    ResponseEntity<String> response = get("/api/v1/leagues/without-token");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(response.getBody())
        .contains("\"error\":\"Missing Authorization header or invalid format\"");
    assertThat(response.getBody()).contains("\"status\":401");
    assertThat(response.getBody())
        .contains("\"requestId\":\"" + response.getHeaders().getFirst("X-Request-Id") + "\"");
    assertThat(noDownstreamRequest()).isTrue();
  }

  @Test
  void protectedRouteWithInvalidTokenReturns401AndDoesNotForward() throws Exception {
    HttpHeaders headers =
        bearerHeaders(validToken(102L, Instant.now().plusSeconds(300)) + "tampered");
    ResponseEntity<String> response = exchange("/api/v1/leagues/invalid-token", headers);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(response.getBody()).contains("\"error\":\"Invalid or expired token\"");
    assertThat(response.getBody()).contains("\"status\":401");
    assertThat(noDownstreamRequest()).isTrue();
  }

  @Test
  void schedulerRouteWithoutTokenReturns401AndDoesNotForward() throws Exception {
    ResponseEntity<String> response = get("/api/v1/jobs");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(response.getBody())
        .contains("\"error\":\"Missing Authorization header or invalid format\"");
    assertThat(noDownstreamRequest()).isTrue();
  }

  @Test
  void schedulerRouteForwardsUserPrincipalHeaders() throws Exception {
    DOWNSTREAM.enqueue(new MockResponse().setResponseCode(200).setBody("{\"ok\":true}"));

    HttpHeaders headers = bearerHeaders(validToken(201L, Instant.now().plusSeconds(300)));
    ResponseEntity<String> response = exchange("/api/v1/jobs", headers);

    RecordedRequest forwarded = takeDownstreamRequest();
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(forwarded.getPath()).isEqualTo("/jobs");
    assertThat(forwarded.getHeader("X-Authenticated-Principal-Type")).isEqualTo("USER");
    assertThat(forwarded.getHeader("X-Authenticated-Principal-Id")).isEqualTo("201");
    assertThat(forwarded.getHeader("X-Gateway-Internal-Token")).isEqualTo(INTERNAL_TOKEN);
  }

  @Test
  void schedulerRouteForwardsServicePrincipalHeaders() throws Exception {
    DOWNSTREAM.enqueue(new MockResponse().setResponseCode(200).setBody("{\"ok\":true}"));

    HttpHeaders headers = bearerHeaders(validServiceToken(301L, Instant.now().plusSeconds(300)));
    ResponseEntity<String> response = exchange("/api/v1/jobs", headers);

    RecordedRequest forwarded = takeDownstreamRequest();
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(forwarded.getHeader("X-Authenticated-Principal-Type")).isEqualTo("SERVICE");
    assertThat(forwarded.getHeader("X-Authenticated-Principal-Id")).isEqualTo("301");
    assertThat(forwarded.getHeader("X-Gateway-Internal-Token")).isEqualTo(INTERNAL_TOKEN);
  }

  @Test
  void forgedGatewayInternalTokenIsOverwrittenForSchedulerRoute() throws Exception {
    DOWNSTREAM.enqueue(new MockResponse().setResponseCode(200).setBody("{\"ok\":true}"));

    HttpHeaders headers = bearerHeaders(validToken(202L, Instant.now().plusSeconds(300)));
    headers.add("X-Gateway-Internal-Token", "forged");
    ResponseEntity<String> response = exchange("/api/v1/jobs", headers);

    RecordedRequest forwarded = takeDownstreamRequest();
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(forwarded.getHeader("X-Gateway-Internal-Token")).isEqualTo(INTERNAL_TOKEN);
  }

  @Test
  void schedulerRouteForwardsAdminClaim() throws Exception {
    DOWNSTREAM.enqueue(new MockResponse().setResponseCode(200).setBody("{\"ok\":true}"));

    HttpHeaders headers = bearerHeaders(validToken(203L, true, Instant.now().plusSeconds(300)));
    ResponseEntity<String> response = exchange("/api/v1/jobs", headers);

    RecordedRequest forwarded = takeDownstreamRequest();
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(forwarded.getHeader("X-Authenticated-Is-Admin")).isEqualTo("true");
  }

  @Test
  void rateLimitExceededReturns429AfterCapacityIsConsumed() throws Exception {
    DOWNSTREAM.enqueue(new MockResponse().setResponseCode(200).setBody("{\"request\":1}"));
    DOWNSTREAM.enqueue(new MockResponse().setResponseCode(200).setBody("{\"request\":2}"));

    HttpHeaders headers = bearerHeaders(validToken(103L, Instant.now().plusSeconds(300)));

    ResponseEntity<String> first = exchange("/api/v1/limited/check", headers);
    ResponseEntity<String> second = exchange("/api/v1/limited/check", headers);
    ResponseEntity<String> third = exchange("/api/v1/limited/check", headers);

    assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(third.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    assertThat(third.getHeaders().getFirst("Retry-After")).isEqualTo("1");
    assertThat(third.getBody()).contains("\"error\":\"Rate limit exceeded.\"");
    assertThat(takeDownstreamRequest().getPath()).isEqualTo("/limited/check");
    assertThat(takeDownstreamRequest().getPath()).isEqualTo("/limited/check");
    assertThat(noDownstreamRequest()).isTrue();
  }

  @Test
  void unknownRouteReturns404AndDoesNotForward() throws Exception {
    ResponseEntity<String> response = get("/api/v1/unknown/resource");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody())
        .contains("\"error\":\"No route found for path: /api/v1/unknown/resource\"");
    assertThat(response.getBody()).contains("\"status\":404");
    assertThat(noDownstreamRequest()).isTrue();
  }

  @Test
  void partialSchedulerPrefixReturns404AndDoesNotForward() throws Exception {
    ResponseEntity<String> response = get("/api/v1/jobsXYZ");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody())
        .contains("\"error\":\"No route found for path: /api/v1/jobsXYZ\"");
    assertThat(noDownstreamRequest()).isTrue();
  }

  @Test
  void healthBypassesGatewayRoutes() {
    ResponseEntity<String> response = get("/health");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isEqualTo("{\"status\":\"UP\"}");
  }

  @Test
  void unavailableDownstreamReturns502() throws Exception {
    HttpHeaders headers = bearerHeaders(validToken(104L, Instant.now().plusSeconds(300)));
    ResponseEntity<String> response = exchange("/api/v1/down/service", headers);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
    assertThat(response.getBody()).contains("\"error\":\"Service unavailable\"");
    assertThat(response.getBody()).contains("\"status\":502");
    assertThat(noDownstreamRequest()).isTrue();
  }

  @Test
  void requestIdIsPropagatedToDownstream() throws Exception {
    DOWNSTREAM.enqueue(new MockResponse().setResponseCode(200).setBody("{\"ok\":true}"));

    ResponseEntity<String> response = get("/api/public/request-id");

    RecordedRequest forwarded = takeDownstreamRequest();
    assertThat(response.getHeaders().getFirst("X-Request-Id")).isNotBlank();
    assertThat(forwarded.getHeader("X-Request-Id"))
        .isEqualTo(response.getHeaders().getFirst("X-Request-Id"));
  }

  @Test
  void authenticatedPrincipalHeadersArePropagatedToDownstream() throws Exception {
    DOWNSTREAM.enqueue(new MockResponse().setResponseCode(200).setBody("{\"ok\":true}"));

    HttpHeaders headers = bearerHeaders(validToken(105L, Instant.now().plusSeconds(300)));
    exchange("/api/v1/leagues/user-header", headers);

    RecordedRequest forwarded = takeDownstreamRequest();
    assertThat(forwarded.getHeader("X-Authenticated-Principal-Type")).isEqualTo("USER");
    assertThat(forwarded.getHeader("X-Authenticated-Principal-Id")).isEqualTo("105");
  }

  @Test
  void forgedAuthenticatedPrincipalHeadersAreOverwrittenByJwtIdentity() throws Exception {
    DOWNSTREAM.enqueue(new MockResponse().setResponseCode(200).setBody("{\"ok\":true}"));

    HttpHeaders headers = bearerHeaders(validToken(106L, Instant.now().plusSeconds(300)));
    headers.add("X-Authenticated-Principal-Type", "SERVICE");
    headers.add("X-Authenticated-Principal-Id", "999999");
    headers.add("X-Authenticated-Is-Admin", "true");
    exchange("/api/v1/leagues/forged-user", headers);

    RecordedRequest forwarded = takeDownstreamRequest();
    assertThat(forwarded.getHeader("X-Authenticated-Principal-Type")).isEqualTo("USER");
    assertThat(forwarded.getHeader("X-Authenticated-Principal-Id")).isEqualTo("106");
    assertThat(forwarded.getHeader("X-Authenticated-Is-Admin")).isEqualTo("false");
  }

  private static void startDownstream() {
    if (DOWNSTREAM.getPort() != -1) {
      return;
    }

    try {
      DOWNSTREAM.start();
    } catch (IOException e) {
      throw new IllegalStateException("Failed to start MockWebServer", e);
    }
  }

  private static String downstreamBaseUrl() {
    return DOWNSTREAM.url("").toString().replaceAll("/$", "");
  }

  private HttpHeaders bearerHeaders(String token) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);
    return headers;
  }

  private ResponseEntity<String> get(String path) {
    return restClient
        .get()
        .uri(path)
        .exchange(
            (request, response) ->
                new ResponseEntity<>(
                    new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8),
                    response.getHeaders(),
                    response.getStatusCode()));
  }

  private ResponseEntity<String> exchange(String path, HttpHeaders headers) {
    return restClient
        .method(HttpMethod.GET)
        .uri(path)
        .headers(existing -> existing.addAll(headers))
        .exchange(
            (request, response) ->
                new ResponseEntity<>(
                    new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8),
                    response.getHeaders(),
                    response.getStatusCode()));
  }

  private String validToken(Long userId, Instant expiration) {
    return validToken(userId, false, expiration);
  }

  private String validToken(Long userId, boolean admin, Instant expiration) {
    return Jwts.builder()
        .issuer(ISSUER)
        .claim("userId", userId)
        .claim("admin", admin)
        .expiration(Date.from(expiration))
        .signWith(Keys.hmacShaKeyFor(SECRET.getBytes()))
        .compact();
  }

  private String validServiceToken(Long serviceId, Instant expiration) {
    return Jwts.builder()
        .issuer(ISSUER)
        .claim("serviceId", serviceId)
        .expiration(Date.from(expiration))
        .signWith(Keys.hmacShaKeyFor(SECRET.getBytes()))
        .compact();
  }

  private RecordedRequest takeDownstreamRequest() throws InterruptedException {
    RecordedRequest request = DOWNSTREAM.takeRequest(1, TimeUnit.SECONDS);
    assertThat(request).isNotNull();
    return request;
  }

  private boolean noDownstreamRequest() throws InterruptedException {
    return DOWNSTREAM.takeRequest(200, TimeUnit.MILLISECONDS) == null;
  }

  private static int findClosedPort() {
    try (ServerSocket socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    } catch (IOException e) {
      throw new IllegalStateException("Failed to reserve closed port", e);
    }
  }
}
