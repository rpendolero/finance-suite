package com.finance.server.infrastructure.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Optional read-only API key authentication for the MCP transport. */
public class McpApiKeyFilter extends OncePerRequestFilter {
  private final byte[] expectedHash;

  public McpApiKeyFilter(@Value("${finance.security.mcp-api-key-sha256:}") String expectedHash) {
    if (!expectedHash.isBlank() && !expectedHash.matches("[0-9a-fA-F]{64}")) {
      throw new IllegalArgumentException("MCP API key SHA-256 must be 64 hexadecimal characters");
    }
    this.expectedHash = expectedHash.isBlank() ? null : HexFormat.of().parseHex(expectedHash);
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getServletPath();
    return !("/mcp".equals(path) || path.startsWith("/mcp/"));
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
      FilterChain chain) throws ServletException, IOException {
    String key = request.getHeader("X-API-Key");
    if (key == null) {
      chain.doFilter(request, response);
      return;
    }
    if (expectedHash == null || key.length() < 32 ||
        !MessageDigest.isEqual(expectedHash, sha256(key))) {
      response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid MCP API key");
      return;
    }
    var auth = new UsernamePasswordAuthenticationToken("mcp-api-key", null,
        List.of(new SimpleGrantedAuthority("ROLE_READER")));
    try {
      SecurityContextHolder.getContext().setAuthentication(auth);
      chain.doFilter(request, response);
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  private static byte[] sha256(String value) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }
}
