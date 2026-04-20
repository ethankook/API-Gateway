package com.scheduler.filter;

import com.common.helper.FilterErrorResponseWriter;
import com.scheduler.enums.PrincipalType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component("schedulerAuthFilter")
@RequiredArgsConstructor
public class AuthenticatedCallerFilter extends OncePerRequestFilter {

  @Value("${scheduler.require-authenticated-caller}")
  private boolean requireAuthenticatedCaller;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    String principalTypeHeader = request.getHeader("X-Authenticated-Principal-Type");
    String principalIdHeader = request.getHeader("X-Authenticated-Principal-Id");

    if (requireAuthenticatedCaller && (principalTypeHeader == null || principalIdHeader == null)) {
      FilterErrorResponseWriter.writeError(
          response, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized", request);
      return;
    }

    if (principalTypeHeader != null || principalIdHeader != null) {
      try {
        PrincipalType principalType = PrincipalType.valueOf(principalTypeHeader);
        Long principalId = Long.parseLong(principalIdHeader);
        request.setAttribute("X-Authenticated-Principal-Type", principalType);
        request.setAttribute("X-Authenticated-Principal-Id", principalId);
      } catch (IllegalArgumentException e) {
        FilterErrorResponseWriter.writeError(
            response, HttpServletResponse.SC_BAD_REQUEST, "Invalid authenticated principal", request);
        return;
      }
    }

    filterChain.doFilter(request, response);
  }
}
