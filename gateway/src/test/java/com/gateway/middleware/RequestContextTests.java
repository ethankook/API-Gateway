package com.gateway.middleware;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.gateway.middleware.RequestTracing.RequestContextFilter;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

public class RequestContextTests {

  private RequestContextFilter filter;
  private MockHttpServletRequest request;
  private MockHttpServletResponse response;
  private FilterChain chain;

  @BeforeEach
  void setUp() {
    filter = new RequestContextFilter();
    request = new MockHttpServletRequest();
    response = new MockHttpServletResponse();
    chain = mock(FilterChain.class);
  }

  @Test
  void filterAddsRequestIdToResponseHeader() throws Exception {
    filter.doFilter(request, response, chain);

    assertThat(response.getHeader("X-Request-Id")).isNotNull();
    assertThat(response.getHeader("X-Request-Id")).isNotBlank();
  }

  @Test
  void filterAddsRequestIdToRequestAttribute() throws Exception {
    filter.doFilter(request, response, chain);

    assertThat(request.getAttribute("requestId")).isNotNull();
  }

  @Test
  void filterGeneratesUniqueRequestId() throws Exception {
    MockHttpServletRequest request2 = new MockHttpServletRequest();
    MockHttpServletResponse response2 = new MockHttpServletResponse();

    filter.doFilter(request, response, chain);
    filter.doFilter(request2, response2, chain);

    assertThat(response.getHeader("X-Request-Id"))
        .isNotEqualTo(response2.getHeader("X-Request-Id"));
  }

  @Test
  void filterShouldSetStartTimeAsRequestAttribute() throws Exception {
    filter.doFilter(request, response, chain);
    assertThat(request.getAttribute("startTime")).isNotNull();
  }

  @Test
  void filterInitializesLifecycleAttributes() throws Exception {
    filter.doFilter(request, response, chain);

    assertThat(request.getAttribute("authResult")).isEqualTo("skipped");
    assertThat(request.getAttribute("rateLimitResult")).isEqualTo("skipped");
    assertThat(request.getAttribute("downstreamUrl")).isNull();
    assertThat(request.getAttribute("downstreamStatus")).isNull();
    assertThat(request.getAttribute("downstreamLatencyMs")).isNull();
  }

  @Test
  void shouldCallNextFilter() throws Exception {
    filter.doFilter(request, response, chain);
    verify(chain).doFilter(request, response);
  }

  @Test
  void shouldRemoveRequestIdFromMDC() throws Exception {
    filter.doFilter(request, response, chain);
    assertThat(MDC.get("requestId")).isNull();
  }

  @Test
  void shouldClearMdcEvenWhenDownstreamThrows() throws Exception {
    doThrow(new RuntimeException("Test exception")).when(chain).doFilter(request, response);
    assertThatThrownBy(() -> filter.doFilter(request, response, chain))
        .isInstanceOf(RuntimeException.class);

    assertThat(MDC.get("requestId")).isNull();
  }
}
