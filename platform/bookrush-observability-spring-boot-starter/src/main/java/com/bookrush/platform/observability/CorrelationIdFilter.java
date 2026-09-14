package com.bookrush.platform.observability;

import com.bookrush.platform.core.CorrelationId;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

public final class CorrelationIdFilter extends OncePerRequestFilter {
  public static final String HEADER = "X-Correlation-ID";
  public static final String MDC_KEY = "correlationId";

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String candidate = request.getHeader(HEADER);
    String value;
    try {
      value = candidate == null ? UUID.randomUUID().toString() : new CorrelationId(candidate).value();
    } catch (IllegalArgumentException invalid) {
      value = UUID.randomUUID().toString();
    }
    MDC.put(MDC_KEY, value);
    response.setHeader(HEADER, value);
    try {
      chain.doFilter(request, response);
    } finally {
      MDC.remove(MDC_KEY);
    }
  }
}
