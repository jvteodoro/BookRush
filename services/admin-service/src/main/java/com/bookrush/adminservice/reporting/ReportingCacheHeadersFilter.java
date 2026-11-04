package com.bookrush.adminservice.reporting;

import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Applies a short, identity-varying cache policy to read-only reporting responses. */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class ReportingCacheHeadersFilter extends OncePerRequestFilter {
  private final long maxAgeSeconds;

  public ReportingCacheHeadersFilter(
      @Value("${bookrush.reporting.cache-max-age-seconds:30}") long maxAgeSeconds) {
    this.maxAgeSeconds = Math.max(0, maxAgeSeconds);
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
      FilterChain filterChain) throws ServletException, IOException {
    filterChain.doFilter(request, response);
    if (!HttpMethod.GET.matches(request.getMethod())
        || !request.getRequestURI().startsWith("/api/admin/v1/reporting/")) {
      return;
    }
    response.setHeader("Vary", "Authorization");
    if (response.getStatus() >= 400) {
      response.setHeader("Cache-Control", "no-store");
    } else {
      response.setHeader("Cache-Control",
          "private, max-age=" + maxAgeSeconds + ", must-revalidate");
    }
  }
}
