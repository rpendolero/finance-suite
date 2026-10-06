package com.finance.server.infrastructure.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Logs technical request metadata without URLs, payloads or credentials. */
@org.springframework.core.annotation.Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE + 10)
@Component
@Slf4j
public class RequestTraceFilter extends OncePerRequestFilter {
  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String incoming = request.getHeader("X-Correlation-ID");
    String id =
        incoming != null
                && incoming.matches(
                    "[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}")
            ? incoming
            : UUID.randomUUID().toString();
    String previous = MDC.get("correlationId");
    MDC.put("correlationId", id);
    response.setHeader("X-Correlation-ID", id);
    long started = System.nanoTime();
    try {
      log.info("Request started: method={}", request.getMethod());
      chain.doFilter(request, response);
      log.info(
          "Request completed: status={}, durationMs={}",
          response.getStatus(),
          (System.nanoTime() - started) / 1_000_000);
    } catch (IOException | ServletException | RuntimeException e) {
      log.error(
          "Request failed: errorType={}, durationMs={}",
          e.getClass().getSimpleName(),
          (System.nanoTime() - started) / 1_000_000);
      throw e;
    } finally {
      if (previous == null) MDC.remove("correlationId");
      else MDC.put("correlationId", previous);
    }
  }
}
