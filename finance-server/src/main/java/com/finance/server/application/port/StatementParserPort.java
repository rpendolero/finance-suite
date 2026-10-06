package com.finance.server.application.port;

import com.finance.domain.Movement;
import java.io.InputStream;
import java.util.List;

public interface StatementParserPort {
  List<Movement> parse(InputStream input, String productId);
}
