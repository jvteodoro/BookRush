package com.bookrush.adminservice.reporting;

import static org.junit.jupiter.api.Assertions.assertEquals;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ReportingCacheHeadersFilterTest {
  private final ReportingCacheHeadersFilter filter = new ReportingCacheHeadersFilter(30);

  @Test
  void variesSuccessfulReportingReadsByAuthorization() throws Exception {
    var request = new MockHttpServletRequest("GET", "/api/admin/v1/reporting/reader-day");
    var response = new MockHttpServletResponse();
    FilterChain chain = (req, res) -> ((MockHttpServletResponse) res).setStatus(200);

    filter.doFilter(request, response, chain);

    assertEquals("Authorization", response.getHeader("Vary"));
    assertEquals("private, max-age=30, must-revalidate", response.getHeader("Cache-Control"));
  }

  @Test
  void neverCachesReportingErrors() throws Exception {
    var request = new MockHttpServletRequest("GET", "/api/admin/v1/reporting/reader-day");
    var response = new MockHttpServletResponse();
    FilterChain chain = (req, res) -> ((MockHttpServletResponse) res).sendError(403);

    filter.doFilter(request, response, chain);

    assertEquals("no-store", response.getHeader("Cache-Control"));
  }
}
