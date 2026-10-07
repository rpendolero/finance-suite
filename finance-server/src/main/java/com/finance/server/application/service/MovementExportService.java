package com.finance.server.application.service;

import com.finance.domain.Movement;
import com.finance.server.application.port.MovementSearchPort;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Slf4j
public class MovementExportService {
  private final MovementSearchPort search;

  public byte[] exportCsv(MovementSearchPort.Criteria criteria) {
    StringBuilder out = new StringBuilder("Fecha;Producto;Comercio;Descripcion;Categoria;Importe;Moneda;Estado;Tipo\n");
    int offset = 0;
    MovementSearchPort.Page page;
    do {
      var current = new MovementSearchPort.Criteria(criteria.period(), criteria.productId(), criteria.category(),
          criteria.merchant(), criteria.text(), criteria.minAmount(), criteria.maxAmount(), criteria.kind(),
          criteria.status(), criteria.sortBy(), criteria.sortDirection(), offset, 200);
      page = search.search(current);
      page.items().forEach(m -> append(out, m));
      offset += page.items().size();
    } while (!page.items().isEmpty() && offset < page.total());
    log.info("Movement CSV export completed: rows={}", offset);
    return out.toString().getBytes(StandardCharsets.UTF_8);
  }

  private void append(StringBuilder out, Movement m) {
    out.append(cell(m.date())).append(';').append(cell(m.productId())).append(';').append(cell(m.merchant()))
        .append(';').append(cell(m.description())).append(';').append(cell(m.category())).append(';')
        .append(m.amount()).append(';').append(cell(m.currency())).append(';').append(m.status()).append(';')
        .append(m.kind()).append('\n');
  }

  private String cell(Object value) {
    if (value == null) return "";
    return String.valueOf(value).replace(";", ",").replace("\n", " ");
  }
}
