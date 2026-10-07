package com.finance.server.infrastructure.config;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.net.URI;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rejects browser requests from untrusted origins.
 *
 * <p>The backend can be reached directly or through the dashboard development proxy, so trusted
 * frontend origins are explicitly configurable instead of assuming that browser and backend ports
 * are identical.
 */
public final class OriginFilter extends OncePerRequestFilter {

  private final Set<String> allowedOrigins;

  public OriginFilter(Set<String> allowedOrigins) {
    this.allowedOrigins =
        allowedOrigins.stream()
            .map(OriginFilter::canonicalOrigin)
            .collect(Collectors.toUnmodifiableSet());
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {

    String origin = request.getHeader("Origin");
    if (origin != null && !isAllowed(origin, request)) {
      response.sendError(HttpServletResponse.SC_FORBIDDEN);
      return;
    }

    chain.doFilter(request, response);
  }

  private boolean isAllowed(String origin, HttpServletRequest request) {
    try {
      String canonical = canonicalOrigin(origin);
      return canonical.equals(requestOrigin(request)) || allowedOrigins.contains(canonical);
    } catch (IllegalArgumentException ex) {
      return false;
    }
  }

  private static String requestOrigin(HttpServletRequest request) {
    String scheme = request.getScheme();
    String host = request.getServerName();
    int port = request.getServerPort();
    return canonicalOrigin(scheme + "://" + host + ":" + port);
  }

  private static String canonicalOrigin(String value) {
    URI uri = URI.create(value.trim());
    if (uri.getScheme() == null || uri.getHost() == null) {
      throw new IllegalArgumentException("Invalid origin");
    }

    String scheme = uri.getScheme().toLowerCase(java.util.Locale.ROOT);
    String host = uri.getHost().toLowerCase(java.util.Locale.ROOT);
    int port = uri.getPort();
    int effectivePort = port < 0 ? ("https".equals(scheme) ? 443 : 80) : port;

    return scheme + "://" + host + ":" + effectivePort;
  }
}
