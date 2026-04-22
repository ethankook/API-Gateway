package com.scheduler.filter;

import com.common.helper.FilterErrorResponseWriter;
import com.scheduler.enums.PrincipalType;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component("schedulerAuthFilter")
@RequiredArgsConstructor
public class AuthenticatedCallerFilter extends OncePerRequestFilter {

  @Value("${scheduler.require-authenticated-caller}")
  private boolean requireAuthenticatedCaller;

  @Value("${scheduler.gateway-internal-token:}")
  private String gatewayInternalToken;

  @PostConstruct
  void validateConfiguration() {
    if (requireAuthenticatedCaller
        && (gatewayInternalToken == null || gatewayInternalToken.isBlank())) {
      throw new IllegalStateException(
          "scheduler.gateway-internal-token must be configured when authenticated callers are required");
    }
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    String gatewayTokenHeader = request.getHeader("X-Gateway-Internal-Token");
    String principalTypeHeader = request.getHeader("X-Authenticated-Principal-Type");
    String principalIdHeader = request.getHeader("X-Authenticated-Principal-Id");
    String isAdminHeader = request.getHeader("X-Authenticated-Is-Admin");

    if (requireAuthenticatedCaller && !validGatewayToken(gatewayTokenHeader)) {
      FilterErrorResponseWriter.writeError(
          response, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized", request);
      return;
    }

    if (requireAuthenticatedCaller && (principalTypeHeader == null || principalIdHeader == null)) {
      FilterErrorResponseWriter.writeError(
          response, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized", request);
      return;
    }

    if (principalTypeHeader != null || principalIdHeader != null) {
      try {
        PrincipalType principalType = PrincipalType.valueOf(principalTypeHeader);
        Long principalId = Long.parseLong(principalIdHeader);
        Boolean isAdmin = parseAdminHeader(isAdminHeader);
        request.setAttribute("X-Authenticated-Principal-Type", principalType);
        request.setAttribute("X-Authenticated-Principal-Id", principalId);
        request.setAttribute("X-Authenticated-Is-Admin", isAdmin);
      } catch (IllegalArgumentException e) {
        FilterErrorResponseWriter.writeError(
            response,
            HttpServletResponse.SC_BAD_REQUEST,
            "Invalid authenticated principal",
            request);
        return;
      }
    }

    filterChain.doFilter(request, response);
  }

  private boolean validGatewayToken(String gatewayTokenHeader) {
    if (gatewayTokenHeader == null || gatewayInternalToken == null) {
      return false;
    }

    byte[] configured = gatewayInternalToken.getBytes(StandardCharsets.UTF_8);
    byte[] provided = gatewayTokenHeader.getBytes(StandardCharsets.UTF_8);
    return MessageDigest.isEqual(configured, provided);
  }

  private Boolean parseAdminHeader(String isAdminHeader) {
    if (isAdminHeader == null || isAdminHeader.isBlank()) {
      return false;
    }

    if ("true".equalsIgnoreCase(isAdminHeader)) {
      return true;
    }

    if ("false".equalsIgnoreCase(isAdminHeader)) {
      return false;
    }

    throw new IllegalArgumentException("Invalid admin header");
  }
}
