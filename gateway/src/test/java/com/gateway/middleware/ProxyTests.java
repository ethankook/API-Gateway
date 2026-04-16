package com.gateway.middleware;

import com.gateway.config.GatewayProperties;
import com.gateway.middleware.Proxy.ProxyFilter;
import com.gateway.middleware.RequestTracing.RequestContextFilter;
import com.gateway.middleware.RouteMatching.Route;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class ProxyTests {

    private MockWebServer downstream;
    private ProxyFilter proxyFilter;
    private RequestContextFilter requestContextFilter;

    @BeforeEach
    void setUp() throws IOException {
        downstream = new MockWebServer();
        downstream.start();

        GatewayProperties properties = new GatewayProperties();
        properties.setConnectTimeoutMs(250);
        properties.setReadTimeoutMs(250);

        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofMillis(properties.getReadTimeoutMs()));

        proxyFilter = new ProxyFilter(
                WebClient.builder()
                        .clientConnector(new ReactorClientHttpConnector(httpClient))
                        .build(),
                properties
        );
        requestContextFilter = new RequestContextFilter();
    }

    @AfterEach
    void tearDown() throws IOException {
        downstream.shutdown();
    }

    @Test
    void proxiesMatchedGetAndReturnsDownstreamStatusHeadersAndBody(CapturedOutput output) throws Exception {
        downstream.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .addHeader("X-Downstream", "league-service")
                .setBody("{\"leagueId\":42}"));

        MockHttpServletRequest request = matchedRequest("GET", "/api/v1/leagues/42");
        request.setQueryString("view=summary");
        request.addHeader("Accept", "application/json");
        MockHttpServletResponse response = new MockHttpServletResponse();

        invokeThroughFilters(request, response);

        RecordedRequest forwarded = downstream.takeRequest(1, TimeUnit.SECONDS);
        assertThat(forwarded).isNotNull();
        assertThat(forwarded.getPath()).isEqualTo("/42?view=summary");
        assertThat(forwarded.getMethod()).isEqualTo("GET");
        assertThat(forwarded.getHeader("Host")).isNotBlank();
        assertThat(forwarded.getHeader("Content-Length")).isNull();
        assertThat(forwarded.getHeader("Accept")).isEqualTo("application/json");

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader("X-Downstream")).isEqualTo("league-service");
        assertThat(response.getContentAsString()).isEqualTo("{\"leagueId\":42}");

        String requestId = response.getHeader("X-Request-Id");
        assertThat(requestId).isNotBlank();
        assertThat(output.getOut()).contains("Proxying requestId=" + requestId);
        assertThat(output.getOut()).contains("Proxy response requestId=" + requestId);
    }

    @Test
    void returnsClean502JsonWhenDownstreamIsStopped() throws Exception {
        int closedPort = closedPort();

        MockHttpServletRequest request = matchedRequest("GET", "/api/v1/leagues/42", "http://localhost:" + closedPort);
        MockHttpServletResponse response = new MockHttpServletResponse();

        invokeThroughFilters(request, response);

        assertThat(response.getStatus()).isEqualTo(502);
        assertThat(response.getContentType()).isEqualTo("application/json");
        assertThat(response.getContentAsString()).contains("\"error\":\"Service unavailable\"");
        assertThat(response.getContentAsString()).contains("\"status\":502");
        assertThat(response.getContentAsString()).contains("\"requestId\":\"" + response.getHeader("X-Request-Id") + "\"");
        assertThat(response.getContentAsString()).doesNotContain("Exception");
    }

    @Test
    void returnsClean504JsonWhenDownstreamExceedsTimeout() throws Exception {
        downstream.enqueue(new MockResponse()
                .setHeadersDelay(1, TimeUnit.SECONDS)
                .setBody("{\"late\":true}"));

        MockHttpServletRequest request = matchedRequest("GET", "/api/v1/leagues/42");
        MockHttpServletResponse response = new MockHttpServletResponse();

        invokeThroughFilters(request, response);

        assertThat(response.getStatus()).isEqualTo(504);
        assertThat(response.getContentType()).isEqualTo("application/json");
        assertThat(response.getContentAsString()).contains("\"error\":\"Server timeout\"");
        assertThat(response.getContentAsString()).contains("\"status\":504");
        assertThat(response.getContentAsString()).contains("\"requestId\":\"" + response.getHeader("X-Request-Id") + "\"");
        assertThat(response.getContentAsString()).doesNotContain("Exception");
    }

    @Test
    void delegatesToFilterChainWhenNoRouteMatched() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/unknown");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        proxyFilter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    private MockHttpServletRequest matchedRequest(String method, String uri) {
        return matchedRequest(method, uri, "http://localhost:" + downstream.getPort());
    }

    private MockHttpServletRequest matchedRequest(String method, String uri, String downstreamBaseUrl) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        Route route = new Route();
        route.setRouteId("League-Service");
        route.setPathPrefix("/api/v1/leagues");
        route.setDownstreamUrl(downstreamBaseUrl);
        request.setAttribute("matchedRoute", route);
        return request;
    }

    private void invokeThroughFilters(MockHttpServletRequest request, MockHttpServletResponse response) throws Exception {
        requestContextFilter.doFilter(request, response, (req, res) ->
                proxyFilter.doFilter(request, response, new MockFilterChain()));
    }

    private int closedPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
