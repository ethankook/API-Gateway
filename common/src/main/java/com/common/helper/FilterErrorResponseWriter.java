package com.common.helper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

public final class FilterErrorResponseWriter {

  private FilterErrorResponseWriter() {}

  public static void writeError(
      HttpServletResponse response, int status, String message, HttpServletRequest request)
      throws IOException {
    String requestId = requestId(request);
    response.setStatus(status);
    response.setContentType("application/json");
    response.setHeader("X-Request-Id", requestId);
    response
        .getWriter()
        .write(
            String.format(
                "{\"error\":\"%s\",\"status\":%d,\"requestId\":\"%s\"}",
                escapeJson(message), status, escapeJson(requestId)));
  }

  public static String requestId(HttpServletRequest request) {
    String requestId = (String) request.getAttribute("requestId");
    return requestId != null ? requestId : "unknown";
  }

  private static String escapeJson(String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
