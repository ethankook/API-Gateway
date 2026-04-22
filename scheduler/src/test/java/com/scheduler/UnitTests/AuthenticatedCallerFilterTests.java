package com.scheduler.UnitTests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.scheduler.enums.PrincipalType;
import com.scheduler.filter.AuthenticatedCallerFilter;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

class AuthenticatedCallerFilterTests {

  private static final String INTERNAL_TOKEN = "test-gateway-internal-token";

  private AuthenticatedCallerFilter filter;

  @BeforeEach
  void setUp() {
    filter = new AuthenticatedCallerFilter();
    ReflectionTestUtils.setField(filter, "requireAuthenticatedCaller", true);
    ReflectionTestUtils.setField(filter, "gatewayInternalToken", INTERNAL_TOKEN);
  }

  @Test
  void rejectsRequestWithoutGatewayInternalToken() throws Exception {
    MockHttpServletRequest request = authenticatedRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    filter.doFilter(request, response, chain);

    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(response.getContentAsString()).contains("\"error\":\"Unauthorized\"");
    verifyNoInteractions(chain);
  }

  @Test
  void rejectsRequestWithInvalidGatewayInternalToken() throws Exception {
    MockHttpServletRequest request = authenticatedRequest();
    request.addHeader("X-Gateway-Internal-Token", "wrong-token");
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    filter.doFilter(request, response, chain);

    assertThat(response.getStatus()).isEqualTo(401);
    verifyNoInteractions(chain);
  }

  @Test
  void rejectsRequestWithoutPrincipalHeaders() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/jobs");
    request.addHeader("X-Gateway-Internal-Token", INTERNAL_TOKEN);
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    filter.doFilter(request, response, chain);

    assertThat(response.getStatus()).isEqualTo(401);
    verifyNoInteractions(chain);
  }

  @Test
  void rejectsInvalidPrincipalHeader() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/jobs");
    request.addHeader("X-Gateway-Internal-Token", INTERNAL_TOKEN);
    request.addHeader("X-Authenticated-Principal-Type", "INVALID");
    request.addHeader("X-Authenticated-Principal-Id", "42");
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    filter.doFilter(request, response, chain);

    assertThat(response.getStatus()).isEqualTo(400);
    assertThat(response.getContentAsString())
        .contains("\"error\":\"Invalid authenticated principal\"");
    verifyNoInteractions(chain);
  }

  @Test
  void acceptsValidGatewayTokenAndPrincipalHeaders() throws Exception {
    MockHttpServletRequest request = authenticatedRequest();
    request.addHeader("X-Gateway-Internal-Token", INTERNAL_TOKEN);
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    filter.doFilter(request, response, chain);

    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(request.getAttribute("X-Authenticated-Principal-Type"))
        .isEqualTo(PrincipalType.USER);
    assertThat(request.getAttribute("X-Authenticated-Principal-Id")).isEqualTo(42L);
    assertThat(request.getAttribute("X-Authenticated-Is-Admin")).isEqualTo(false);
    verify(chain).doFilter(request, response);
  }

  @Test
  void acceptsValidAdminHeader() throws Exception {
    MockHttpServletRequest request = authenticatedRequest();
    request.addHeader("X-Gateway-Internal-Token", INTERNAL_TOKEN);
    request.addHeader("X-Authenticated-Is-Admin", "true");
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    filter.doFilter(request, response, chain);

    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(request.getAttribute("X-Authenticated-Is-Admin")).isEqualTo(true);
    verify(chain).doFilter(request, response);
  }

  @Test
  void rejectsInvalidAdminHeader() throws Exception {
    MockHttpServletRequest request = authenticatedRequest();
    request.addHeader("X-Gateway-Internal-Token", INTERNAL_TOKEN);
    request.addHeader("X-Authenticated-Is-Admin", "maybe");
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    filter.doFilter(request, response, chain);

    assertThat(response.getStatus()).isEqualTo(400);
    assertThat(response.getContentAsString())
        .contains("\"error\":\"Invalid authenticated principal\"");
    verifyNoInteractions(chain);
  }

  @Test
  void acceptsUppercaseFalseAdminHeader() throws Exception {
    MockHttpServletRequest request = authenticatedRequest();
    request.addHeader("X-Gateway-Internal-Token", INTERNAL_TOKEN);
    request.addHeader("X-Authenticated-Is-Admin", "FALSE");
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    filter.doFilter(request, response, chain);

    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(request.getAttribute("X-Authenticated-Is-Admin")).isEqualTo(false);
    verify(chain).doFilter(request, response);
  }

  private MockHttpServletRequest authenticatedRequest() {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/jobs");
    request.addHeader("X-Authenticated-Principal-Type", "USER");
    request.addHeader("X-Authenticated-Principal-Id", "42");
    return request;
  }
}
