package com.finance.server;

import static org.junit.jupiter.api.Assertions.*;

import com.finance.server.infrastructure.config.RequestTraceFilter;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestTraceFilterTest {
  @Test
  void propagatesCorrelationAndRestoresCallerContext() throws Exception {
    String id = "1e4b3d91-c302-4c34-8c7c-27148ee13cc2";
    var request = new MockHttpServletRequest();
    request.addHeader("X-Correlation-ID", id);
    var response = new MockHttpServletResponse();
    MDC.put("correlationId", "outer");
    try {
      new RequestTraceFilter()
          .doFilter(request, response, (req, res) -> assertEquals(id, MDC.get("correlationId")));
      assertEquals(id, response.getHeader("X-Correlation-ID"));
      assertEquals("outer", MDC.get("correlationId"));
    } finally {
      MDC.remove("correlationId");
    }
  }

  @Test
  void replacesUntrustedCorrelationAndClearsContextOnFailure() {
    var request = new MockHttpServletRequest();
    request.addHeader("X-Correlation-ID", "private bank data");
    var response = new MockHttpServletResponse();
    assertThrows(
        IllegalStateException.class,
        () ->
            new RequestTraceFilter()
                .doFilter(
                    request,
                    response,
                    (req, res) -> {
                      throw new IllegalStateException("private failure details");
                    }));
    assertDoesNotThrow(() -> java.util.UUID.fromString(response.getHeader("X-Correlation-ID")));
    assertNull(MDC.get("correlationId"));
  }
}
