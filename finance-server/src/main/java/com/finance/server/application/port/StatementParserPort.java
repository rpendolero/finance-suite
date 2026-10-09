package com.finance.server.application.port;

import com.finance.domain.Movement;
import com.finance.statements.StatementFormat;
import java.io.InputStream;
import java.util.List;

public interface StatementParserPort {
  List<Movement> parse(InputStream input, String productId);
  default List<Movement> parse(InputStream input, String productId, StatementFormat format) {
    if (format != StatementFormat.CSV) throw new IllegalArgumentException("Formato no soportado");
    return parse(input, productId);
  }
}
