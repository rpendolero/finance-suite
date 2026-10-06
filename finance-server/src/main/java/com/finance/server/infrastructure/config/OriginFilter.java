package com.finance.server.infrastructure.config;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.net.URI;
import org.springframework.web.filter.OncePerRequestFilter;

/** Rechaza peticiones de navegador de otros orígenes; Basic Auth sin cookies de sesión. */
public class OriginFilter extends OncePerRequestFilter {
  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String origin = request.getHeader("Origin");
    if (origin != null) {
      try {
        var uri = URI.create(origin);
        var expected = URI.create(request.getRequestURL().toString());
        int port = uri.getPort() < 0 ? ("https".equals(uri.getScheme()) ? 443 : 80) : uri.getPort();
        int expectedPort =
            expected.getPort() < 0
                ? ("https".equals(expected.getScheme()) ? 443 : 80)
                : expected.getPort();
        if (!java.util.Objects.equals(uri.getHost(), expected.getHost())
            || !java.util.Objects.equals(uri.getScheme(), expected.getScheme())
            || port != expectedPort) {
          response.sendError(403);
          return;
        }
      } catch (IllegalArgumentException e) {
        response.sendError(403);
        return;
      }
    }
    chain.doFilter(request, response);
  }
}
